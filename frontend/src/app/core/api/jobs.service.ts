import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, last, map, of, switchMap, takeWhile, tap, timer } from 'rxjs';
import { messages } from '../messages';
import { ToastService } from '../services/toast.service';

export type JobKey = 'purge' | 'user-sync';

export interface JobStatus {
  key: JobKey;
  cron: string;
  running: boolean;
  nextRunAt?: string;
  lastRunAt?: string;
  lastRunSucceeded?: boolean;
}

const POLL_INTERVAL_MS = 2000;

@Injectable({ providedIn: 'root' })
export class JobsService {
  readonly jobs = signal<JobStatus[]>([]);
  private toast = inject(ToastService);

  constructor(private http: HttpClient) {}

  load(): void {
    this.fetch().subscribe({
      error: (err) => console.warn('Failed to load jobs:', err),
    });
  }

  /** Runs the job and toasts the outcome; emits whether it succeeded. */
  runAndNotify(key: JobKey): Observable<boolean> {
    const name = messages.jobs.names[key];
    return this.run(key).pipe(
      map((job) => job?.lastRunSucceeded === true),
      tap((succeeded) =>
        succeeded
          ? this.toast.show(messages.jobs.done(name))
          : this.toast.show(messages.jobs.failed(name), 'error'),
      ),
      catchError(() => {
        this.toast.show(messages.jobs.runError, 'error');
        this.load();
        return of(false);
      }),
    );
  }

  /** Starts the job, then polls until it finishes; emits its final status once. */
  private run(key: JobKey): Observable<JobStatus | undefined> {
    return this.http.post<void>(`/api/jobs/${key}/run`, {}).pipe(
      tap(() => this.jobs.update((jobs) => jobs.map((j) => (j.key === key ? { ...j, running: true } : j)))),
      switchMap(() => timer(POLL_INTERVAL_MS, POLL_INTERVAL_MS)),
      switchMap(() => this.fetch()),
      map((jobs) => jobs.find((j) => j.key === key)),
      takeWhile((job) => !!job?.running, true),
      last(),
    );
  }

  private fetch(): Observable<JobStatus[]> {
    return this.http.get<JobStatus[]>('/api/jobs').pipe(tap((jobs) => this.jobs.set(jobs)));
  }
}
