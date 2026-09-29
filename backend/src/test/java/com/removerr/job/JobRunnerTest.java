package com.removerr.job;

import com.removerr.job.dto.JobStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class JobRunnerTest {

    private final TaskScheduler scheduler = mock(TaskScheduler.class);

    @Test
    void runNow_returnsResultAndRecordsSuccess() {
        Job<String> job = job(userId -> "done");
        JobRunner runner = new JobRunner(List.of(job), scheduler, false);

        assertThat(runner.runNow(job, 1L)).isEqualTo("done");

        JobStatusResponse status = runner.statuses().getFirst();
        assertThat(status.lastRunSucceeded()).isTrue();
        assertThat(status.lastRunAt()).isNotNull();
        assertThat(status.running()).isFalse();
    }

    @Test
    void runNow_recordsFailureAndRethrows() {
        Job<String> job = job(userId -> { throw new IllegalArgumentException("boom"); });
        JobRunner runner = new JobRunner(List.of(job), scheduler, false);

        assertThatThrownBy(() -> runner.runNow(job, null)).hasMessage("boom");
        assertThat(runner.statuses().getFirst().lastRunSucceeded()).isFalse();
    }

    @Test
    void runNow_rejectsOverlappingRun() {
        var job = new Job<String>() {
            JobRunner runner;
            Throwable overlap;
            public String key() { return "test"; }
            public String cron() { return "0 0 3 * * *"; }
            public String run(Long triggeredByUserId) {
                try {
                    runner.runNow(this, null);
                } catch (IllegalStateException e) {
                    overlap = e;
                }
                return "outer";
            }
        };
        job.runner = new JobRunner(List.of(job), scheduler, false);

        job.runner.runNow(job, null);

        assertThat(job.overlap).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void statuses_exposeNextRunAndUnknownLastRun() {
        JobRunner runner = new JobRunner(List.of(job(userId -> "x")), scheduler, false);

        JobStatusResponse status = runner.statuses().getFirst();

        assertThat(Instant.parse(status.nextRunAt())).isAfter(Instant.now());
        assertThat(status.lastRunAt()).isNull();
        assertThat(status.lastRunSucceeded()).isNull();
    }

    @Test
    void scheduleAll_alsoStartsStartupJobsWhenEnabled() {
        Job<String> startupJob = new Job<>() {
            public String key() { return "startup"; }
            public String cron() { return "0 0 * * * *"; }
            public boolean runAtStartup() { return true; }
            public String run(Long triggeredByUserId) { return "x"; }
        };

        new JobRunner(List.of(startupJob), scheduler, true).scheduleAll();
        new JobRunner(List.of(startupJob), scheduler, false).scheduleAll();

        verify(scheduler, times(2)).schedule(any(Runnable.class), any(CronTrigger.class));
        verify(scheduler, times(1)).schedule(any(Runnable.class), any(Instant.class));
    }

    private static Job<String> job(Function<Long, String> body) {
        return new Job<>() {
            public String key() { return "test"; }
            public String cron() { return "0 0 3 * * *"; }
            public String run(Long triggeredByUserId) { return body.apply(triggeredByUserId); }
        };
    }
}
