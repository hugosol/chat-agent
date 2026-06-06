package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.FsrsRescheduleLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FsrsRescheduleLogRepository extends JpaRepository<FsrsRescheduleLog, String> {

    Page<FsrsRescheduleLog> findByUserIdOrderByStartTimeDesc(String userId, Pageable pageable);
}
