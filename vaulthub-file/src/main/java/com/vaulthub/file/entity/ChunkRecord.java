package com.vaulthub.file.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "chunks")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChunkRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🧠 Which file this chunk belongs to
    @Column(nullable = false)
    private Long fileId;

    // 🧠 Order of this chunk (0, 1, 2, 3...)
    @Column(nullable = false)
    private Integer chunkIndex;

    // 🧠 Size of this chunk in bytes
    @Column(nullable = false)
    private Long chunkSize;

    // 🧠 Where this chunk is saved on disk
    // e.g. "./chunks/abc123_0.chunk"
    @Column(nullable = false)
    private String storagePath;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}