package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.MemoryAssertion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryAssertionRepository extends JpaRepository<MemoryAssertion, String> {

    List<MemoryAssertion> findBySessionId(String sessionId);

    List<MemoryAssertion> findByEnabled(boolean enabled);

    List<MemoryAssertion> findByUserIdAndMode(String userId, AgentMode mode);
}
