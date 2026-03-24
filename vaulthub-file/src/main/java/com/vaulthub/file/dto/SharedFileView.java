package com.vaulthub.file.dto;

import lombok.*;

import java.time.LocalDateTime;

// ════════════════════════════════════════════════════════
//  SharedFileView — combines FileShare + FileResponse
//  into one flat object for the "Shared with me" tab.
//
//  Thymeleaf can't index into a List, so we flatten
//  the share record and the file record into one DTO
//  that shared.html can iterate directly.
// ════════════════════════════════════════════════════════

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class SharedFileView {

    // From FileShare
    private Long shareId;
    private String ownerEmail;
    private LocalDateTime sharedAt;

    // From FileResponse
    private String fileUuid;
    private String fileName;
    private String fileSizeFormatted;
    private String mimeType;
}