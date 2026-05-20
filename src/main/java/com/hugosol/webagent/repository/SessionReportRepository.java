package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.SessionReport;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SessionReportRepository extends JpaRepository<SessionReport, String> {
    Optional<SessionReport> findBySessionId(String sessionId);
}
