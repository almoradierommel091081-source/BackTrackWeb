package org.example.backtrackweb;

import org.example.backtrackweb.support.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The application starts with a real PostgreSQL database attached.
 */
class BackTrackWebApplicationTests extends PostgresTestBase {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context).isNotNull();
    }
}
