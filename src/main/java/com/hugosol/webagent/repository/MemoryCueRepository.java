package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryCue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryCueRepository extends JpaRepository<MemoryCue, String> {

    List<MemoryCue> findByUserIdAndMode(String userId, AgentMode mode);

    List<MemoryCue> findBySessionId(String sessionId);
}
