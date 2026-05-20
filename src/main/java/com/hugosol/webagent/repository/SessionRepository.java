package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.Session;
import com.hugosol.webagent.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionRepository extends JpaRepository<Session, String> {
    List<Session> findByStatusOrderByStartTimeDesc(SessionStatus status);
    List<Session> findAllByOrderByStartTimeDesc();
}
