package com.bfs.fraudcase.tools;

import com.bfs.fraudcase.config.CapstoneProperties;
import com.bfs.fraudcase.domain.Json;
import com.bfs.fraudcase.domain.ToolStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Deterministic synthetic systems of record. Labelled scenarios are pinned in
 * {@code fixtures/scenario-overrides.json}; every other reference is derived from a stable SHA-256 hash of
 * {@code seed + ":" + reference}, so identical input always yields identical tool results.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class SyntheticFixtureProvider {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DeviceFixture(Boolean deviceKnown, Integer minutesBeforePayment, String trustLevel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LocationFixture(Integer distanceKm, Integer timeDeltaMinutes, String anomalyLevel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TransactionFixture(String scenario, Boolean found, String status, String channel, BigDecimal amount,
                                     String currency, OffsetDateTime occurredAt, String authOutcome,
                                     String maskedMerchant, String duplicateOf, DeviceFixture device,
                                     LocationFixture location, Map<String, String> failures) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomerFixture(Integer count, Integer similarCount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MerchantFixture(String riskBand, Boolean blocklistMatch, String category) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FixtureFile(Map<String, TransactionFixture> transactions, Map<String, CustomerFixture> customers,
                              Map<String, MerchantFixture> merchants) {
    }

    private final FixtureFile overrides;
    private final String seed;

    public SyntheticFixtureProvider(CapstoneProperties props) {
        this.seed = props.tools().deterministicSeed();
        try (InputStream in = new ClassPathResource("fixtures/scenario-overrides.json").getInputStream()) {
            this.overrides = Json.MAPPER.readValue(in, FixtureFile.class);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load synthetic fixtures", e);
        }
    }

    public TransactionFixture transaction(String transactionRef) {
        TransactionFixture pinned = overrides.transactions().get(transactionRef);
        if (pinned != null) {
            return pinned;
        }
        byte[] h = hash("txn:" + transactionRef);
        BigDecimal amount = BigDecimal.valueOf(500 + (u(h, 0) * 256 + u(h, 1)) % 20000).setScale(2, RoundingMode.HALF_UP);
        String[] auth = {"SUCCESS", "SUCCESS", "NOT_ATTEMPTED", "UNAVAILABLE"};
        return new TransactionFixture("hash-derived", true, "POSTED", "CARD_NOT_PRESENT", amount, "INR",
                OffsetDateTime.of(2026, 11, 1 + u(h, 2) % 5, u(h, 3) % 24, u(h, 4) % 60, 0, 0, ZoneOffset.ofHoursMinutes(5, 30)),
                auth[u(h, 5) % auth.length], null, null,
                new DeviceFixture(u(h, 6) % 3 != 0, u(h, 6) % 3 != 0 ? 0 : 5 + u(h, 7) % 120, u(h, 6) % 3 != 0 ? "HIGH" : "LOW"),
                new LocationFixture(u(h, 8) % 4 == 0 ? 600 + u(h, 9) * 8 : u(h, 9) % 40, 30 + u(h, 10) % 300,
                        u(h, 8) % 4 == 0 ? "HIGH" : "NONE"),
                Map.of());
    }

    public CustomerFixture customer(String maskedCustomerRef) {
        CustomerFixture pinned = overrides.customers().get(maskedCustomerRef);
        if (pinned != null) {
            return pinned;
        }
        byte[] h = hash("cust:" + maskedCustomerRef);
        int count = u(h, 0) % 3;
        return new CustomerFixture(count, count == 0 ? 0 : u(h, 1) % (count + 1));
    }

    public MerchantFixture merchant(String maskedMerchant) {
        MerchantFixture pinned = overrides.merchants().get(maskedMerchant);
        if (pinned != null) {
            return pinned;
        }
        byte[] h = hash("mer:" + maskedMerchant);
        String[] bands = {"LOW", "LOW", "MEDIUM", "HIGH"};
        String[] categories = {"General retail", "Digital goods", "Travel", "Electronics", "Food delivery"};
        return new MerchantFixture(bands[u(h, 0) % bands.length], u(h, 1) % 16 == 0, categories[u(h, 2) % categories.length]);
    }

    /** Simulated failure for a tool on a transaction-scoped scenario (T08). */
    public Optional<ToolStatus> simulatedFailure(String toolName, String transactionRef) {
        TransactionFixture t = overrides.transactions().get(transactionRef);
        if (t == null || t.failures() == null || !t.failures().containsKey(toolName)) {
            return Optional.empty();
        }
        return Optional.of(ToolStatus.valueOf(t.failures().get(toolName)));
    }

    private byte[] hash(String key) {
        return HexFormat.of().parseHex(Json.sha256(seed + ":" + key));
    }

    private static int u(byte[] h, int i) {
        return h[i] & 0xFF;
    }
}
