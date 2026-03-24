package com.vaulthub.file.dto;

import lombok.*;
import java.time.LocalDateTime;

// 🧠 DTO = what we send TO the frontend
//    Never send the full entity directly!
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FileResponse {

    private Long id;
    private String fileName;
    private Long fileSize;
    private String mimeType;
    private Integer totalChunks;
    private String fileUuid;
    private String status;
    private LocalDateTime createdAt;

    // 🧠 Helper — human readable file size
    // e.g. 1048576 → "1.0 MB"
    public String getFileSizeFormatted() {
        if (fileSize == null) return "0 B";
        if (fileSize < 1024) return fileSize + " B";
        if (fileSize < 1024 * 1024)
            return String.format("%.1f KB", fileSize / 1024.0);
        if (fileSize < 1024 * 1024 * 1024)
            return String.format("%.1f MB", fileSize / (1024.0 * 1024));
        return String.format("%.1f GB", fileSize / (1024.0 * 1024 * 1024));
    }
}