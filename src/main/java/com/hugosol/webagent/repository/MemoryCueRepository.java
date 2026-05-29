package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryCue;
import com.hugosol.webagent.model.MemoryCueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemoryCueRepository extends JpaRepository<MemoryCue, String> {

    List<MemoryCue> findBySessionId(String sessionId);

    List<MemoryCue> findAllByStatus(MemoryCueStatus status);

    Optional<MemoryCue> findTopByUserIdAndModeAndStatusOrderByCreateTimeDesc(
            String userId, AgentMode mode, MemoryCueStatus status);
}
