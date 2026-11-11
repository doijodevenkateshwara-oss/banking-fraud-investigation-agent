package com.bfs.fraudcase.observability;

import com.bfs.fraudcase.config.CapstoneProperties;
import com.bfs.fraudcase.knowledge.PolicyIngestionService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Health for the model provider and vector store, without revealing credentials.
 *
 * @author Venkateshwara Doijode
 */
@Configuration
public class FraudHealthIndicators {

    @Bean
    HealthIndicator modelProvider(CapstoneProperties props, MeterRegistry meters) {
        return () -> {
            CapstoneProperties.Model m = props.model();
            boolean configured = m.apiKeyPresent();
            double errors = meters.find("fraud.model.errors").counter() == null ? 0 : meters.find("fraud.model.errors").counter().count();
            double invalid = meters.find("fraud.recommendation.invalid").counters().stream().mapToDouble(c -> c.count()).sum();
            Health.Builder b = configured ? Health.up() : Health.status("DEGRADED");
            return b.withDetail("framework", "langchain4j")
                    .withDetail("provider", "anthropic")
                    .withDetail("model", m.modelName())
                    .withDetail("credentialsConfigured", configured)
                    .withDetail("onFailure", "SAFE_MANUAL_REVIEW")
                    .withDetail("modelErrors", errors)
                    .withDetail("invalidOutputs", invalid)
                    .build();
        };
    }

    @Bean
    HealthIndicator vectorStore(PolicyIngestionService policies, CapstoneProperties props) {
        return () -> {
            int n = policies.indexedCount();
            return (n > 0 ? Health.up() : Health.down())
                    .withDetail("indexedChunks", n)
                    .withDetail("topK", props.retrieval().topK())
                    .withDetail("similarityThreshold", props.retrieval().similarityThreshold())
                    .build();
        };
    }
}
