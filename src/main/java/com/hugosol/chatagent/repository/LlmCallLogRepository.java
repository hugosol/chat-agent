package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.LlmCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, String> {

    @Modifying
    @Query("DELETE FROM LlmCallLog WHERE createTime < :cutoff")
    void deleteByCreateTimeBefore(@Param("cutoff") Instant cutoff);
}
