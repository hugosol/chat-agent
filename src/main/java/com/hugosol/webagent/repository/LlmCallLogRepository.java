package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.LlmCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface LlmCallLogRepository extends JpaRepository<LlmCallLog, String> {

    @Modifying
    @Query("DELETE FROM LlmCallLog WHERE createTime < :cutoff")
    void deleteByCreateTimeBefore(@Param("cutoff") LocalDateTime cutoff);
}
