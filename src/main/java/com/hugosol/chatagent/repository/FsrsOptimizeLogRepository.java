package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.FsrsOptimizeLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FsrsOptimizeLogRepository extends JpaRepository<FsrsOptimizeLog, String> {

    Page<FsrsOptimizeLog> findByUserIdOrderByStartTimeDesc(String userId, Pageable pageable);
}
