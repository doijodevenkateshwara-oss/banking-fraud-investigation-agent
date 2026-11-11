package com.bfs.fraudcase.agent.langchain4j;

import com.bfs.fraudcase.agent.common.CaseConversation;
import com.bfs.fraudcase.agent.common.ProcessorUnavailableException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * LangChain4j follow-up assistant with bounded, persistent memory keyed by caseId.
 *
 * @author Venkateshwara Doijode
 */
@Component
public class Lc4jCaseConversation implements CaseConversation {

    private final ChatModel chatModel;
    private final CaseMemory memory;
    private final String systemPrompt;

    public Lc4jCaseConversation(ChatModel fraudChatModel, CaseMemory memory) {
        this.chatModel = fraudChatModel;
        this.memory = memory;
        try {
            this.systemPrompt = new ClassPathResource("prompts/followup-system.txt").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String answer(String caseId, String caseContext, String question) {
        FollowUpAssistant assistant = AiServices.builder(FollowUpAssistant.class)
                .chatModel(chatModel)
                .chatMemoryProvider(memory)
                .systemMessageProvider(id -> {
                    if (!caseId.equals(id)) {
                        throw new IllegalStateException("memory/case mismatch");
                    }
                    return systemPrompt + "\n<untrusted_data>\n" + caseContext + "\n</untrusted_data>";
                })
                .build();
        try {
            return assistant.answer(caseId, question);
        } catch (RuntimeException e) {
            throw new ProcessorUnavailableException("Follow-up assistant unavailable (" + e.getClass().getSimpleName() + ")", e);
        }
    }
}
