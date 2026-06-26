package com.hugosol.chatagent.config;

import com.hugosol.chatagent.model.AssertionGroup;
import com.hugosol.chatagent.model.FsrsParameters;
import com.hugosol.chatagent.model.User;
import com.hugosol.chatagent.repository.AssertionGroupRepository;
import com.hugosol.chatagent.repository.UserRepository;
import com.hugosol.chatagent.service.FsrsParametersService;
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
    private final FsrsParametersService fsrsParametersService;
    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;
    private final AssertionGroupRepository assertionGroupRepository;

    public DataInitializer(UserRepository userRepository,
                            FsrsParametersService fsrsParametersService,
                            AppProperties appProperties,
                            PasswordEncoder passwordEncoder,
                            JdbcTemplate jdbcTemplate,
                            AssertionGroupRepository assertionGroupRepository) {
        this.userRepository = userRepository;
        this.fsrsParametersService = fsrsParametersService;
        this.appProperties = appProperties;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
        this.assertionGroupRepository = assertionGroupRepository;
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
        initAssertionGroups();

        migrateDropSessionsModeCheckConstraint();
        migrateDropOtherModeCheckConstraints();

        try {
            jdbcTemplate.execute("ALTER TABLE memory_cues DROP COLUMN IF EXISTS tags");
            log.info("Migrated: dropped tags column from memory_cues");
        } catch (Exception e) {
            log.debug("Tags column migration skipped or already applied: {}", e.getMessage());
        }

        migrateEnabledColumn();

        try {
            jdbcTemplate.execute("ALTER TABLE user_preferences DROP COLUMN IF EXISTS timezone");
            log.info("Migrated: dropped timezone column from user_preferences");
        } catch (Exception e) {
            log.debug("Timezone column migration skipped or already applied: {}", e.getMessage());
        }

        migrateWordsLowerSpaces();
    }

    private void migrateEnabledColumn() {
        try {
            jdbcTemplate.execute("UPDATE users SET enabled = true WHERE enabled IS NULL");
            log.info("Migrated: backfilled enabled column for existing users");
        } catch (Exception e) {
            log.debug("Enabled column migration skipped or already applied: {}", e.getMessage());
        }
    }

    private void migrateDropSessionsModeCheckConstraint() {
        try {
            jdbcTemplate.execute("ALTER TABLE sessions ALTER COLUMN mode VARCHAR NOT NULL");
            log.info("Migrated: redefined sessions.mode column (removed old CHECK constraint)");
        } catch (Exception e) {
            log.debug("Mode CHECK constraint migration skipped: {}", e.getMessage());
        }
    }

    private void migrateDropOtherModeCheckConstraints() {
        try {
            jdbcTemplate.execute("ALTER TABLE memory_cues ALTER COLUMN mode VARCHAR NOT NULL");
            log.info("Migrated: redefined memory_cues.mode column (removed old CHECK constraint)");
        } catch (Exception e) {
            log.debug("memory_cues mode CHECK constraint migration skipped: {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE user_learning_profiles ALTER COLUMN mode VARCHAR");
            log.info("Migrated: redefined user_learning_profiles.mode column (removed old CHECK constraint)");
        } catch (Exception e) {
            log.debug("user_learning_profiles mode CHECK constraint migration skipped: {}", e.getMessage());
        }
    }

    private void migrateWordsLowerSpaces() {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE subtitle_lines SET words_lower = ' ' || words_lower || ' ' WHERE words_lower NOT LIKE ' %'");
            if (updated > 0) {
                log.info("Migrated: added boundary spaces to {} subtitle_lines.words_lower rows", updated);
            }
        } catch (Exception e) {
            log.debug("wordsLower spaces migration skipped: {}", e.getMessage());
        }
    }

    private void initAssertionGroups() {
        if (assertionGroupRepository.findByName("error-pattern").isEmpty()) {
            assertionGroupRepository.save(
                    new AssertionGroup("error-pattern", "Grammar and word choice error patterns recurring in the user's conversations"));
            log.info("Created seed assertion group: error-pattern");
        } else {
            log.info("Assertion group already exists: error-pattern");
        }
    }

    private void initFsrsParameters() {
        var allUsers = userRepository.findAll();
        for (var user : allUsers) {
            if (fsrsParametersService.get(user.getId()) == null) {
                FsrsParameters defaults = FsrsParameters.defaults(user.getId());
                fsrsParametersService.save(defaults);
                log.info("Created default FsrsParameters for user: {}", user.getUsername());
            }
        }
    }
}
