package com.removerr.plexuser;

import com.removerr.job.Job;
import org.springframework.stereotype.Component;

@Component
public class UserSyncJob implements Job<Void> {

    private final PlexUserService plexUserService;

    public UserSyncJob(PlexUserService plexUserService) {
        this.plexUserService = plexUserService;
    }

    @Override
    public String key() {
        return "user-sync";
    }

    @Override
    public String cron() {
        return "0 0 * * * *";
    }

    // The access flags may be stale after downtime, and a new install has no users yet.
    @Override
    public boolean runAtStartup() {
        return true;
    }

    @Override
    public Void run(Long triggeredByUserId) {
        plexUserService.syncWithServer();
        return null;
    }
}
