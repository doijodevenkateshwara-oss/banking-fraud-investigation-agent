package com.bfs.fraudcase.tools;

import com.bfs.fraudcase.domain.EvidenceType;
import com.bfs.fraudcase.domain.Masking;
import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.domain.Priority;
import com.bfs.fraudcase.domain.RiskLevel;
import com.bfs.fraudcase.domain.Route;
import com.bfs.fraudcase.domain.Severity;
import com.bfs.fraudcase.domain.ToolStatus;
import com.bfs.fraudcase.knowledge.PolicyRetriever;
import com.bfs.fraudcase.tools.ToolExecutor.EvidenceDraft;
import com.bfs.fraudcase.tools.SyntheticFixtureProvider.TransactionFixture;
import com.bfs.fraudcase.tools.dto.CaseSla;
import com.bfs.fraudcase.tools.dto.DeviceSignals;
import com.bfs.fraudcase.tools.dto.LocationComparison;
import com.bfs.fraudcase.tools.dto.MerchantRiskProfile;
import com.bfs.fraudcase.tools.dto.PolicyEvidence;
import com.bfs.fraudcase.tools.dto.PriorDisputes;
import com.bfs.fraudcase.tools.dto.TransactionDetails;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The approved, read-only evidence tools bound to one {@link InvestigationSession}. Framework-neutral: AI adapters
 * wrap these methods; the rules adapter calls them directly. No method can change account, payment or customer state.
 *
 * @author Venkateshwara Doijode
 */
public final class EvidenceTools {

    private static final String CORE = "synthetic-core-banking";
    private static final String DEVICE = "synthetic-device-intelligence";
    private static final String GEO = "synthetic-geo-service";
    private static final String MERCHANT = "synthetic-merchant-registry";
    private static final String HISTORY = "synthetic-dispute-history";
    private static final String SLA = "synthetic-sla-calculator";
    private static final String POLICY = "approved-policy-store";

    private final InvestigationSession session;
    private final ToolExecutor executor;
    private final SyntheticFixtureProvider fixtures;
    private final PolicyRetriever retriever;
    private final Clock clock;

    EvidenceTools(InvestigationSession session, ToolExecutor executor, SyntheticFixtureProvider fixtures,
                  PolicyRetriever retriever, Clock clock) {
        this.session = session;
        this.executor = executor;
        this.fixtures = fixtures;
        this.retriever = retriever;
        this.clock = clock;
    }

    public InvestigationSession session() {
        return session;
    }

    public TransactionDetails getTransactionDetails(String transactionRef) {
        return executor.execute(session, ToolRegistry.GET_TRANSACTION_DETAILS, Map.of("transactionRef", s(transactionRef)),
                id -> {
                    requireMatch(transactionRef, Masking.TRANSACTION_REF, "transactionRef");
                    simulateFailure(ToolRegistry.GET_TRANSACTION_DETAILS, transactionRef);
                    TransactionFixture t = fixtures.transaction(transactionRef);
                    if (!Boolean.TRUE.equals(t.found())) {
                        return new TransactionDetails(id, ToolStatus.NOT_FOUND, clock.instant(), CORE, false, transactionRef,
                                null, null, null, null, null, null, null, null, false, null,
                                List.of("No transaction record found for the reference"));
                    }
                    List<String> obs = new ArrayList<>();
                    obs.add(("CARD_NOT_PRESENT".equals(t.channel()) ? "E-commerce" : t.channel()) + " transaction " + t.status());
                    if ("UNAVAILABLE".equals(t.authOutcome())) {
                        obs.add("Authentication outcome unavailable from the transaction record");
                    }
                    if (t.duplicateOf() != null) {
                        obs.add("Payment fingerprint matches " + t.duplicateOf());
                    }
                    return new TransactionDetails(id, ToolStatus.SUCCEEDED, clock.instant(), CORE, true, transactionRef,
                            t.status(), t.occurredAt(), t.amount(), t.currency(), t.channel(), t.authOutcome(),
                            t.maskedMerchant(), "FP-" + Integer.toHexString((t.maskedMerchant() + t.amount()).hashCode()),
                            t.duplicateOf() != null, t.duplicateOf(), obs);
                },
                (id, status, msg) -> new TransactionDetails(id, status, clock.instant(), CORE, false, transactionRef,
                        null, null, null, null, null, null, null, null, false, null, List.of(msg)),
                this::transactionEvidence, "transaction record");
    }

