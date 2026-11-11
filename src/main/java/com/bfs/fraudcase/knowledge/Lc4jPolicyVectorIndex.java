package com.bfs.fraudcase.knowledge;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j EmbeddingStore implementation using an in-process embedding model (no network, deterministic).
 *
 * @author Venkateshwara Doijode
 */
public class Lc4jPolicyVectorIndex implements PolicyVectorIndex {

    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
    private final Map<String, PolicyChunk> chunksById = new ConcurrentHashMap<>();

    public Lc4jPolicyVectorIndex(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @Override
    public void index(List<PolicyChunk> chunks) {
        for (PolicyChunk c : chunks) {
            Metadata md = new Metadata()
                    .put("documentId", c.documentId()).put("version", c.version()).put("section", c.section())
                    .put("chunkId", c.chunkId()).put("status", c.status())
                    .put("effectiveDate", c.effectiveDate().toString());
            TextSegment segment = TextSegment.from(c.sectionTitle() + ": " + c.text(), md);
            Embedding embedding = embeddingModel.embed(segment).content();
            store.add(c.chunkId(), embedding, segment);
            chunksById.put(c.chunkId(), c);
        }
    }

    @Override
    public List<ScoredChunk> search(String query, int topK, double minScore, LocalDate asOf) {
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(embeddingModel.embed(query).content())
                .maxResults(topK)
                .minScore(minScore)
                .filter(metadataKey("status").isEqualTo("ACTIVE")
                        .and(metadataKey("effectiveDate").isLessThanOrEqualTo(asOf.toString())))
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();
        return matches.stream()
                .map(m -> new ScoredChunk(chunksById.get(m.embeddingId()), m.score()))
                .toList();
    }

    @Override
    public int size() {
        return chunksById.size();
    }
}
