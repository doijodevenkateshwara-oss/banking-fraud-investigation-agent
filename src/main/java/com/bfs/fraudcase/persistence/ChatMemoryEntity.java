package com.bfs.fraudcase.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Case-keyed conversation memory (memoryId == caseId). Holds masked summaries only.
 *
 * @author Venkateshwara Doijode
 */
@Entity
@Table(name = "chat_memory")
public class ChatMemoryEntity {
    @Id
    public String memoryId;
    @Lob
    public String messagesJson;
    public Instant updatedAt;
}
