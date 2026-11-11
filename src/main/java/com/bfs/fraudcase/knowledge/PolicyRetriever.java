package com.bfs.fraudcase.knowledge;

import com.bfs.fraudcase.config.CapstoneProperties;
import com.bfs.fraudcase.domain.PolicyContext;
import com.bfs.fraudcase.domain.RetrievedPassage;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Thresholded Top-K retrieval over ACTIVE, currently effective policy. Returns exact citation metadata or an
 * explicit abstention (empty passage list) when nothing meets the configured similarity threshold.
 *
 * @author Venkateshwara Doijode
 */
@Service
public class PolicyRetriever {

    private final PolicyVectorIndex index;
    private final int topK;
    private final double threshold;
    private final Clock clock;
    private final MeterRegistry meters;

    public PolicyRetriever(PolicyVectorIndex index, CapstoneProperties props, Clock clock, MeterRegistry meters) {
        this.index = index;
        this.topK = props.retrieval().topK();
        this.threshold = props.retrieval().similarityThreshold();
        this.clock = clock;
        this.meters = meters;
    }

    public PolicyContext retrieve(String query) {
        String q = sanitise(query);
        LocalDate asOf = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<PolicyVectorIndex.ScoredChunk> hits = index.search(q, topK, threshold, asOf);
        // Stale replacement: keep only the highest version of each document.
        Map<String, String> latestVersion = new LinkedHashMap<>();
        hits.forEach(h -> latestVersion.merge(h.chunk().documentId(), h.chunk().version(),
                (a, b) -> compareVersions(a, b) >= 0 ? a : b));
        List<RetrievedPassage> passages = hits.stream()
                .filter(h -> h.chunk().version().equals(latestVersion.get(h.chunk().documentId())))
                .map(h -> toPassage(h.chunk(), h.score()))
                .toList();
        if (passages.isEmpty()) {
            meters.counter("fraud.retrieval.abstentions").increment();
        }
        return new PolicyContext(q, topK, threshold, passages);
    }

    public static RetrievedPassage toPassage(PolicyChunk c, double score) {
        return new RetrievedPassage(c.documentId(), c.title(), c.version(), c.effectiveDate(), c.owner(), c.section(),
                c.sectionTitle(), c.chunkId(), Math.round(score * 1000d) / 1000d, c.text(), c.status());
    }

    static String sanitise(String query) {
        String q = query == null ? "" : query.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        return q.length() > 300 ? q.substring(0, 300) : q;
    }

    static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int y = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }
}
