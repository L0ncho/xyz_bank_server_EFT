package com.xyzbank.migration.shared.infrastructure.batch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.explore.JobExplorer;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class RunAllMigrationsRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(RunAllMigrationsRunner.class);

    private final boolean runAll;
    private final JobLauncher jobLauncher;
    private final JobOperator jobOperator;
    private final JobExplorer jobExplorer;
    private final Job dailyTransactionsJob;
    private final Job monthlyInterestsJob;
    private final Job annualGenerationJob;
    private final ConfigurableApplicationContext applicationContext;

    public RunAllMigrationsRunner(
            @Value("${migration.run-all:false}") boolean runAll,
            JobLauncher jobLauncher,
            JobOperator jobOperator,
            JobExplorer jobExplorer,
            @Qualifier("dailyTransactionsJob") Job dailyTransactionsJob,
            @Qualifier("monthlyInterestsJob") Job monthlyInterestsJob,
            @Qualifier("annualGenerationJob") Job annualGenerationJob,
            ConfigurableApplicationContext applicationContext) {
        this.runAll = runAll;
        this.jobLauncher = jobLauncher;
        this.jobOperator = jobOperator;
        this.jobExplorer = jobExplorer;
        this.dailyTransactionsJob = dailyTransactionsJob;
        this.monthlyInterestsJob = monthlyInterestsJob;
        this.annualGenerationJob = annualGenerationJob;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!runAll) {
            return;
        }
        runJobs();
        int exitCode = SpringApplication.exit(applicationContext, () -> 0);
        System.exit(exitCode);
    }

    void runJobs() throws Exception {
        launchJob(dailyTransactionsJob);
        launchJob(monthlyInterestsJob);
        launchJob(annualGenerationJob);
    }

    void launchJob(Job job) throws Exception {
        logger.info("Launching {}", job.getName());
        JobExecution execution = jobLauncher.run(
                job,
                new JobParametersBuilder()
                        .addLong("run.id", System.nanoTime())
                        .toJobParameters());
        if (execution.getStatus() == BatchStatus.FAILED) {
            execution = restartOnce(execution);
        }
        if (execution.getStatus() != BatchStatus.COMPLETED) {
            throw new IllegalStateException(job.getName() + " finished with status " + execution.getStatus());
        }
    }

    private JobExecution restartOnce(JobExecution failedExecution) throws Exception {
        logger.info(
                "Restarting {} execution {}",
                failedExecution.getJobInstance().getJobName(),
                failedExecution.getId());
        Long restartedExecutionId = jobOperator.restart(failedExecution.getId());
        JobExecution restartedExecution = jobExplorer.getJobExecution(restartedExecutionId);
        if (restartedExecution == null) {
            throw new IllegalStateException(
                    failedExecution.getJobInstance().getJobName() + " restart did not produce an execution");
        }
        return restartedExecution;
    }
}
