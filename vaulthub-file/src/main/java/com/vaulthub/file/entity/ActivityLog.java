package com.vaulthub.file.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_logs")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column
    private String fileName;

    @Column
    private String fileUuid;

    @Column
    private String detail;   // e.g. shared with / from email

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public enum ActivityType {
        UPLOAD, DOWNLOAD, DELETE, SHARE_SENT, SHARE_RECEIVED, LOGIN, VIEW
    }
}
