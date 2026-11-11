package com.bfs.fraudcase.knowledge;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

/**
 * Validates, chunks and indexes the approved synthetic policy pack at start-up.
 *
 * @author Venkateshwara Doijode
 */
@Service
public class PolicyIngestionService implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PolicyIngestionService.class);

    private final PolicyVectorIndex index;
    private final PolicyDocumentParser parser = new PolicyDocumentParser(180);
    private final List<PolicyChunk> catalogue = new ArrayList<>();

    public PolicyIngestionService(PolicyVectorIndex index) {
        this.index = index;
    }

    @Override
    public void afterPropertiesSet() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources("classpath*:policies/*.md");
            Arrays.sort(resources, Comparator.comparing(Resource::getFilename));
            for (Resource r : resources) {
                catalogue.addAll(parser.parse(r.getFilename(), r.getContentAsString(StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load policy pack", e);
        }
        ingest(catalogue);
    }

    void ingest(List<PolicyChunk> chunks) {
        index.index(chunks);
        log.info("Indexed {} policy chunks ({} active)", chunks.size(), chunks.stream().filter(PolicyChunk::active).count());
    }

    /** Every ingested chunk, including withdrawn versions (shown as unavailable in the UI). */
    public List<PolicyChunk> catalogue() {
        return List.copyOf(catalogue);
    }

    public Optional<PolicyChunk> chunk(String chunkId) {
        return catalogue.stream().filter(c -> c.chunkId().equals(chunkId)).findFirst();
    }

    public int indexedCount() {
        return index.size();
    }
}
