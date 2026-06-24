import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs';

export interface PlexUser {
  id: number;
  plexAccountId: number | null;
  name: string;
  admin: boolean;
  counted: boolean;
}

@Injectable({ providedIn: 'root' })
export class UsersService {
  readonly allUsers = signal<PlexUser[]>([]);
  readonly countedUsers = computed(() => this.allUsers().filter((u) => u.counted));
  readonly totalCounted = computed(() => this.countedUsers().length);

  constructor(private http: HttpClient) {}

  loadUsers(): void {
    this.http.get<PlexUser[]>('/api/users').subscribe({
      next: (users) => this.allUsers.set(users),
      error: (err) => console.warn('Failed to load users:', err),
    });
  }

  setCounted(id: number, counted: boolean) {
    return this.http.patch<PlexUser>(`/api/users/${id}`, { counted }).pipe(
      tap((updated) => {
        this.allUsers.update((users) =>
          users.map((u) => (u.id === updated.id ? updated : u))
        );
      })
    );
  }
}
