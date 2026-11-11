package com.bfs.fraudcase.agent.langchain4j;

import com.bfs.fraudcase.config.CapstoneProperties;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.chat.ChatModel;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Live Anthropic chat model; without an API key every call fails safely and cases go to manual review.
 *
 * @author Venkateshwara Doijode
 */
@Configuration
public class Lc4jConfig {

    @Bean
    ChatModel fraudChatModel(CapstoneProperties props, ModelObservabilityListener listener) {
        CapstoneProperties.Model m = props.model();
        return m.apiKeyPresent()
                ? AnthropicChatModel.builder()
                .apiKey(m.apiKey())
                .modelName(m.modelName())
                .temperature(m.temperature())
                .maxTokens(m.maxTokens())
                .timeout(Duration.ofSeconds(m.timeoutSeconds()))
                .maxRetries(1)
                .logRequests(false)
                .logResponses(false)
                .listeners(listener)
                .build()
                : new UnconfiguredChatModel();
    }
}
