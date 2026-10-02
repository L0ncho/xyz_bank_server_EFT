package com.xyzbank.migration.shared.infrastructure.batch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobInstance;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.batch.job.enabled=false",
        "migration.run-all=false",
        "spring.datasource.url=jdbc:h2:mem:failedjobrestart;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@DisplayName("The failed job restart")
class TheFailedJobRestartTest {

    /*
     * Cases:
     * 1. First pass fails and the second pass of the same execution completes
     * 2. Two failures and then the process stops
     */

    @Autowired
    private RunAllMigrationsRunner runAllMigrationsRunner;

    @Autowired
    private JobExplorer jobExplorer;

    @Autowired
    private FailureBudget failureBudget;

    @Autowired
    @Qualifier("plannedFailureJob")
    private Job plannedFailureJob;

    @Test
    @DisplayName("completes on the second pass after the first pass fails")
    void completesOnTheSecondPassAfterTheFirstPassFails() throws Exception {
        failureBudget.failTimes(1);
        int instancesBefore = instancesOfPlannedFailureJob().size();

        runAllMigrationsRunner.launchJob(plannedFailureJob);

        List<JobInstance> instances = instancesOfPlannedFailureJob();
        assertEquals(instancesBefore + 1, instances.size());
        List<JobExecution> executions = jobExplorer.getJobExecutions(instances.get(0));
        assertEquals(2, executions.size());
        JobExecution firstPass = earliest(executions);
        JobExecution secondPass = latest(executions);
        assertEquals(BatchStatus.FAILED, firstPass.getStatus());
        assertEquals(BatchStatus.COMPLETED, secondPass.getStatus());
        assertEquals(firstPass.getJobInstance().getInstanceId(), secondPass.getJobInstance().getInstanceId());
        assertEquals(firstPass.getJobParameters(), secondPass.getJobParameters());
    }

    @Test
    @DisplayName("stops after the restarted execution fails again")
    void stopsAfterTheRestartedExecutionFailsAgain() {
        failureBudget.failTimes(2);
        int instancesBefore = instancesOfPlannedFailureJob().size();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> runAllMigrationsRunner.launchJob(plannedFailureJob));

        assertEquals("plannedFailureJob finished with status FAILED", error.getMessage());
        List<JobInstance> instances = instancesOfPlannedFailureJob();
        assertEquals(instancesBefore + 1, instances.size());
        List<JobExecution> executions = jobExplorer.getJobExecutions(instances.get(0));
        assertEquals(2, executions.size());
        assertEquals(BatchStatus.FAILED, earliest(executions).getStatus());
        assertEquals(BatchStatus.FAILED, latest(executions).getStatus());
        assertEquals(earliest(executions).getJobParameters(), latest(executions).getJobParameters());
    }

    private List<JobInstance> instancesOfPlannedFailureJob() {
        return jobExplorer.getJobInstances("plannedFailureJob", 0, 20);
    }

    private JobExecution earliest(List<JobExecution> executions) {
        return executions.stream()
                .min(Comparator.comparingLong(JobExecution::getId))
                .orElseThrow();
    }

    private JobExecution latest(List<JobExecution> executions) {
        return executions.stream()
                .max(Comparator.comparingLong(JobExecution::getId))
                .orElseThrow();
    }

    static class FailureBudget {

        private int remainingFailures;

        void failTimes(int times) {
            remainingFailures = times;
        }

        boolean consumeFailure() {
            if (remainingFailures <= 0) {
                return false;
            }
            remainingFailures--;
            return true;
        }
    }

    @TestConfiguration
    static class PlannedFailureJobConfig {

        @Bean
        FailureBudget failureBudget() {
            return new FailureBudget();
        }

        @Bean
        Job plannedFailureJob(
                JobRepository jobRepository,
                PlatformTransactionManager transactionManager,
                FailureBudget failureBudget) {
            Step plannedFailureStep = new StepBuilder("plannedFailureStep", Objects.requireNonNull(jobRepository))
                    .tasklet((contribution, chunkContext) -> {
                        if (failureBudget.consumeFailure()) {
                            throw new IllegalStateException("planned failure");
                        }
                        return RepeatStatus.FINISHED;
                    }, Objects.requireNonNull(transactionManager))
                    .build();
            return new JobBuilder("plannedFailureJob", jobRepository)
                    .start(Objects.requireNonNull(plannedFailureStep))
                    .build();
        }
    }
}
