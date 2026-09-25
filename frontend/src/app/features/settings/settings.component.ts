import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { Observable, forkJoin, timer } from 'rxjs';
import { ConnectionTestResult, SaveSettingsRequest, SettingsService } from '../../core/api/settings.service';
import { PlexUser, UsersService } from '../../core/api/users.service';
import { AuthService } from '../../core/auth/auth.service';
import { LeaveConfirmable } from '../../core/guards/unsaved-changes.guard';
import { messages } from '../../core/messages';
import { ToastService } from '../../core/services/toast.service';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';
import { UserAvatarComponent } from '../../shared/components/user-avatar/user-avatar.component';

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [NgTemplateOutlet, ConfirmModalComponent, UserAvatarComponent],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.css',
  host: { '(window:beforeunload)': 'onBeforeUnload($event)' },
})
export class SettingsComponent implements LeaveConfirmable {
  private settingsService = inject(SettingsService);
  private usersService = inject(UsersService);
  private auth = inject(AuthService);
  private toast = inject(ToastService);

  readonly loading = this.settingsService.loading;
  readonly allUsers = this.usersService.allUsers;

  pendingCounted = signal<Map<number, boolean>>(new Map());

  readonly countedUsers = computed(() => {
    const m = this.pendingCounted();
    return this.usersService.allUsers().filter((u) =>
      m.has(u.id) ? m.get(u.id)! : u.counted,
    );
  });

  // Form fields
  plexUrl = signal('');
  radarrUrl = signal('');
  radarrKey = signal('');
  editRadarrKey = signal(false);
  sonarrUrl = signal('');
  sonarrKey = signal('');
  editSonarrKey = signal(false);
  seerrUrl = signal('');
  seerrKey = signal('');
  editSeerrKey = signal(false);
  retentionDays = signal(30);

  saving = signal(false);
  testing = signal(false);
  testResults = signal<ConnectionTestResult | null>(null);

  private leaveResolver = signal<((leave: boolean) => void) | null>(null);
  readonly confirmingLeave = computed(() => this.leaveResolver() !== null);

  readonly hasUnsavedChanges = computed(() => {
    const s = this.settingsService.settings();
    if (!s) return false;
    return (
      this.plexUrl() !== (s.plexServerUrl ?? '') ||
      this.radarrUrl() !== (s.radarrUrl ?? '') ||
      this.sonarrUrl() !== (s.sonarrUrl ?? '') ||
      this.seerrUrl() !== (s.seerrUrl ?? '') ||
      this.retentionDays() !== (s.trashRetentionDays ?? 30) ||
      (this.editRadarrKey() && this.radarrKey() !== '') ||
      (this.editSonarrKey() && this.sonarrKey() !== '') ||
      (this.editSeerrKey() && this.seerrKey() !== '') ||
      this.pendingCounted().size > 0
    );
  });

  constructor() {
    this.settingsService.load();
    if (this.usersService.allUsers().length === 0) {
      this.usersService.loadUsers();
    }

    effect(() => {
      const s = this.settingsService.settings();
      if (!s) return;
      this.plexUrl.set(s.plexServerUrl ?? '');
      this.radarrUrl.set(s.radarrUrl ?? '');
      this.sonarrUrl.set(s.sonarrUrl ?? '');
      this.seerrUrl.set(s.seerrUrl ?? '');
      this.retentionDays.set(s.trashRetentionDays ?? 30);
      this.editRadarrKey.set(!s.radarrApiKeyConfigured);
      this.editSonarrKey.set(!s.sonarrApiKeyConfigured);
      this.editSeerrKey.set(!s.seerrApiKeyConfigured);
    });
  }

  radarrConfigured = computed(
    () => this.settingsService.settings()?.radarrApiKeyConfigured ?? false,
  );
  sonarrConfigured = computed(
    () => this.settingsService.settings()?.sonarrApiKeyConfigured ?? false,
  );
  seerrConfigured = computed(
    () => this.settingsService.settings()?.seerrApiKeyConfigured ?? false,
  );

  readonly retentionPresets = [7, 14, 30, 60, 90];
  readonly retentionMin = 1;
  readonly retentionMax = 365;

  // Out-of-range or partial input keeps the last valid value; the field is re-synced on blur.
  onRetentionInput(raw: string) {
    const days = Number(raw);
    if (Number.isInteger(days) && days >= this.retentionMin && days <= this.retentionMax) {
      this.retentionDays.set(days);
    }
  }

  private formValues(): SaveSettingsRequest {
    return {
      plexServerUrl: this.plexUrl() || null,
      radarrUrl: this.radarrUrl() || null,
      radarrApiKey: this.editRadarrKey() ? this.radarrKey() || null : null,
      sonarrUrl: this.sonarrUrl() || null,
      sonarrApiKey: this.editSonarrKey() ? this.sonarrKey() || null : null,
      seerrUrl: this.seerrUrl() || null,
      seerrApiKey: this.editSeerrKey() ? this.seerrKey() || null : null,
      trashRetentionDays: this.retentionDays(),
    };
  }

  onSave() {
    this.saving.set(true);

    const requests: Observable<unknown>[] = [
      this.settingsService.save(this.formValues()),
      ...Array.from(this.pendingCounted().entries()).map(([id, counted]) =>
        this.usersService.setCounted(id, counted),
      ),
    ];

    forkJoin(requests).subscribe({
      next: () => {
        this.toast.show(messages.settings.saved);
        this.pendingCounted.set(new Map());
        this.settingsService.load();
        this.auth.fetchMe().subscribe();
        this.saving.set(false);
      },
      error: () => {
        this.toast.show(messages.settings.saveError, 'error');
        this.saving.set(false);
      },
    });
  }

  onTest() {
    this.testing.set(true);
    this.testResults.set(null);
    // A fast identical response would otherwise look like the click did nothing.
    forkJoin([this.settingsService.test(this.formValues()), timer(400)]).subscribe({
      next: ([results]) => {
        this.testResults.set(results);
        this.testing.set(false);
      },
      error: () => {
        this.toast.show(messages.settings.testError, 'error');
        this.testing.set(false);
      },
    });
  }

  userCounted(user: PlexUser): boolean {
    const m = this.pendingCounted();
    return m.has(user.id) ? m.get(user.id)! : user.counted;
  }

  confirmLeave(): boolean | Promise<boolean> {
    if (!this.hasUnsavedChanges()) return true;
    return new Promise((resolve) => this.leaveResolver.set(resolve));
  }

  resolveLeave(leave: boolean) {
    this.leaveResolver()?.(leave);
    this.leaveResolver.set(null);
  }

  onBeforeUnload(event: BeforeUnloadEvent) {
    if (!this.hasUnsavedChanges()) return;
    event.preventDefault();
    // Older Safari only shows the native prompt when returnValue is set.
    event.returnValue = '';
  }

  onToggleUser(user: PlexUser) {
    const next = !this.userCounted(user);
    this.pendingCounted.update((m) => {
      const updated = new Map(m);
      if (user.counted === next) {
        updated.delete(user.id);
      } else {
        updated.set(user.id, next);
      }
      return updated;
    });
  }

  statusClass(status: string): string {
    if (status === 'OK') return 'status-ok';
    if (status === 'AUTH_FAILED') return 'status-warn';
    if (status === 'UNREACHABLE') return 'status-down';
    return 'status-unknown'; // NOT_CONFIGURED
  }

  statusLabel(status: string): string {
    if (status === 'OK') return 'Connecté';
    if (status === 'AUTH_FAILED') return 'Auth invalide';
    if (status === 'UNREACHABLE') return 'Injoignable';
    return 'Non configuré';
  }
}
