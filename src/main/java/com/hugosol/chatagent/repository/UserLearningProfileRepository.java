package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.AgentMode;
import com.hugosol.chatagent.model.LearningType;
import com.hugosol.chatagent.model.UserLearningProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserLearningProfileRepository extends JpaRepository<UserLearningProfile, String> {
    Optional<UserLearningProfile> findTopByUserIdAndTypeAndModeOrderByVersionDesc(
            String userId, LearningType type, AgentMode mode);
    List<UserLearningProfile> findByUserIdAndTypeAndModeOrderByVersionDesc(
            String userId, LearningType type, AgentMode mode);
}
