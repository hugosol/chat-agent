package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.AssertionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssertionGroupRepository extends JpaRepository<AssertionGroup, String> {

    Optional<AssertionGroup> findByName(String name);
}
