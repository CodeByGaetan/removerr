package com.removerr.job;

/** A recurring background task, scheduled and tracked by {@link JobRunner}. */
public interface Job<T> {

    /** Stable identifier, used in the API and by the frontend to label the job. */
    String key();

    /** Spring cron expression, evaluated in the JVM timezone. */
    String cron();

    /** Whether to also run once in the background when the application starts. */
    default boolean runAtStartup() {
        return false;
    }

    /** @param triggeredByUserId user who launched it manually, {@code null} for a scheduled run */
    T run(Long triggeredByUserId);
}
