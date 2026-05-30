package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findBySessionIdOrderByCreateTimeAsc(String sessionId);
    void deleteBySessionId(String sessionId);
}
