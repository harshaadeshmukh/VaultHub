package com.vaulthub.chat.repository;

import com.vaulthub.chat.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByRoomIdOrderBySentAtAsc(String roomId);

    @Transactional
    void deleteByRoomId(String roomId);
}
