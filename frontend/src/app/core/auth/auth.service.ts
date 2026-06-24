import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, interval, switchMap, takeWhile, tap } from 'rxjs';

export interface User {
  id: number;
  username: string;
  email: string;
  avatarUrl: string;
  admin: boolean;
  countedUsersTotal: number;
}

export interface StartPinResponse {
  pinId: number;
  code: string;
  authUrl: string;
}

export interface PollPinResponse {
  status: 'PENDING' | 'SUCCESS' | 'EXPIRED';
  user: User | null;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly me = signal<User | null>(null);
  readonly loading = signal<boolean>(false);

  constructor(private http: HttpClient, private router: Router) {}

  startPin(): Observable<StartPinResponse> {
    return this.http.post<StartPinResponse>('/api/auth/plex/pin', {});
  }

  pollPin(pinId: number): Observable<PollPinResponse> {
    return interval(2000).pipe(
      switchMap(() => this.http.get<PollPinResponse>(`/api/auth/plex/pin/${pinId}`)),
      tap((res) => {
        if (res.status === 'SUCCESS' && res.user) {
          this.me.set(res.user);
        }
      }),
      takeWhile((res) => res.status === 'PENDING', true),
    );
  }

  fetchMe(): Observable<User> {
    return this.http.get<User>('/api/me').pipe(
      tap((user) => this.me.set(user)),
    );
  }

  logout(): Observable<void> {
    return this.http.post<void>('/api/auth/logout', {}).pipe(
      tap(() => {
        this.me.set(null);
        this.router.navigate(['/login']);
      }),
    );
  }
}
