package com.bfs.fraudcase.knowledge;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Venkateshwara Doijode
 */
@Configuration
public class KnowledgeConfig {

    @Bean
    EmbeddingModel policyEmbeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    PolicyVectorIndex policyVectorIndex(EmbeddingModel policyEmbeddingModel) {
        return new Lc4jPolicyVectorIndex(policyEmbeddingModel);
    }
}
