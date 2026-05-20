package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.ErrorRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ErrorRecordRepository extends JpaRepository<ErrorRecord, String> {
    List<ErrorRecord> findBySessionId(String sessionId);
}
