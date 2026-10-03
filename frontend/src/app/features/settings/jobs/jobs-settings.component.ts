import { Component, inject } from '@angular/core';
import { JobStatus, JobsService } from '../../../core/api/jobs.service';
import { messages } from '../../../core/messages';
import { describeSchedule } from '../../../core/util/cron.util';
import { dayAndTime } from '../../../core/util/date.util';

@Component({
  selector: 'app-jobs-settings',
  standalone: true,
  templateUrl: './jobs-settings.component.html',
})
export class JobsSettingsComponent {
  private jobsService = inject(JobsService);

  readonly jobs = this.jobsService.jobs;
  readonly dayAndTime = dayAndTime;

  constructor() {
    this.jobsService.load();
  }

  name(job: JobStatus): string {
    return messages.jobs.names[job.key];
  }

  schedule(job: JobStatus): string {
    return describeSchedule(job.cron, job.nextRunAt);
  }

  run(job: JobStatus) {
    this.jobsService.runAndNotify(job.key).subscribe();
  }
}
