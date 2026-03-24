package com.vaulthub.chat.service;

import com.vaulthub.chat.entity.*;
import com.vaulthub.chat.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRequestRepository requestRepo;
    private final MessageRepository messageRepo;

    // Build a consistent roomId from two vaultIds (always same order)
    public String buildRoomId(String a, String b) {
        return a.compareTo(b) < 0 ? a + "_" + b : b + "_" + a;
    }

    public ChatRequest sendRequest(String senderVaultId, String receiverVaultId) {
        // Block duplicate requests in both directions
        requestRepo.findBySenderVaultIdAndReceiverVaultId(senderVaultId, receiverVaultId)
            .ifPresent(r -> { throw new RuntimeException("Request already sent"); });
        requestRepo.findBySenderVaultIdAndReceiverVaultId(receiverVaultId, senderVaultId)
            .ifPresent(r -> { throw new RuntimeException("Request already exists from them — check pending requests"); });

        return requestRepo.save(ChatRequest.builder()
            .senderVaultId(senderVaultId)
            .receiverVaultId(receiverVaultId)
            .status(ChatRequest.Status.PENDING)
            .build());
    }

    public ChatRequest acceptRequest(Long requestId, String myVaultId) {
        ChatRequest req = requestRepo.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Request not found"));
        if (!req.getReceiverVaultId().equals(myVaultId))
            throw new RuntimeException("Not your request");
        req.setStatus(ChatRequest.Status.ACCEPTED);
        return requestRepo.save(req);
    }

    public void rejectRequest(Long requestId, String myVaultId) {
        ChatRequest req = requestRepo.findById(requestId)
            .orElseThrow(() -> new RuntimeException("Request not found"));
        if (!req.getReceiverVaultId().equals(myVaultId))
            throw new RuntimeException("Not your request");
        requestRepo.delete(req);
    }

    public List<ChatRequest> getPendingRequests(String myVaultId) {
        return requestRepo.findByReceiverVaultIdAndStatus(myVaultId, ChatRequest.Status.PENDING);
    }

    // All accepted contacts (requests in either direction)
    public List<ChatRequest> getAcceptedContacts(String myVaultId) {
        return Stream.concat(
            requestRepo.findBySenderVaultIdAndStatus(myVaultId, ChatRequest.Status.ACCEPTED).stream(),
            requestRepo.findByReceiverVaultIdAndStatus(myVaultId, ChatRequest.Status.ACCEPTED).stream()
        ).toList();
    }

    public List<Message> getChatHistory(String roomId) {
        return messageRepo.findByRoomIdOrderBySentAtAsc(roomId);
    }

    public boolean areFriends(String a, String b) {
        return requestRepo.existsBySenderVaultIdAndReceiverVaultIdAndStatus(a, b, ChatRequest.Status.ACCEPTED)
            || requestRepo.existsBySenderVaultIdAndReceiverVaultIdAndStatus(b, a, ChatRequest.Status.ACCEPTED);
    }

    public boolean hasPendingRequest(String a, String b) {
        return requestRepo.findBySenderVaultIdAndReceiverVaultId(a, b)
            .map(r -> r.getStatus() == ChatRequest.Status.PENDING).orElse(false)
            || requestRepo.findBySenderVaultIdAndReceiverVaultId(b, a)
            .map(r -> r.getStatus() == ChatRequest.Status.PENDING).orElse(false);
    }

    // Delete the accepted connection between two users AND all their messages
    public void disconnect(String myVaultId, String otherVaultId) {
        // Find the accepted request in either direction
        ChatRequest req = requestRepo.findBySenderVaultIdAndReceiverVaultId(myVaultId, otherVaultId)
            .filter(r -> r.getStatus() == ChatRequest.Status.ACCEPTED)
            .orElseGet(() -> requestRepo.findBySenderVaultIdAndReceiverVaultId(otherVaultId, myVaultId)
                .filter(r -> r.getStatus() == ChatRequest.Status.ACCEPTED)
                .orElseThrow(() -> new RuntimeException("No active connection found")));

        String roomId = buildRoomId(myVaultId, otherVaultId);

        // Delete all messages in the chat room
        messageRepo.deleteByRoomId(roomId);

        // Delete the connection record
        requestRepo.delete(req);
    }

}