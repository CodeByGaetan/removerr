import { Component, computed, inject, signal } from '@angular/core';
import { JobsService } from '../../../core/api/jobs.service';
import { PlexUser, Role, UserChanges, UsersService } from '../../../core/api/users.service';
import { AuthService } from '../../../core/auth/auth.service';
import { messages } from '../../../core/messages';
import { RelativeDatePipe } from '../../../core/pipes/relative-date.pipe';
import { ToastService } from '../../../core/services/toast.service';
import { ToggleSwitchComponent } from '../../../shared/components/toggle-switch/toggle-switch.component';
import { UserAvatarComponent } from '../../../shared/components/user-avatar/user-avatar.component';

const ROLE_LABELS: Record<Role, string> = {
  NONE: 'Aucun',
  USER: 'Utilisateur',
  ADMIN: 'Admin',
};

@Component({
  selector: 'app-users-settings',
  standalone: true,
  imports: [RelativeDatePipe, ToggleSwitchComponent, UserAvatarComponent],
  templateUrl: './users-settings.component.html',
})
export class UsersSettingsComponent {
  private usersService = inject(UsersService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  private jobsService = inject(JobsService);

  readonly loading = this.usersService.loading;
  // Display order of the role selector, from least to most privileged.
  readonly roles: Role[] = ['NONE', 'USER', 'ADMIN'];

  // Not sorted by role: the row would jump away from the cursor right after a change.
  readonly users = computed(() =>
    this.usersService.allUsers().slice().sort((a, b) =>
      Number(b.owner) - Number(a.owner) || a.name.localeCompare(b.name),
    ),
  );
  readonly accessCount = computed(() => this.users().filter((u) => u.role !== 'NONE').length);

  private pendingIds = signal<ReadonlySet<number>>(new Set());
  readonly syncing = signal(false);

  constructor() {
    this.usersService.loadUsers();
  }

  // Reloads even on failure: the sync may have imported accounts before failing.
  onSync() {
    this.syncing.set(true);
    this.jobsService.runAndNotify('user-sync').subscribe(() => {
      this.syncing.set(false);
      this.usersService.loadUsers();
    });
  }

  roleLabel(role: Role): string {
    return ROLE_LABELS[role];
  }

  isCurrentUser(user: PlexUser): boolean {
    return user.id === this.auth.me()?.id;
  }

  // Mirrors the backend rules: the owner's role and your own role are immutable.
  canEditRole(user: PlexUser): boolean {
    return !user.owner && !this.isCurrentUser(user);
  }

  isPending(user: PlexUser): boolean {
    return this.pendingIds().has(user.id);
  }

  onRoleChange(user: PlexUser, role: Role) {
    if (role === user.role) return;
    this.apply(user, { role }, () => this.toast.show(messages.users.roleUpdated(user.name, ROLE_LABELS[role])));
  }

  onToggleCounted(user: PlexUser) {
    // countedUsersTotal on /api/me feeds the "seen by x/y" ratio in the library.
    this.apply(user, { counted: !user.counted }, () => this.auth.fetchMe().subscribe());
  }

  private apply(user: PlexUser, changes: UserChanges, onSuccess: () => void) {
    this.setPending(user.id, true);
    this.usersService.update(user.id, changes).subscribe({
      next: () => {
        this.setPending(user.id, false);
        onSuccess();
      },
      error: () => {
        this.setPending(user.id, false);
        this.toast.show(messages.users.updateError, 'error');
      },
    });
  }

  private setPending(id: number, pending: boolean) {
    this.pendingIds.update((ids) => {
      const next = new Set(ids);
      if (pending) next.add(id); else next.delete(id);
      return next;
    });
  }
}
