package com.vaulthub.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String senderVaultId;

    @Column(nullable = false)
    private String receiverVaultId;

    @Column(nullable = false, length = 2000)
    private String content;

    @CreationTimestamp
    private LocalDateTime sentAt;

    // roomId = alphabetically sorted vaultIds joined by "_"
    // e.g. "ABC_XYZ" — same for both users, used for WebSocket topic
    @Column(nullable = false)
    private String roomId;
}