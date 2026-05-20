package com.hugosol.webagent.repository;

import com.hugosol.webagent.model.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProgressRepository extends JpaRepository<UserProgress, String> {
}