    private List<EvidenceDraft> transactionEvidence(TransactionDetails t) {
        List<EvidenceDraft> out = new ArrayList<>();
        Severity sev = switch (t.authOutcome() == null ? "UNAVAILABLE" : t.authOutcome()) {
            case "FAILED" -> Severity.HIGH;
            case "SUCCESS" -> Severity.LOW;
            default -> Severity.MEDIUM;
        };
        String auth = switch (t.authOutcome() == null ? "UNAVAILABLE" : t.authOutcome()) {
            case "SUCCESS" -> "authentication challenge passed";
            case "FAILED" -> "authentication challenge failed";
            case "NOT_ATTEMPTED" -> "no authentication challenge attempted";
            default -> "authentication outcome unavailable";
        };
        boolean cnp = "CARD_NOT_PRESENT".equals(t.channel());
        out.add(new EvidenceDraft(EvidenceType.TRANSACTION, sev, cnp ? "Card-not-present payment" : "Payment " + t.channel(),
                (cnp ? "E-commerce transaction" : t.channel() + " transaction") + " " + t.transactionStatus() + "; " + auth + "."));
        if (t.authUnavailable()) {
            session.addMissing("authentication challenge outcome");
        }
        if (t.duplicateFingerprintMatch()) {
            out.add(new EvidenceDraft(EvidenceType.TRANSACTION, Severity.HIGH, "Duplicate posting",
                    "Payment fingerprint, merchant and amount match " + t.duplicateOfRef() + "."));
        }
        return out;
    }

    public DeviceSignals getDeviceSignals(String maskedCustomerRef, String transactionRef) {
        return executor.execute(session, ToolRegistry.GET_DEVICE_SIGNALS,
                Map.of("maskedCustomerRef", s(maskedCustomerRef), "transactionRef", s(transactionRef)),
                id -> {
                    requireMatch(maskedCustomerRef, Masking.MASKED_CUSTOMER_REF, "maskedCustomerRef");
                    requireMatch(transactionRef, Masking.TRANSACTION_REF, "transactionRef");
                    simulateFailure(ToolRegistry.GET_DEVICE_SIGNALS, transactionRef);
                    TransactionFixture t = fixtures.transaction(transactionRef);
                    if (!Boolean.TRUE.equals(t.found()) || t.device() == null) {
                        return new DeviceSignals(id, ToolStatus.NOT_FOUND, clock.instant(), DEVICE, null, null, null, null,
                                List.of("No device session linked to the transaction"));
                    }
                    var d = t.device();
                    Instant firstSeen = Boolean.TRUE.equals(d.deviceKnown())
                            ? t.occurredAt().toInstant().minus(Duration.ofDays(210))
                            : t.occurredAt().toInstant().minus(Duration.ofMinutes(d.minutesBeforePayment()));
                    return new DeviceSignals(id, ToolStatus.SUCCEEDED, clock.instant(), DEVICE, d.deviceKnown(), firstSeen,
                            d.minutesBeforePayment(), d.trustLevel(),
                            List.of(Boolean.TRUE.equals(d.deviceKnown()) ? "Device previously trusted for this customer"
                                    : "Device first observed " + d.minutesBeforePayment() + " minutes before the payment"));
                },
                (id, status, msg) -> new DeviceSignals(id, status, clock.instant(), DEVICE, null, null, null, null, List.of(msg)),
                d -> List.of(Boolean.TRUE.equals(d.deviceKnown())
                        ? new EvidenceDraft(EvidenceType.DEVICE, Severity.LOW, "Known trusted device",
                        "Device previously trusted for this customer (trust " + d.trustLevel() + ").")
                        : new EvidenceDraft(EvidenceType.DEVICE, Severity.HIGH, "New device observed",
                        "First seen " + d.minutesBeforePayment() + " minutes before the disputed payment.")),
                "device signals");
    }

