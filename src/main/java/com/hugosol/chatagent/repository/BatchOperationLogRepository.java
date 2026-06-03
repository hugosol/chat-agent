package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.BatchOperationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchOperationLogRepository extends JpaRepository<BatchOperationLog, String> {
}
