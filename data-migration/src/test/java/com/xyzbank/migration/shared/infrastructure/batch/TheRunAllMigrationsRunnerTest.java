package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("The run-all migrations runner")
class TheRunAllMigrationsRunnerTest {

    /*
     * Cases:
     * 1. Launches the three jobs in order against MySQL and a CSV fixture
     * 2. A second run against an already-migrated database still finishes successfully
     */

    @Container
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "always");
        registry.add("spring.sql.init.schema-locations", () -> "classpath:db/schema.sql");
        registry.add("migration.run-all", () -> "false");
        registry.add("spring.batch.job.enabled", () -> "false");
        registry.add("migration.data.daily-transactions", () -> "file:data/semana_3/transacciones.csv");
        registry.add("migration.data.monthly-interests", () -> "file:data/semana_3/intereses.csv");
        registry.add("migration.data.annual-accounts", () -> "file:data/semana_3/cuentas_anuales.csv");
        registry.add("migration.batch.skip-limit", () -> "2000");
    }

    @Autowired
    private RunAllMigrationsRunner runAllMigrationsRunner;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("launches the three jobs in order then finishes successfully")
    void launchesTheThreeJobsInOrderThenFinishesSuccessfully() throws Exception {
        runAllMigrationsRunner.runJobs();

        assertTrue(count("daily_transaction_reports") > 0);
        assertTrue(count("account_balances") > 0);
        assertTrue(count("annual_audit_reports") > 0);
        assertEquals("SUCCESS", statusOf("dailyTransactionsJob"));
        assertEquals("SUCCESS", statusOf("monthlyInterestsJob"));
        assertEquals("SUCCESS", statusOf("annualGenerationJob"));
    }

    @Test
    @DisplayName("exits successfully on a second run against an already-migrated database")
    void exitsSuccessfullyOnASecondRunAgainstAnAlreadyMigratedDatabase() throws Exception {
        runAllMigrationsRunner.runJobs();
        int dailyReports = count("daily_transaction_reports");

        runAllMigrationsRunner.runJobs();

        assertEquals(dailyReports, count("daily_transaction_reports"));
        assertEquals("SUCCESS", statusOf("dailyTransactionsJob"));
    }

    private int count(String table) {
        Integer value = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
        return value == null ? 0 : value;
    }

    private String statusOf(String jobName) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM migration_executions WHERE job_name = ?",
                String.class,
                jobName);
    }
}
