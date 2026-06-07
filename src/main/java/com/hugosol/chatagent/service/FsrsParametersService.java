package com.hugosol.chatagent.service;

import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.repository.FsrsParametersRepository;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FsrsParametersService {

    private final FsrsParametersRepository parametersRepository;

    public FsrsParametersService(FsrsParametersRepository parametersRepository) {
        this.parametersRepository = parametersRepository;
    }

    @Cacheable(value = "fsrsParameters", key = "#userId")
    public FsrsParameters get(String userId) {
        return parametersRepository.findByUserId(userId).orElse(null);
    }

    @Transactional
    @CacheEvict(value = "fsrsParameters", key = "#parameters.userId")
    public FsrsParameters save(FsrsParameters parameters) {
        return parametersRepository.save(parameters);
    }
}