    public LocationComparison compareRecentLocations(String maskedCustomerRef, String transactionRef) {
        return executor.execute(session, ToolRegistry.COMPARE_RECENT_LOCATIONS,
                Map.of("maskedCustomerRef", s(maskedCustomerRef), "transactionRef", s(transactionRef)),
                id -> {
                    requireMatch(maskedCustomerRef, Masking.MASKED_CUSTOMER_REF, "maskedCustomerRef");
                    requireMatch(transactionRef, Masking.TRANSACTION_REF, "transactionRef");
                    simulateFailure(ToolRegistry.COMPARE_RECENT_LOCATIONS, transactionRef);
                    TransactionFixture t = fixtures.transaction(transactionRef);
                    if (!Boolean.TRUE.equals(t.found()) || t.location() == null) {
                        return new LocationComparison(id, ToolStatus.NOT_FOUND, clock.instant(), GEO, null, null, null,
                                List.of("No recent trusted location available"));
                    }
                    var l = t.location();
                    return new LocationComparison(id, ToolStatus.SUCCEEDED, clock.instant(), GEO, l.distanceKm(),
                            l.timeDeltaMinutes(), l.anomalyLevel(),
                            List.of(String.format("%,d km from the latest trusted card activity", l.distanceKm())));
                },
                (id, status, msg) -> new LocationComparison(id, status, clock.instant(), GEO, null, null, null, List.of(msg)),
                l -> List.of("HIGH".equals(l.anomalyLevel()) || "MEDIUM".equals(l.anomalyLevel())
                        ? new EvidenceDraft(EvidenceType.LOCATION, Severity.valueOf(l.anomalyLevel()), "Location anomaly",
                        String.format("%,d km from the latest trusted card activity.", l.distanceKm()))
                        : new EvidenceDraft(EvidenceType.LOCATION, Severity.LOW, "Location consistent",
                        String.format("%,d km from recent trusted activity; no anomaly.", l.distanceKm()))),
                "recent location comparison");
    }

    public MerchantRiskProfile getMerchantRiskProfile(String maskedMerchant) {
        return executor.execute(session, ToolRegistry.GET_MERCHANT_RISK_PROFILE, Map.of("maskedMerchant", s(maskedMerchant)),
                id -> {
                    requireMatch(maskedMerchant, Masking.MASKED_MERCHANT, "maskedMerchant");
                    simulateFailure(ToolRegistry.GET_MERCHANT_RISK_PROFILE, session.facts().transactionRef());
                    var m = fixtures.merchant(maskedMerchant);
                    return new MerchantRiskProfile(id, ToolStatus.SUCCEEDED, clock.instant(), MERCHANT, m.riskBand(),
                            m.blocklistMatch(), m.category(), List.of("Category: " + m.category()));
                },
                (id, status, msg) -> new MerchantRiskProfile(id, status, clock.instant(), MERCHANT, null, null, null, List.of(msg)),
                m -> List.of(new EvidenceDraft(EvidenceType.MERCHANT,
                        Boolean.TRUE.equals(m.blocklistMatch()) ? Severity.HIGH : Severity.valueOf(m.riskBand()),
                        "Merchant profile", "Risk band " + m.riskBand() + "; "
                        + (Boolean.TRUE.equals(m.blocklistMatch()) ? "confirmed blocklist match." : "no confirmed blocklist match."))),
                "merchant risk profile");
    }

    public PriorDisputes getPriorDisputes(String maskedCustomerRef) {
        return executor.execute(session, ToolRegistry.GET_PRIOR_DISPUTES, Map.of("maskedCustomerRef", s(maskedCustomerRef)),
                id -> {
                    requireMatch(maskedCustomerRef, Masking.MASKED_CUSTOMER_REF, "maskedCustomerRef");
                    simulateFailure(ToolRegistry.GET_PRIOR_DISPUTES, session.facts().transactionRef());
                    var c = fixtures.customer(maskedCustomerRef);
                    return new PriorDisputes(id, ToolStatus.SUCCEEDED, clock.instant(), HISTORY, c.count(), c.similarCount(),
                            180, List.of(c.count() + " earlier claim(s), " + c.similarCount() + " similar"));
                },
                (id, status, msg) -> new PriorDisputes(id, status, clock.instant(), HISTORY, null, null, 180, List.of(msg)),
                p -> List.of(p.similarCount() >= 2
                        ? new EvidenceDraft(EvidenceType.HISTORY, Severity.MEDIUM, "Repeated similar claims",
                        p.similarCount() + " similar claims recorded in the previous " + p.lookbackDays() + " days.")
                        : new EvidenceDraft(EvidenceType.HISTORY, Severity.LOW, "Earlier disputes",
                        p.similarCount() == 0 ? "No similar claims recorded in the previous " + p.lookbackDays() + " days."
                                : "1 similar claim recorded in the previous " + p.lookbackDays() + " days.")),
                "earlier dispute history");
    }

