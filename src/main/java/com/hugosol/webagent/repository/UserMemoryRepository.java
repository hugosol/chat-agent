package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.AgentMode;
import com.hugosol.webagent.model.MemoryType;
import com.hugosol.webagent.model.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserMemoryRepository extends JpaRepository<UserMemory, String> {

    Optional<UserMemory> findTopByUserIdAndTypeAndModeOrderByVersionDesc(String userId, MemoryType type, AgentMode mode);

    List<UserMemory> findByUserIdAndTypeAndModeOrderByVersionDesc(String userId, MemoryType type, AgentMode mode);
}
