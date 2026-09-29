import { Component, inject } from '@angular/core';
import { JobStatus, JobsService } from '../../../core/api/jobs.service';
import { messages } from '../../../core/messages';
import { dayAndTime } from '../../../core/util/date.util';

@Component({
  selector: 'scheduled-jobs',
  standalone: true,
  templateUrl: './scheduled-jobs.component.html',
})
export class ScheduledJobsComponent {
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
    if (job.key === 'user-sync') return 'Toutes les heures';
    // The purge hour comes from PURGE_HOUR on the server; the next run carries it.
    const time = job.nextRunAt
      ? new Date(job.nextRunAt).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })
      : null;
    return time ? `Tous les jours à ${time}` : 'Tous les jours';
  }

  run(job: JobStatus) {
    this.jobsService.runAndNotify(job.key).subscribe();
  }
}
