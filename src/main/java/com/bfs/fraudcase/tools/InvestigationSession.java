package com.bfs.fraudcase.tools;

import com.bfs.fraudcase.domain.CaseFacts;
import com.bfs.fraudcase.domain.EvidenceItem;
import com.bfs.fraudcase.domain.InvestigationPlan;
import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.domain.ToolTraceRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mutable, case-scoped working set for one investigation run: tool traces, evidence, retrievals and missing
 * information. A session is never shared between cases.
 *
 * @author Venkateshwara Doijode
 */
public final class InvestigationSession {

    /** Evidence source key for items created from validated intake (as opposed to a tool name). */
    public static final String INTAKE = "intake";

    private final CaseFacts facts;
    private final String caseNumber;
    private final int runNumber;
    private final String correlationId;
    private final InvestigationPlan plan;
    private final List<ToolTraceRecord> traces = new ArrayList<>();
    private final List<EvidenceItem> evidence = new ArrayList<>();
    private final Set<String> missingInformation = new LinkedHashSet<>();
    private final List<PolicyContext> retrievals = new ArrayList<>();
    private final Map<String, Object> cache = new HashMap<>();
    private final Map<String, List<String>> evidenceIdsByTool = new LinkedHashMap<>();
    private final Map<String, String> traceIdByTool = new LinkedHashMap<>();
    private int traceSeq;
    private int evidenceSeq;

    public InvestigationSession(CaseFacts facts, int runNumber, String correlationId, InvestigationPlan plan) {
        this.facts = facts;
        this.caseNumber = facts.caseId().substring(facts.caseId().lastIndexOf('-') + 1);
        this.runNumber = runNumber;
        this.correlationId = correlationId;
        this.plan = plan;
    }

    public CaseFacts facts() {
        return facts;
    }

    public String caseId() {
        return facts.caseId();
    }

    public int runNumber() {
        return runNumber;
    }

    public String correlationId() {
        return correlationId;
    }

    public InvestigationPlan plan() {
        return plan;
    }

    synchronized String nextTraceId() {
        return "TR-%s-%d-%02d".formatted(caseNumber, runNumber, ++traceSeq);
    }

    synchronized int currentTraceSeq() {
        return traceSeq;
    }

    public synchronized String nextEvidenceId() {
        return "EV-%s-%d-%02d".formatted(caseNumber, runNumber, ++evidenceSeq);
    }

    synchronized void addTrace(ToolTraceRecord trace) {
        traces.add(trace);
        traceIdByTool.put(trace.toolName(), trace.traceId());
    }

    public synchronized void addEvidence(String toolName, EvidenceItem item) {
        evidence.add(item);
        evidenceIdsByTool.computeIfAbsent(toolName, k -> new ArrayList<>()).add(item.evidenceId());
    }

    public synchronized void addMissing(String item) {
        missingInformation.add(item);
    }

    synchronized void addRetrieval(PolicyContext ctx) {
        retrievals.add(ctx);
    }

    synchronized Object cached(String key) {
        return cache.get(key);
    }

    synchronized void cache(String key, Object value) {
        cache.put(key, value);
    }

    public synchronized List<ToolTraceRecord> traces() {
        return List.copyOf(traces);
    }

    public synchronized List<EvidenceItem> evidence() {
        return List.copyOf(evidence);
    }

    public synchronized List<String> missingInformation() {
        return List.copyOf(missingInformation);
    }

    public synchronized List<String> evidenceIds(String toolName) {
        return List.copyOf(evidenceIdsByTool.getOrDefault(toolName, List.of()));
    }

    public synchronized Optional<String> traceId(String toolName) {
        return Optional.ofNullable(traceIdByTool.get(toolName));
    }

    /** The most recent retrieval, or {@link PolicyContext#notRetrieved()} when policy was never searched. */
    public synchronized PolicyContext latestPolicyContext() {
        return retrievals.isEmpty() ? PolicyContext.notRetrieved() : retrievals.getLast();
    }

    /** All passages retrieved in this run; citations must be a subset of these. */
    public synchronized List<com.bfs.fraudcase.domain.RetrievedPassage> allRetrievedPassages() {
        return retrievals.stream().flatMap(r -> r.passages().stream()).toList();
    }

    public synchronized boolean policySearched() {
        return !retrievals.isEmpty();
    }
}
