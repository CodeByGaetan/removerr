package com.removerr.job;

import com.removerr.job.dto.JobStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schedules every {@link Job} bean and is the single entry point to run one, so a manual
 * run can never overlap the scheduled one. Status is kept in memory only: after a restart
 * the last run is unknown until the job runs again.
 */
@Component
public class JobRunner {

    private static final Logger log = LoggerFactory.getLogger(JobRunner.class);

    private final List<Job<?>> jobs;
    private final TaskScheduler scheduler;
    private final boolean startupRunsEnabled;

    private final Set<String> running = ConcurrentHashMap.newKeySet();
    private final Map<String, LastRun> lastRuns = new ConcurrentHashMap<>();

    private record LastRun(Instant at, boolean succeeded) {}

    public JobRunner(List<Job<?>> jobs, TaskScheduler scheduler,
                     @Value("${removerr.jobs.run-at-startup:true}") boolean startupRunsEnabled) {
        this.jobs = jobs;
        this.scheduler = scheduler;
        this.startupRunsEnabled = startupRunsEnabled;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleAll() {
        for (Job<?> job : jobs) {
            scheduler.schedule(() -> runScheduled(job), new CronTrigger(job.cron()));
            log.info("Job '{}' scheduled with cron '{}'", job.key(), job.cron());
            if (startupRunsEnabled && job.runAtStartup()) {
                runInBackground(job.key(), null);
            }
        }
    }

    /** Runs the job in the caller's thread and returns its result. */
    public <T> T runNow(Job<T> job, Long triggeredByUserId) {
        if (!running.add(job.key())) {
            throw new IllegalStateException("Job '" + job.key() + "' is already running");
        }
        Instant start = Instant.now();
        try {
            T result = job.run(triggeredByUserId);
            lastRuns.put(job.key(), new LastRun(start, true));
            return result;
        } catch (RuntimeException e) {
            lastRuns.put(job.key(), new LastRun(start, false));
            throw e;
        } finally {
            running.remove(job.key());
        }
    }

    /** Starts the job in the background; its progress is visible through {@link #statuses()}. */
    public void runInBackground(String key, Long triggeredByUserId) {
        Job<?> job = find(key);
        if (running.contains(key)) {
            throw new IllegalStateException("Job '" + key + "' is already running");
        }
        scheduler.schedule(() -> runLogged(job, triggeredByUserId), Instant.now());
    }

    public List<JobStatusResponse> statuses() {
        return jobs.stream().map(this::toStatus).toList();
    }

    private void runScheduled(Job<?> job) {
        if (running.contains(job.key())) {
            log.info("Job '{}' still running — scheduled run skipped", job.key());
            return;
        }
        runLogged(job, null);
    }

    // Background runs have no caller to report to: failures end up in the logs and the status.
    private void runLogged(Job<?> job, Long triggeredByUserId) {
        try {
            runNow(job, triggeredByUserId);
        } catch (RuntimeException e) {
            log.error("Job '{}' failed", job.key(), e);
        }
    }

    private Job<?> find(String key) {
        return jobs.stream().filter(j -> j.key().equals(key)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown job '" + key + "'"));
    }

    private JobStatusResponse toStatus(Job<?> job) {
        LastRun last = lastRuns.get(job.key());
        ZonedDateTime next = CronExpression.parse(job.cron()).next(ZonedDateTime.now());
        return new JobStatusResponse(
                job.key(),
                running.contains(job.key()),
                next != null ? next.toInstant().toString() : null,
                last != null ? last.at().toString() : null,
                last != null ? last.succeeded() : null);
    }
}
