import { Component, computed, inject, signal } from '@angular/core';
import { TrashItem, TrashService } from '../../core/api/trash.service';
import { ToastService } from '../../core/services/toast.service';
import { AuthService } from '../../core/auth/auth.service';
import { FileSizePipe } from '../../core/pipes/file-size.pipe';
import { RelativeDatePipe } from '../../core/pipes/relative-date.pipe';
import { messages } from '../../core/messages';
import { MS_PER_DAY } from '../../core/util/date.util';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';

@Component({
  selector: 'app-trash',
  standalone: true,
  imports: [FileSizePipe, RelativeDatePipe, ConfirmModalComponent],
  templateUrl: './trash.component.html',
  styleUrl: './trash.component.css',
})
export class TrashComponent {
  private trashService = inject(TrashService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);

  readonly items = this.trashService.trashItems;
  readonly loading = this.trashService.loading;
  readonly isAdmin = computed(() => this.auth.me()?.admin ?? false);

  restoreTarget = signal<TrashItem | null>(null);
  restoring = signal(false);

  confirmingPurge = signal(false);
  purging = signal(false);

  count = computed(() => this.items().length);
  totalSize = computed(() => this.items().reduce((s, i) => s + i.sizeBytes, 0));

  constructor() {
    this.trashService.loadTrash();
  }

  daysLeft(purgeAt: string): number {
    return Math.max(0, Math.ceil((new Date(purgeAt).getTime() - Date.now()) / MS_PER_DAY));
  }

  urgencyClass(purgeAt: string): string {
    const d = this.daysLeft(purgeAt);
    if (d <= 1) return 'purge-critical';
    if (d <= 3) return 'purge-soon';
    return 'purge-normal';
  }

  badgeClass(type: string): string {
    if (type === 'MOVIE') return 'badge-movie';
    if (type === 'SHOW') return 'badge-show';
    return 'badge-season';
  }

  badgeLabel(type: string): string {
    if (type === 'MOVIE') return 'Film';
    if (type === 'SHOW') return 'Série';
    return 'Saison';
  }

  onRestore(item: TrashItem) {
    this.restoreTarget.set(item);
  }

  onConfirmRestore() {
    const item = this.restoreTarget();
    if (!item) return;
    this.restoring.set(true);
    this.trashService.restore(item.id).subscribe({
      next: () => {
        this.toast.show(messages.trash.restored(item.title));
        this.restoreTarget.set(null);
        this.restoring.set(false);
      },
      error: () => {
        this.toast.show(messages.trash.restoreError, 'error');
        this.restoreTarget.set(null);
        this.restoring.set(false);
      },
    });
  }

  onPurge() {
    this.confirmingPurge.set(true);
  }

  onConfirmPurge() {
    this.purging.set(true);
    this.trashService.purge().subscribe({
      next: (result) => {
        this.toast.show(messages.trash.purged(result.purged, result.failed));
        this.trashService.loadTrash();
        this.confirmingPurge.set(false);
        this.purging.set(false);
      },
      error: () => {
        this.toast.show(messages.trash.purgeError, 'error');
        this.confirmingPurge.set(false);
        this.purging.set(false);
      },
    });
  }
}
