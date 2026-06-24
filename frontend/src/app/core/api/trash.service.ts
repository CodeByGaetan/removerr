import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface TrashItem {
  id: number;
  mediaType: 'MOVIE' | 'SHOW' | 'SEASON';
  externalService: string;
  externalId: number;
  tmdbId: number | null;
  title: string;
  year: number | null;
  posterUrl: string | null;
  sizeBytes: number;
  trashedAt: string;
  purgeAt: string;
  seerrRequestedBy: string | null;
  trashedByUserId: number;
}

export interface PurgeResult {
  purged: number;
  failed: number;
}

@Injectable({ providedIn: 'root' })
export class TrashService {
  readonly trashItems = signal<TrashItem[]>([]);
  readonly loading = signal(false);

  constructor(private http: HttpClient) {}

  loadTrash(): void {
    this.loading.set(true);
    this.http.get<TrashItem[]>('/api/trash').subscribe({
      next: (items) => {
        this.trashItems.set(items);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  restore(id: number): Observable<TrashItem> {
    return this.http.post<TrashItem>(`/api/trash/${id}/restore`, {}).pipe(
      tap(() => this.trashItems.update((items) => items.filter((i) => i.id !== id))),
    );
  }

  purge(): Observable<PurgeResult> {
    return this.http.post<PurgeResult>('/api/admin/purge', {});
  }

  trashMovie(radarrId: number, immediate = false): Observable<TrashItem> {
    return this.http.post<TrashItem>('/api/trash/movies', { radarrId, immediate }).pipe(
      tap((item) => this.trashItems.update((items) => [item, ...items])),
    );
  }

  trashShow(sonarrId: number, immediate = false): Observable<TrashItem> {
    return this.http.post<TrashItem>('/api/trash/shows', { sonarrId, immediate }).pipe(
      tap((item) => this.trashItems.update((items) => [item, ...items])),
    );
  }

  trashSeason(sonarrId: number, seasonNumber: number, immediate = false): Observable<TrashItem> {
    return this.http.post<TrashItem>('/api/trash/seasons', { sonarrId, seasonNumber, immediate }).pipe(
      tap((item) => this.trashItems.update((items) => [item, ...items])),
    );
  }
}
