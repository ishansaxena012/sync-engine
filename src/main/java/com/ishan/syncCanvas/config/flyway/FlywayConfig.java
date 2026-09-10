package com.ishan.syncCanvas.config.flyway;

import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway adoption settings for a database that already existed before Flyway was
 * wired in.
 *
 * <p>The schema was originally created by Hibernate {@code ddl-auto=update}, so it has
 * the V1/V2 tables but no {@code flyway_schema_history}. Flyway refuses to touch a
 * non-empty schema without a history table unless told to baseline; baselining at 2
 * records the schema as already at the V2 level, so only V3 and later are applied. On a
 * genuinely empty database baseline never triggers and V1..Vn all run normally. Once a
 * history table exists this is inert.
 *
 * <p>Lives in Java rather than {@code application.yml} deliberately: the yml files are
 * git-ignored in this project, so anything placed there never reaches the deployed
 * image — this must ship with the code.
 */
@Configuration
public class FlywayConfig {

    @Bean
    FlywayConfigurationCustomizer baselineExistingSchema() {
        return configuration -> configuration
                .baselineOnMigrate(true)
                .baselineVersion("2");
    }
}
