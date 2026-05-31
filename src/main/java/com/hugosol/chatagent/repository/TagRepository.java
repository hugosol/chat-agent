package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.Tag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TagRepository extends JpaRepository<Tag, String> {

    Optional<Tag> findByNameAndUserId(String name, String userId);

    Optional<Tag> findByNameIgnoreCaseAndUserId(String name, String userId);

    Optional<Tag> findByNameIgnoreCaseAndUserIdAndIdNot(String name, String userId, String id);

    List<Tag> findByUserId(String userId);

    List<Tag> findByUserIdAndType(String userId, String type);
}
