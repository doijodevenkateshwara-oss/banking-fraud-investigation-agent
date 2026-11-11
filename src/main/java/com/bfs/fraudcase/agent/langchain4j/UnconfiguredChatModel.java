package com.bfs.fraudcase.agent.langchain4j;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;

/**
 * Placeholder used when the live provider has no credentials; always fails so the safe fallback path applies.
 *
 * @author Venkateshwara Doijode
 */
class UnconfiguredChatModel implements ChatModel {

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        throw new IllegalStateException("Model provider is not configured (set ANTHROPIC_API_KEY)");
    }
}
