import { Component, ElementRef, HostListener, computed, inject, input, output, signal } from '@angular/core';
import { LibraryService, MediaCard, ShowCard } from '../../../core/api/library.service';
import { TrashService } from '../../../core/api/trash.service';
import { AuthService } from '../../../core/auth/auth.service';
import { FileSizePipe } from '../../../core/pipes/file-size.pipe';
import { ToastService } from '../../../core/services/toast.service';
import { messages } from '../../../core/messages';
import { ConfirmModalComponent } from '../confirm-modal/confirm-modal.component';
import { RequesterChipComponent } from '../requester-chip/requester-chip.component';
import { TrashImmediateCheckboxComponent } from '../trash-immediate-checkbox/trash-immediate-checkbox.component';
import { ViewersRingComponent } from '../viewers-ring/viewers-ring.component';

@Component({
  selector: 'library-card',
  standalone: true,
  imports: [
    FileSizePipe,
    ViewersRingComponent,
    RequesterChipComponent,
    ConfirmModalComponent,
    TrashImmediateCheckboxComponent,
  ],
  templateUrl: './library-card.component.html',
  styleUrl: './library-card.component.css',
})
export class LibraryCardComponent {
  private auth = inject(AuthService);
  private library = inject(LibraryService);
  private trashService = inject(TrashService);
  private toast = inject(ToastService);
  private host = inject<ElementRef<HTMLElement>>(ElementRef);

  movie = input<MediaCard | null>(null);
  show = input<ShowCard | null>(null);
  open = output<string>();

  showModal = signal(false);
  trashing = signal(false);
  immediate = signal(false);
  hovered = signal(false);

  private readonly canHover =
    typeof window !== 'undefined' && window.matchMedia('(hover: hover)').matches;

  isAdmin = computed(() => this.auth.me()?.admin ?? false);

  posterUrl = computed(() => this.movie()?.posterUrl ?? this.show()?.posterUrl ?? null);
  title = computed(() => this.movie()?.title ?? this.show()?.title ?? '');
  year = computed(() => this.movie()?.year ?? this.show()?.year ?? 0);
  sizeOnDisk = computed(() => this.movie()?.sizeOnDisk ?? this.show()?.sizeOnDisk ?? 0);

  plexViewerUserIds = computed(
    () => this.movie()?.plexViewerUserIds ?? this.show()?.plexViewerUserIds ?? null,
  );
  totalCounted = computed(() => this.auth.me()?.countedUsersTotal ?? 0);

  seerr = computed(() => this.movie()?.seerr ?? this.show()?.seerr ?? null);

  seasonsInfo = computed(() => {
    const s = this.show();
    if (!s) return null;
    const seasons = s.seasons.filter((x) => x.seasonNumber > 0);
    const downloaded = seasons.filter((x) => x.episodeFileCount > 0).length;
    return { downloaded, total: seasons.length };
  });

  detailId = computed(() => {
    const m = this.movie();
    const s = this.show();
    if (m) return `movie-${m.radarrId}`;
    if (s) return `show-${s.sonarrId}`;
    return null;
  });

  ariaLabel = computed(() => {
    const kind = this.show() ? 'série' : 'film';
    const viewers = this.plexViewerUserIds();
    const total = this.totalCounted();
    const watchedPart = viewers !== null && total > 0 ? `, vu par ${viewers.length} sur ${total}` : '';
    return `${this.title()} (${this.year()}), ${kind}${watchedPart}. Entrée pour ouvrir, T pour supprimer.`;
  });

  onCardClick(_event: MouseEvent) {
    if (this.showModal()) return;
    const id = this.detailId();
    if (!id) return;
    if (this.canHover || this.hovered()) {
      this.open.emit(id);
    } else {
      this.hovered.set(true);
    }
  }

  onCardKeydown(event: KeyboardEvent) {
    if (this.showModal()) return;
    if (event.key === 'Enter' || event.key === ' ') {
      const id = this.detailId();
      if (id) {
        event.preventDefault();
        this.open.emit(id);
      }
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (!this.hovered()) return;
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.hovered.set(false);
    }
  }

  onTrash(event: Event) {
    event.stopPropagation();
    this.immediate.set(false);
    this.showModal.set(true);
  }

  onConfirm() {
    const immediate = this.isAdmin() && this.immediate();
    this.trashing.set(true);
    const m = this.movie();
    const s = this.show();
    const call = m
      ? this.trashService.trashMovie(m.radarrId, immediate)
      : this.trashService.trashShow(s!.sonarrId, immediate);

    call.subscribe({
      next: () => {
        if (m) this.library.removeMovie(m.radarrId);
        else if (s) this.library.removeShow(s.sonarrId);
        this.toast.show(messages.trash.itemTrashed(this.title()));
        this.showModal.set(false);
        this.trashing.set(false);
      },
      error: () => {
        this.toast.show(messages.trash.trashError, 'error');
        this.showModal.set(false);
        this.trashing.set(false);
      },
    });
  }

  onCancel() {
    this.showModal.set(false);
  }

  toggleImmediate(checked: boolean): void {
    this.immediate.set(checked);
  }
}
