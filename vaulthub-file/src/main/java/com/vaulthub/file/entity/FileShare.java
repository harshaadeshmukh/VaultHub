package com.vaulthub.file.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_shares",
        uniqueConstraints = @UniqueConstraint(columnNames = {"fileUuid", "sharedWithEmail"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FileShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileUuid;

    // ── Owner side ──────────────────────────
    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String ownerEmail;

    // ── Recipient side ───────────────────────
    @Column(nullable = false)
    private String sharedWithEmail;

    @Column(nullable = false)
    private Long sharedWithOwnerId;

    @Column(nullable = false)
    private String sharedWithName;

    // ── Expiry ───────────────────────────────
    // null = never expires
    @Column
    private LocalDateTime expiresAt;

    // ── Status ───────────────────────────────
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime sharedAt;

    // ── Helper ───────────────────────────────
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
}
