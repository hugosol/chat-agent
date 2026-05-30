package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.ErrorRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ErrorRecordRepository extends JpaRepository<ErrorRecord, String> {
    List<ErrorRecord> findBySessionId(String sessionId);
}
