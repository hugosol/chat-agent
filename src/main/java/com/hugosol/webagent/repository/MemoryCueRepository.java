package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.MemoryCue;
import com.hugosol.webagent.model.MemoryCueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryCueRepository extends JpaRepository<MemoryCue, String> {

    List<MemoryCue> findBySessionId(String sessionId);

    List<MemoryCue> findAllByStatus(MemoryCueStatus status);
}
