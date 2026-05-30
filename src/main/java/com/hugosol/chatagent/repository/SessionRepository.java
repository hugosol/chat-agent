package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.Session;
import com.hugosol.chatagent.model.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SessionRepository extends JpaRepository<Session, String> {
    List<Session> findByStatusOrderByStartTimeDesc(SessionStatus status);
    List<Session> findAllByOrderByStartTimeDesc();
    List<Session> findByUserIdOrderByStartTimeDesc(String userId);
}
