package com.vaulthub.file.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "files")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FileRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🧠 Original file name
    @Column(nullable = false)
    private String fileName;

    // 🧠 File size in bytes
    @Column(nullable = false)
    private Long fileSize;

    // 🧠 e.g. "application/pdf" or "image/png"
    @Column(nullable = false)
    private String mimeType;

    // 🧠 How many chunks this file was split into
    @Column(nullable = false)
    private Integer totalChunks;

    // 🧠 Who owns this file (user ID from auth service)
    @Column(nullable = false)
    private Long ownerId;

    // 🧠 Unique ID for this file (used in chunk filenames)
    @Column(nullable = false, unique = true)
    private String fileUuid;

    // 🧠 UPLOADING → READY → DELETED
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FileStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    public enum FileStatus {
        UPLOADING, READY, DELETED
    }
}