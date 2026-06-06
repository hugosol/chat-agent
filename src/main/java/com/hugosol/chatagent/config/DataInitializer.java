package com.hugosol.chatagent.config;

import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.User;
import com.hugosol.chatagent.repository.FsrsParametersRepository;
import com.hugosol.chatagent.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final FsrsParametersRepository fsrsParametersRepository;
    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    public DataInitializer(UserRepository userRepository,
                            FsrsParametersRepository fsrsParametersRepository,
                            AppProperties appProperties,
                            PasswordEncoder passwordEncoder,
                            JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.fsrsParametersRepository = fsrsParametersRepository;
        this.appProperties = appProperties;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        for (var user : appProperties.getInitialUsers()) {
            if (userRepository.findByUsername(user.username()).isEmpty()) {
                User entity = new User(user.username(), passwordEncoder.encode(user.password()));
                userRepository.save(entity);
                log.info("Created initial user: {}", user.username());
            } else {
                log.info("Initial user already exists: {}", user.username());
            }
        }

        initFsrsParameters();

        try {
            jdbcTemplate.execute("ALTER TABLE memory_cues DROP COLUMN IF EXISTS tags");
            log.info("Migrated: dropped tags column from memory_cues");
        } catch (Exception e) {
            log.debug("Tags column migration skipped or already applied: {}", e.getMessage());
        }

        migrateEnabledColumn();
    }

    private void migrateEnabledColumn() {
        try {
            jdbcTemplate.execute("UPDATE users SET enabled = true WHERE enabled IS NULL");
            log.info("Migrated: backfilled enabled column for existing users");
        } catch (Exception e) {
            log.debug("Enabled column migration skipped or already applied: {}", e.getMessage());
        }
    }

    private void initFsrsParameters() {
        var allUsers = userRepository.findAll();
        for (var user : allUsers) {
            if (fsrsParametersRepository.findByUserId(user.getId()).isEmpty()) {
                FsrsParameters defaults = FsrsParameters.defaults(user.getId());
                fsrsParametersRepository.save(defaults);
                log.info("Created default FsrsParameters for user: {}", user.getUsername());
            }
        }
    }
}
