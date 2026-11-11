package com.bfs.fraudcase.agent.langchain4j;

import com.bfs.fraudcase.persistence.ChatMemoryEntity;
import com.bfs.fraudcase.persistence.ChatMemoryRepository;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Persistent, case-keyed chat memory (survives restarts). Only masked case data ever enters memory.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class JpaChatMemoryStore implements ChatMemoryStore {

    private final ChatMemoryRepository repository;
    private final Clock clock;

    public JpaChatMemoryStore(ChatMemoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return repository.findById(memoryId.toString())
                .map(e -> ChatMessageDeserializer.messagesFromJson(e.messagesJson))
                .orElseGet(List::of);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        ChatMemoryEntity e = repository.findById(memoryId.toString()).orElseGet(ChatMemoryEntity::new);
        e.memoryId = memoryId.toString();
        e.messagesJson = ChatMessageSerializer.messagesToJson(messages);
        e.updatedAt = clock.instant();
        repository.save(e);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        repository.deleteById(memoryId.toString());
    }
}
