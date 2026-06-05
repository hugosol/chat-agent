package com.hugosol.chatagent.repository;

import com.hugosol.chatagent.model.FsrsParameters;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FsrsParametersRepository extends JpaRepository<FsrsParameters, String> {
    Optional<FsrsParameters> findByUserId(String userId);
}
