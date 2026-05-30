package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.SessionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SessionReportRepository extends JpaRepository<SessionReport, String> {
    Optional<SessionReport> findBySessionId(String sessionId);
}
