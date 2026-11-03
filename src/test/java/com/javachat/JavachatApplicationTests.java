package com.javachat;

import com.javachat.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class JavachatApplicationTests extends IntegrationTest {

    @Test
    void contextLoadsAndMigrationsApplyToARealDatabase() {
        Integer applied = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        org.assertj.core.api.Assertions.assertThat(applied).isEqualTo(4);
    }
}
