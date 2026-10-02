package com.xyzbank.migration.dailytransactions.infrastructure.batch;

import com.xyzbank.migration.dailytransactions.application.ports.InMemoryDailyReportWriter;
import com.xyzbank.migration.shared.application.ports.InMemoryMigrationExecutionPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBatchTest
@SpringBootTest
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "migration.data.daily-transactions=file:data/legacy/movimientos_financieros_diarios.csv",
        "migration.batch.skip-limit=2000"
})
class TheOfficialLegacyDailyTransactionsJobTest {

    /*
     * Cases:
     * 1. Skips invalid rows of the official daily CSV and writes the valid ones
     */

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    @Qualifier("dailyTransactionsJob")
    private Job dailyTransactionsJob;

    @Autowired
    private InMemoryDailyReportWriter dailyReportWriter;

    @Autowired
    private InMemoryMigrationExecutionPort migrationExecutionPort;

    @BeforeEach
    void resetMigrationLedger() {
        migrationExecutionPort.clear();
    }

    @Test
    void skipsInvalidRowsOfTheOfficialDailyCsv() throws Exception {
        jobLauncherTestUtils.setJob(Objects.requireNonNull(dailyTransactionsJob));

        JobExecution execution = jobLauncherTestUtils.launchJob(
                new JobParametersBuilder()
                        .addLong("run.id", System.currentTimeMillis())
                        .toJobParameters()
        );

        StepExecution processStep = execution.getStepExecutions().stream()
                .filter(step -> "processDailyTransactions".equals(step.getStepName()))
                .findFirst()
                .orElseThrow();

        assertEquals(BatchStatus.COMPLETED, execution.getStatus());
        assertTrue(processStep.getSkipCount() > 0);
        assertTrue(dailyReportWriter.written().stream().noneMatch(transaction -> "1".equals(transaction.idValue())));
        assertTrue(dailyReportWriter.written().stream().anyMatch(transaction -> "2".equals(transaction.idValue())));
    }

    @TestConfiguration
    static class TestWriters {

        @Bean
        @Primary
        InMemoryDailyReportWriter dailyReportWriter() {
            return new InMemoryDailyReportWriter();
        }

        @Bean
        @Primary
        InMemoryMigrationExecutionPort migrationExecutionPort() {
            return new InMemoryMigrationExecutionPort();
        }
    }
}