    public CaseSla calculateCaseSla(String route, String riskLevel) {
        return executor.execute(session, ToolRegistry.CALCULATE_CASE_SLA, Map.of("route", s(route), "riskLevel", s(riskLevel)),
                id -> {
                    Route r = parseEnum(Route.class, route, "route");
                    RiskLevel risk = parseEnum(RiskLevel.class, riskLevel, "riskLevel");
                    record Target(String queue, Priority priority, int hours) {
                    }
                    Target t = switch (r) {
                        case FRAUD_INVESTIGATION -> risk == RiskLevel.HIGH ? new Target("FRAUD-INVESTIGATION-Q1", Priority.P1, 4)
                                : new Target("FRAUD-INVESTIGATION-Q2", Priority.P2, 24);
                        case PAYMENT_OPERATIONS -> new Target("PAYMENT-OPERATIONS", Priority.P2, 24);
                        case DISPUTE_OPERATIONS -> new Target("DISPUTE-OPERATIONS", Priority.P3, 72);
                        case MANUAL_REVIEW -> risk == RiskLevel.HIGH ? new Target("MANUAL-REVIEW", Priority.P1, 8)
                                : new Target("MANUAL-REVIEW", Priority.P2, 24);
                    };
                    return new CaseSla(id, ToolStatus.SUCCEEDED, clock.instant(), SLA, t.queue(), t.priority(),
                            clock.instant().plus(Duration.ofHours(t.hours())),
                            "Training target of " + t.hours() + "h for " + r + " at " + risk + " risk", List.of());
                },
                (id, status, msg) -> new CaseSla(id, status, clock.instant(), SLA, null, null, null, null, List.of(msg)),
                sla -> List.of(new EvidenceDraft(EvidenceType.SLA, Severity.INFO, "Training SLA target",
                        sla.queue() + " · " + sla.priority() + " · " + sla.explanation() + ".")),
                "case SLA target");
    }

    public PolicyEvidence retrievePolicyEvidence(String query) {
        return executor.execute(session, ToolRegistry.RETRIEVE_POLICY_EVIDENCE, Map.of("query", s(query)),
                id -> {
                    if (query == null || query.isBlank()) {
                        throw new IllegalArgumentException("query must not be blank");
                    }
                    PolicyContext ctx = retriever.retrieve(query);
                    session.addRetrieval(ctx);
                    return new PolicyEvidence(id, ToolStatus.SUCCEEDED, clock.instant(), POLICY, ctx.query(), ctx.topK(),
                            ctx.threshold(), ctx.passages(), ctx.abstained(),
                            ctx.abstained() ? List.of("No approved passage met the similarity threshold") : List.of());
                },
                (id, status, msg) -> new PolicyEvidence(id, status, clock.instant(), POLICY, query, 0, 0, List.of(), true, List.of(msg)),
                p -> List.of(), "applicable policy passage");
    }

    private void simulateFailure(String tool, String transactionRef) {
        fixtures.simulatedFailure(tool, transactionRef).ifPresent(status -> {
            if (status == ToolStatus.TIMEOUT) {
                try {
                    Thread.sleep(executor.timeoutMs() + 250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            throw new SimulatedFailure(status);
        });
    }

    /** Thrown inside tool code to emulate a not-found or unavailable synthetic system. */
    static final class SimulatedFailure extends RuntimeException {
        final ToolStatus status;

        SimulatedFailure(ToolStatus status) {
            super(status.name(), null, false, false);
            this.status = status;
        }
    }

    private static void requireMatch(String value, java.util.regex.Pattern pattern, String name) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a masked/approved reference");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String name) {
        try {
            return Enum.valueOf(type, value);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(name + " must be one of " + java.util.Arrays.toString(type.getEnumConstants()));
        }
    }

    private static String s(String v) {
        return v == null ? "" : v;
    }
}
