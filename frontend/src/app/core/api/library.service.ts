import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { messages } from '../messages';

export interface SeerrInfo {
  requestId: number;
  requestedByUsername: string;
  requestedByAvatar: string;
  requestedAt: string;
}

export interface SeasonCard {
  seasonNumber: number;
  episodeFileCount: number;
  totalEpisodes: number;
  sizeOnDisk: number;
  monitored: boolean;
  plexUniqueViewers: number | null;
}

export interface MediaCard {
  mediaType: string;
  radarrId: number;
  tmdbId: number;
  title: string;
  year: number;
  posterUrl: string | null;
  sizeOnDisk: number;
  hasFile: boolean;
  monitored: boolean;
  addedAt: string;
  seerr: SeerrInfo | null;
  plexViewCount: number | null;
  plexUniqueViewers: number | null;
}

export interface ShowCard {
  mediaType: string;
  sonarrId: number;
  tvdbId: number;
  title: string;
  year: number;
  posterUrl: string | null;
  sizeOnDisk: number;
  monitored: boolean;
  addedAt: string;
  seasons: SeasonCard[];
  seerr: SeerrInfo | null;
  plexWatchedEpisodes: number | null;
  plexTotalEpisodes: number | null;
  plexUniqueViewers: number | null;
}

@Injectable({ providedIn: 'root' })
export class LibraryService {
  readonly movies = signal<MediaCard[]>([]);
  readonly moviesLoading = signal<boolean>(false);
  readonly moviesError = signal<string | null>(null);

  readonly shows = signal<ShowCard[]>([]);
  readonly showsLoading = signal<boolean>(false);
  readonly showsError = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  loadMovies(): void {
    this.moviesLoading.set(true);
    this.moviesError.set(null);
    this.http.get<MediaCard[]>('/api/library').subscribe({
      next: (data) => {
        this.movies.set(data);
        this.moviesLoading.set(false);
      },
      error: () => {
        this.moviesError.set(messages.library.moviesLoadError);
        this.moviesLoading.set(false);
      },
    });
  }

  loadShows(): void {
    this.showsLoading.set(true);
    this.showsError.set(null);
    this.http.get<ShowCard[]>('/api/library/shows').subscribe({
      next: (data) => {
        // Sonarr returns sizeOnDisk = 0 at the series level; recompute from seasons
        const normalized = data.map((show) => ({
          ...show,
          sizeOnDisk:
            show.sizeOnDisk > 0
              ? show.sizeOnDisk
              : show.seasons.reduce((sum, s) => sum + s.sizeOnDisk, 0),
        }));
        this.shows.set(normalized);
        this.showsLoading.set(false);
      },
      error: () => {
        this.showsError.set(messages.library.showsLoadError);
        this.showsLoading.set(false);
      },
    });
  }

  removeMovie(radarrId: number): void {
    this.movies.update((ms) => ms.filter((m) => m.radarrId !== radarrId));
  }

  removeShow(sonarrId: number): void {
    this.shows.update((ss) => ss.filter((s) => s.sonarrId !== sonarrId));
  }

  removeSeason(sonarrId: number, seasonNumber: number): void {
    this.shows.update((ss) =>
      ss.map((s) =>
        s.sonarrId === sonarrId
          ? { ...s, seasons: s.seasons.filter((sn) => sn.seasonNumber !== seasonNumber) }
          : s,
      ),
    );
  }
}
