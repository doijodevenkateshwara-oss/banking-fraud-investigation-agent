package com.bfs.fraudcase.agent.langchain4j;

import com.bfs.fraudcase.config.CapstoneProperties;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

/**
 * Bounded, case-keyed memory provider: memoryId must be the caseId; windows never span cases.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class CaseMemory implements ChatMemoryProvider {

    private final JpaChatMemoryStore store;
    private final int maxMessages;

    public CaseMemory(JpaChatMemoryStore store, CapstoneProperties props) {
        this.store = store;
        this.maxMessages = props.memory().maxMessages();
    }

    @Override
    public ChatMemory get(Object memoryId) {
        if (memoryId == null || !memoryId.toString().matches("^CASE-\\d{4}-\\d{4,}$")) {
            throw new IllegalArgumentException("Memory must be keyed by a case ID");
        }
        return MessageWindowChatMemory.builder().id(memoryId).maxMessages(maxMessages).chatMemoryStore(store).build();
    }

    public int maxMessages() {
        return maxMessages;
    }
}
