package com.vaulthub.file.controller;

import com.vaulthub.file.dto.FileResponse;
import com.vaulthub.file.entity.ActivityLog;
import com.vaulthub.file.repository.ActivityLogRepository;
import com.vaulthub.file.service.FileService;
import com.vaulthub.file.service.ShareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileApiController {

    private final FileService fileService;
    private final ShareService shareService;
    private final ActivityLogRepository activityLogRepository;

    // ══════════════════════════════════════════
    //  GET /api/files/stats?ownerId=xxx
    // ══════════════════════════════════════════
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats(@RequestParam Long ownerId) {
        try {
            List<FileResponse> files = fileService.getFilesForUser(ownerId);
            long totalBytes  = fileService.getTotalStorageBytes(ownerId);
            long sharedCount = shareService.getFilesSharedWithMe(ownerId).size();

            Map<String, Object> stats = new HashMap<>();
            stats.put("fileCount",   files.size());
            stats.put("totalBytes",  totalBytes);
            stats.put("sharedCount", sharedCount);
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Stats fetch failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("fileCount", 0, "totalBytes", 0L, "sharedCount", 0));
        }
    }

    // ══════════════════════════════════════════
    //  GET /api/files/recent?ownerId=xxx&limit=5
    // ══════════════════════════════════════════
    @GetMapping("/recent")
    public ResponseEntity<List<FileResponse>> getRecentFiles(
            @RequestParam Long ownerId,
            @RequestParam(defaultValue = "5") int limit) {
        try {
            return ResponseEntity.ok(
                fileService.getFilesForUser(ownerId).stream().limit(limit).toList());
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    // ══════════════════════════════════════════
    //  GET /api/files/analytics?ownerId=xxx
    //  Returns all analytics data for dashboard
    // ══════════════════════════════════════════
    @GetMapping("/analytics")
    public ResponseEntity<Map<String, Object>> getAnalytics(@RequestParam Long ownerId) {
        try {
            Map<String, Object> result = new HashMap<>();

            // ── 1. Upload trend — last 7 days ─────────────────────
            LocalDateTime since = LocalDate.now().minusDays(6).atStartOfDay();
            List<Object[]> uploadRows = activityLogRepository.countUploadsByDay(ownerId, since);

            // Build a full 7-day map (fill zeros for missing days)
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM d");
            Map<String, Long> uploadMap = new LinkedHashMap<>();
            for (int i = 6; i >= 0; i--) {
                uploadMap.put(LocalDate.now().minusDays(i).format(fmt), 0L);
            }
            for (Object[] row : uploadRows) {
                // row[0] is java.sql.Date or LocalDate depending on DB
                String dayStr;
                if (row[0] instanceof java.sql.Date sd) {
                    dayStr = sd.toLocalDate().format(fmt);
                } else if (row[0] instanceof LocalDate ld) {
                    dayStr = ld.format(fmt);
                } else {
                    dayStr = row[0].toString();
                }
                if (uploadMap.containsKey(dayStr)) {
                    uploadMap.put(dayStr, ((Number) row[1]).longValue());
                }
            }
            result.put("uploadTrendLabels", new ArrayList<>(uploadMap.keySet()));
            result.put("uploadTrendData",   new ArrayList<>(uploadMap.values()));

            // ── 2. File type breakdown (pie chart) ────────────────
            List<FileResponse> allFiles = fileService.getFilesForUser(ownerId);
            Map<String, Long> typeCount = allFiles.stream()
                    .collect(Collectors.groupingBy(
                            f -> simplifyMime(f.getMimeType()),
                            Collectors.counting()
                    ));
            // Sort descending
            List<Map.Entry<String, Long>> sorted = typeCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .toList();
            result.put("typeLabels", sorted.stream().map(Map.Entry::getKey).toList());
            result.put("typeData",   sorted.stream().map(Map.Entry::getValue).toList());

            // ── 3. Top 5 most viewed files ────────────────────────
            List<Object[]> topViewed = activityLogRepository.topViewedFiles(
                    ownerId, PageRequest.of(0, 5));
            List<Map<String, Object>> topViewedList = topViewed.stream().map(row -> {
                Map<String, Object> m = new HashMap<>();
                m.put("fileUuid", row[0]);
                m.put("fileName", row[1]);
                m.put("views",    ((Number) row[2]).longValue());
                return m;
            }).toList();
            result.put("topViewed", topViewedList);

            // ── 4. Storage used vs limit (in MB) ─────────────────
            long totalBytes = fileService.getTotalStorageBytes(ownerId);
            long limitBytes = 1024L * 1024 * 1024; // 1 GB default limit
            result.put("storageUsedMB",  totalBytes / (1024.0 * 1024));
            result.put("storageLimitMB", limitBytes / (1024.0 * 1024));
            result.put("storagePercent", Math.min(100.0, (totalBytes * 100.0) / limitBytes));

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Analytics fetch failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "uploadTrendLabels", List.of(),
                "uploadTrendData",   List.of(),
                "typeLabels",        List.of(),
                "typeData",          List.of(),
                "topViewed",         List.of(),
                "storageUsedMB",     0.0,
                "storageLimitMB",    1024.0,
                "storagePercent",    0.0
            ));
        }
    }

    // ── Mime → friendly label ─────────────────
    private String simplifyMime(String mime) {
        if (mime == null) return "Other";
        if (mime.startsWith("image/"))      return "Images";
        if (mime.equals("application/pdf")) return "PDFs";
        if (mime.startsWith("video/"))      return "Videos";
        if (mime.startsWith("audio/"))      return "Audio";
        if (mime.startsWith("text/"))       return "Text";
        if (mime.contains("zip") || mime.contains("compressed") || mime.contains("tar"))
            return "Archives";
        if (mime.contains("word") || mime.contains("document")) return "Docs";
        if (mime.contains("sheet") || mime.contains("excel"))   return "Sheets";
        return "Other";
    }
}
