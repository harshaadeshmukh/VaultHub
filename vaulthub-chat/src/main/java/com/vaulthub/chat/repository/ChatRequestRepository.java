package com.vaulthub.chat.repository;

import com.vaulthub.chat.entity.ChatRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ChatRequestRepository extends JpaRepository<ChatRequest, Long> {

    List<ChatRequest> findByReceiverVaultIdAndStatus(String receiverVaultId, ChatRequest.Status status);
    List<ChatRequest> findBySenderVaultIdAndStatus(String senderVaultId, ChatRequest.Status status);

    Optional<ChatRequest> findBySenderVaultIdAndReceiverVaultId(String senderVaultId, String receiverVaultId);

    boolean existsBySenderVaultIdAndReceiverVaultIdAndStatus(
        String senderVaultId, String receiverVaultId, ChatRequest.Status status);
}
