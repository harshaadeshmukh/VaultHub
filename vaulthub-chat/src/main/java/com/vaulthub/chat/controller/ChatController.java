package com.vaulthub.chat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaulthub.chat.entity.ChatRequest;
import com.vaulthub.chat.entity.Message;
import com.vaulthub.chat.repository.MessageRepository;
import com.vaulthub.chat.service.ChatService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final MessageRepository messageRepo;
    private final SimpMessagingTemplate broker;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // GET /chat — server-side render contacts + pending for instant load
    @GetMapping("/chat")
    public String chatPage(HttpSession session, Model model) {
        String vaultId  = session.getAttribute("vaultId")  != null ? session.getAttribute("vaultId").toString()  : "";
        String fullName = session.getAttribute("fullName") != null ? session.getAttribute("fullName").toString() : "";
        model.addAttribute("myVaultId",  vaultId);
        model.addAttribute("myFullName", fullName);
        model.addAttribute("profilePhoto", session.getAttribute("profilePhoto") != null ? session.getAttribute("profilePhoto").toString() : "");

        // ── Pre-load contacts + pending IN PARALLEL ──────────────────────
        CompletableFuture<String> contactsFuture = CompletableFuture.supplyAsync(() -> {
            try {
                List<ChatRequest> accepted = chatService.getAcceptedContacts(vaultId);
                // Fetch all auth info in parallel
                List<CompletableFuture<Map<String, Object>>> futures = accepted.stream().map(r ->
                    CompletableFuture.supplyAsync(() -> {
                        String otherId = r.getSenderVaultId().equals(vaultId)
                                ? r.getReceiverVaultId() : r.getSenderVaultId();
                        String otherName = otherId, otherEmail = "", otherPhoto = "";
                        try {
                            Map<?,?> auth = restTemplate.getForObject(
                                "http://localhost:8081/api/users/by-vaultid?vaultId=" + otherId, Map.class);
                            if (auth != null && Boolean.TRUE.equals(auth.get("found"))) {
                                otherName  = auth.get("fullName")     != null ? (String) auth.get("fullName")     : otherId;
                                otherEmail = auth.get("email")        != null ? (String) auth.get("email")        : "";
                                otherPhoto = auth.get("profilePhoto") != null ? (String) auth.get("profilePhoto") : "";
                            }
                        } catch (Exception ignored) {}
                        Map<String, Object> m = new HashMap<>();
                        m.put("vaultId",      otherId);
                        m.put("fullName",     otherName);
                        m.put("email",        otherEmail);
                        m.put("profilePhoto", otherPhoto);
                        m.put("roomId",       chatService.buildRoomId(vaultId, otherId));
                        return m;
                    })
                ).collect(Collectors.toList());

                List<Map<String, Object>> contacts = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                return objectMapper.writeValueAsString(contacts);
            } catch (Exception e) { return "[]"; }
        });

        CompletableFuture<String> pendingFuture = CompletableFuture.supplyAsync(() -> {
            try {
                List<ChatRequest> pending = chatService.getPendingRequests(vaultId);
                List<CompletableFuture<Map<String, Object>>> futures = pending.stream().map(r ->
                    CompletableFuture.supplyAsync(() -> {
                        String senderId = r.getSenderVaultId();
                        String senderName = senderId, senderEmail = "", senderPhoto = "";
                        try {
                            Map<?,?> auth = restTemplate.getForObject(
                                "http://localhost:8081/api/users/by-vaultid?vaultId=" + senderId, Map.class);
                            if (auth != null && Boolean.TRUE.equals(auth.get("found"))) {
                                senderName  = auth.get("fullName")     != null ? (String) auth.get("fullName")     : senderId;
                                senderEmail = auth.get("email")        != null ? (String) auth.get("email")        : "";
                                senderPhoto = auth.get("profilePhoto") != null ? (String) auth.get("profilePhoto") : "";
                            }
                        } catch (Exception ignored) {}
                        Map<String, Object> m = new HashMap<>();
                        m.put("id",            r.getId());
                        m.put("senderVaultId", senderId);
                        m.put("senderName",    senderName);
                        m.put("senderEmail",   senderEmail);
                        m.put("senderPhoto",   senderPhoto);
                        return m;
                    })
                ).collect(Collectors.toList());

                List<Map<String, Object>> pendingList = futures.stream()
                    .map(CompletableFuture::join)
                    .collect(Collectors.toList());
                return objectMapper.writeValueAsString(pendingList);
            } catch (Exception e) { return "[]"; }
        });

        // Wait for both futures together — total time = slowest single call, not sum
        CompletableFuture.allOf(contactsFuture, pendingFuture).join();

        model.addAttribute("initialContacts", contactsFuture.join());
        model.addAttribute("initialPending",  pendingFuture.join());

        return "chat";
    }

    // GET /chat/history/{roomId} — load past messages
    @GetMapping("/chat/history/{roomId}")
    @ResponseBody
    public ResponseEntity<?> history(@PathVariable String roomId, HttpSession session) {
        Object vObj = session.getAttribute("vaultId");
        if (vObj == null) return ResponseEntity.status(401).build();
        String myVaultId = vObj.toString();
        if (!roomId.contains(myVaultId)) {
            Map<String, Object> err = new HashMap<>();
            err.put("error", "Access denied");
            return ResponseEntity.status(403).body(err);
        }
        return ResponseEntity.ok(chatService.getChatHistory(roomId));
    }

    // WebSocket: browser sends to /app/chat.send
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload Map<String, String> payload) {
        String senderVaultId   = payload.get("from");
        String receiverVaultId = payload.get("to");
        String content         = payload.get("content");

        if (senderVaultId == null || receiverVaultId == null
                || content == null || content.isBlank()) return;

        if (!chatService.areFriends(senderVaultId, receiverVaultId)) return;

        String roomId = chatService.buildRoomId(senderVaultId, receiverVaultId);

        Message msg = messageRepo.save(Message.builder()
            .senderVaultId(senderVaultId)
            .receiverVaultId(receiverVaultId)
            .content(content)
            .roomId(roomId)
            .build());

        Map<String, Object> out = new HashMap<>();
        out.put("id",            msg.getId());
        out.put("senderVaultId", msg.getSenderVaultId());
        out.put("content",       msg.getContent());
        out.put("sentAt",        msg.getSentAt().toString());

        broker.convertAndSend("/topic/chat/" + roomId, out);
    }
}
