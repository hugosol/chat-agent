package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findBySessionIdOrderByTimestampAsc(String sessionId);
    void deleteBySessionId(String sessionId);
}
