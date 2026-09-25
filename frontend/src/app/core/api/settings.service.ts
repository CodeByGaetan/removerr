import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface AppSettings {
  plexServerUrl: string | null;
  radarrUrl: string | null;
  radarrApiKeyConfigured: boolean;
  sonarrUrl: string | null;
  sonarrApiKeyConfigured: boolean;
  seerrUrl: string | null;
  seerrApiKeyConfigured: boolean;
  trashRetentionDays: number;
}

export interface SaveSettingsRequest {
  plexServerUrl?: string | null;
  radarrUrl?: string | null;
  radarrApiKey?: string | null;
  sonarrUrl?: string | null;
  sonarrApiKey?: string | null;
  seerrUrl?: string | null;
  seerrApiKey?: string | null;
  trashRetentionDays?: number | null;
}

export type ServiceStatus = 'OK' | 'NOT_CONFIGURED' | 'AUTH_FAILED' | 'UNREACHABLE';

export interface ServiceTestResult {
  status: ServiceStatus;
  error: string | null;
}

export interface ConnectionTestResult {
  plex: ServiceTestResult;
  radarr: ServiceTestResult;
  sonarr: ServiceTestResult;
  seerr: ServiceTestResult;
}

@Injectable({ providedIn: 'root' })
export class SettingsService {
  readonly settings = signal<AppSettings | null>(null);
  readonly loading = signal(false);

  constructor(private http: HttpClient) {}

  load(): void {
    this.loading.set(true);
    this.http.get<AppSettings>('/api/settings').subscribe({
      next: (s) => { this.settings.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  save(payload: SaveSettingsRequest): Observable<void> {
    return this.http.put<void>('/api/settings', payload);
  }

  test(form: SaveSettingsRequest): Observable<ConnectionTestResult> {
    return this.http.post<ConnectionTestResult>('/api/settings/test', form);
  }
}
