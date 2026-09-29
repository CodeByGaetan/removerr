package com.removerr.job;

import com.removerr.job.dto.JobStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class JobController {

    private final JobRunner jobRunner;

    public JobController(JobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    @GetMapping("/api/jobs")
    public List<JobStatusResponse> getJobs() {
        return jobRunner.statuses();
    }

    @PostMapping("/api/jobs/{key}/run")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void runJob(@PathVariable String key, @AuthenticationPrincipal Long userId) {
        jobRunner.runInBackground(key, userId);
    }
}
