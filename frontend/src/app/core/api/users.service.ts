import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { finalize, tap } from 'rxjs';

export type Role = 'NONE' | 'USER' | 'ADMIN';
export type ServerAccess = 'OWNER' | 'SHARED' | 'HOME' | 'MANAGED';

// plexAccountId, serverAccess, role and lastLoginAt are only sent to admins (Settings › Users).
export interface PlexUser {
  id: number;
  plexAccountId: number | null;
  name: string;
  owner: boolean;
  serverAccess: ServerAccess;
  role: Role;
  counted: boolean;
  lastLoginAt: string | null;
}

export type UserChanges = Partial<Pick<PlexUser, 'counted' | 'role'>>;

@Injectable({ providedIn: 'root' })
export class UsersService {
  readonly allUsers = signal<PlexUser[]>([]);
  readonly loading = signal(false);
  readonly countedUsers = computed(() => this.allUsers().filter((u) => u.counted));
  readonly totalCounted = computed(() => this.countedUsers().length);

  constructor(private http: HttpClient) {}

  loadUsers(): void {
    this.loading.set(true);
    this.http
      .get<PlexUser[]>('/api/users')
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (users) => this.allUsers.set(users),
        error: (err) => console.warn('Failed to load users:', err),
      });
  }

  update(id: number, changes: UserChanges) {
    return this.http.patch<PlexUser>(`/api/users/${id}`, changes).pipe(
      tap((updated) => {
        this.allUsers.update((users) =>
          users.map((u) => (u.id === updated.id ? updated : u))
        );
      })
    );
  }
}
