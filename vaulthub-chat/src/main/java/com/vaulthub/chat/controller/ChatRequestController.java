package com.vaulthub.chat.controller;

import com.vaulthub.chat.entity.ChatRequest;
import com.vaulthub.chat.service.ChatService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatRequestController {

    private final ChatService chatService;
    private final RestTemplate restTemplate;

    private String getVaultId(HttpSession session) {
        Object v = session.getAttribute("vaultId");
        if (v == null) throw new RuntimeException("Not authenticated");
        return v.toString();
    }

    private String getMyEmail(HttpSession session) {
        Object v = session.getAttribute("email");
        // Spring Security stores the principal as SPRING_SECURITY_CONTEXT,
        // but auth service also puts email in session directly via customSuccessHandler
        // Fall back to reading from security context name if needed
        return (v != null) ? v.toString() : "";
    }

    private Map<String, Object> error(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("error", msg);
        return m;
    }

    private Map<String, Object> ok(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("message", msg);
        return m;
    }

    // ── GET /chat/lookup-by-email?email=user@example.com
    //    Called by frontend when user types a full email in the Add modal.
    //    Only returns a result if it is an exact email match.
    @GetMapping("/lookup-by-email")
    public ResponseEntity<Map<String, Object>> lookupByEmail(
            @RequestParam String email, HttpSession session) {

        String myVaultId = getVaultId(session);

        // Block looking up yourself — compare by email stored in session
        Object myEmailObj = session.getAttribute("email");
        if (myEmailObj != null && myEmailObj.toString().equalsIgnoreCase(email)) {
            return ResponseEntity.badRequest().body(error("That's your own account"));
        }

        try {
            Map result = restTemplate.getForObject(
                "http://localhost:8081/api/users/by-email?email=" + email, Map.class);

            Map<String, Object> resp = new HashMap<>();
            if (result == null || Boolean.FALSE.equals(result.get("found"))) {
                resp.put("found", false);
                return ResponseEntity.ok(resp);
            }

            String targetVaultId = (String) result.get("vaultId");

            // Also block if it's the same user by vaultId
            if (myVaultId.equals(targetVaultId)) {
                return ResponseEntity.badRequest().body(error("That's your own account"));
            }

            boolean alreadyFriends = chatService.areFriends(myVaultId, targetVaultId);
            boolean requestPending = chatService.hasPendingRequest(myVaultId, targetVaultId);

            resp.put("found",          true);
            resp.put("fullName",       result.get("fullName"));
            resp.put("vaultId",        targetVaultId);
            resp.put("email",          email);
            resp.put("alreadyFriends", alreadyFriends);
            resp.put("requestPending", requestPending);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("found", false);
            return ResponseEntity.ok(resp);
        }
    }

    // ── POST /chat/request?targetVaultId=XYZ
    @PostMapping("/request")
    public ResponseEntity<Map<String, Object>> sendRequest(
            @RequestParam String targetVaultId, HttpSession session) {
        String myVaultId = getVaultId(session);
        if (myVaultId.equals(targetVaultId)) {
            return ResponseEntity.badRequest().body(error("Cannot send request to yourself"));
        }
        try {
            ChatRequest req = chatService.sendRequest(myVaultId, targetVaultId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("message",   "Request sent");
            resp.put("requestId", req.getId());
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    // ── POST /chat/accept/{requestId}
    @PostMapping("/accept/{requestId}")
    public ResponseEntity<Map<String, Object>> accept(
            @PathVariable Long requestId, HttpSession session) {
        String myVaultId = getVaultId(session);
        try {
            ChatRequest req = chatService.acceptRequest(requestId, myVaultId);
            String roomId   = chatService.buildRoomId(req.getSenderVaultId(), req.getReceiverVaultId());
            Map<String, Object> resp = new HashMap<>();
            resp.put("roomId",  roomId);
            resp.put("message", "Request accepted");
            return ResponseEntity.ok(resp);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    // ── POST /chat/reject/{requestId}
    @PostMapping("/reject/{requestId}")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable Long requestId, HttpSession session) {
        String myVaultId = getVaultId(session);
        try {
            chatService.rejectRequest(requestId, myVaultId);
            return ResponseEntity.ok(ok("Request rejected"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

    // ── GET /chat/requests/pending
    @GetMapping("/requests/pending")
    public ResponseEntity<List<Map<String, Object>>> getPending(HttpSession session) {
        String myVaultId = getVaultId(session);
        List<ChatRequest> pending = chatService.getPendingRequests(myVaultId);

        List<Map<String, Object>> enriched = new ArrayList<>();
        for (ChatRequest r : pending) {
            String senderName = r.getSenderVaultId();
            try {
                Map authResult = restTemplate.getForObject(
                    "http://localhost:8081/api/users/by-vaultid?vaultId=" + r.getSenderVaultId(), Map.class);
                if (authResult != null && Boolean.TRUE.equals(authResult.get("found"))) {
                    senderName = (String) authResult.get("fullName");
                }
            } catch (Exception ignored) {}

            Map<String, Object> m = new HashMap<>();
            m.put("id",            r.getId());
            m.put("senderVaultId", r.getSenderVaultId());
            m.put("senderName",    senderName);
            m.put("createdAt",     r.getCreatedAt().toString());
            enriched.add(m);
        }
        return ResponseEntity.ok(enriched);
    }

    // ── GET /chat/contacts
    @GetMapping("/contacts")
    public ResponseEntity<List<Map<String, Object>>> getContacts(HttpSession session) {
        String myVaultId = getVaultId(session);
        List<ChatRequest> accepted = chatService.getAcceptedContacts(myVaultId);

        List<Map<String, Object>> contacts = new ArrayList<>();
        for (ChatRequest r : accepted) {
            String otherVaultId = r.getSenderVaultId().equals(myVaultId)
                ? r.getReceiverVaultId() : r.getSenderVaultId();
            String otherName = otherVaultId;
            String otherEmail = "";
            try {
                Map authResult = restTemplate.getForObject(
                    "http://localhost:8081/api/users/by-vaultid?vaultId=" + otherVaultId, Map.class);
                if (authResult != null && Boolean.TRUE.equals(authResult.get("found"))) {
                    otherName = (String) authResult.get("fullName");
                    otherEmail = authResult.get("email") != null ? (String) authResult.get("email") : "";
                }
            } catch (Exception ignored) {}

            Map<String, Object> m = new HashMap<>();
            m.put("vaultId",  otherVaultId);
            m.put("fullName", otherName);
            m.put("email",    otherEmail);
            m.put("roomId",   chatService.buildRoomId(myVaultId, otherVaultId));
            contacts.add(m);
        }
        return ResponseEntity.ok(contacts);
    }

    // ── DELETE /chat/disconnect?targetVaultId=XYZ
    //    Removes the accepted connection and deletes all messages in the room.
    @DeleteMapping("/disconnect")
    public ResponseEntity<Map<String, Object>> disconnect(
            @RequestParam String targetVaultId, HttpSession session) {
        String myVaultId = getVaultId(session);
        try {
            chatService.disconnect(myVaultId, targetVaultId);
            return ResponseEntity.ok(ok("Connection removed"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(error(e.getMessage()));
        }
    }

}