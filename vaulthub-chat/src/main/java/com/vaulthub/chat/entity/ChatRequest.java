package com.vaulthub.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String senderVaultId;    // who sent the request

    @Column(nullable = false)
    private String receiverVaultId;  // who should receive it

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @CreationTimestamp
    private LocalDateTime createdAt;

    public enum Status {
        PENDING, ACCEPTED, REJECTED
    }
}