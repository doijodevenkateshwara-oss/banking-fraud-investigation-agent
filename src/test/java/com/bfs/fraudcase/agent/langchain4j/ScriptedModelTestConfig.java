package com.bfs.fraudcase.agent.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the live Anthropic model with the deterministic offline scripted model in tests.
 *
 * @author Venkateshwara Doijode
 */
@TestConfiguration
public class ScriptedModelTestConfig {

    @Bean
    @Primary
    ChatModel scriptedFraudChatModel(ModelObservabilityListener listener) {
        return new ScriptedFraudChatModel(listener);
    }
}
