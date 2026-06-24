import { Component, OnDestroy, computed, effect, inject, input, output, signal } from '@angular/core';
import { LibraryService, MediaCard, SeasonCard, ShowCard } from '../../core/api/library.service';
import { TrashService } from '../../core/api/trash.service';
import { UsersService } from '../../core/api/users.service';
import { AuthService } from '../../core/auth/auth.service';
import { LayoutService } from '../../core/layout.service';
import { FileSizePipe } from '../../core/pipes/file-size.pipe';
import { ToastService } from '../../core/services/toast.service';
import { messages } from '../../core/messages';
import { relativeDate } from '../../core/util/date.util';
import { ConfirmModalComponent } from '../../shared/components/confirm-modal/confirm-modal.component';
import { RequesterChipComponent } from '../../shared/components/requester-chip/requester-chip.component';
import { SeasonRowComponent } from '../../shared/components/season-row/season-row.component';
import { StatTileComponent } from '../../shared/components/stat-tile/stat-tile.component';
import { TrashImmediateCheckboxComponent } from '../../shared/components/trash-immediate-checkbox/trash-immediate-checkbox.component';
import { UserPillComponent } from '../../shared/components/user-pill/user-pill.component';

type ModalAction = { type: 'media' } | { type: 'season'; season: SeasonCard };

@Component({
  selector: 'app-library-detail',
  standalone: true,
  imports: [
    FileSizePipe,
    StatTileComponent,
    UserPillComponent,
    SeasonRowComponent,
    RequesterChipComponent,
    ConfirmModalComponent,
    TrashImmediateCheckboxComponent,
  ],
  templateUrl: './library-detail.component.html',
})
export class LibraryDetailComponent implements OnDestroy {
  private library = inject(LibraryService);
  private trashService = inject(TrashService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  private usersService = inject(UsersService);
  private layout = inject(LayoutService);

  id = input.required<string>();
  close = output<void>();

  readonly accent = 'var(--color-accent)';

  modalAction = signal<ModalAction | null>(null);
  trashing = signal(false);
  immediate = signal(false);

  isAdmin = computed(() => this.auth.me()?.admin ?? false);

  isMovie = computed(() => this.id().startsWith('movie-'));
  isShow = computed(() => this.id().startsWith('show-'));

  media = computed((): MediaCard | ShowCard | null => {
    const id = this.id();
    if (id.startsWith('movie-')) {
      const radarrId = parseInt(id.substring(6), 10);
      return this.library.movies().find((m) => m.radarrId === radarrId) ?? null;
    }
    if (id.startsWith('show-')) {
      const sonarrId = parseInt(id.substring(5), 10);
      return this.library.shows().find((s) => s.sonarrId === sonarrId) ?? null;
    }
    return null;
  });

  movie = computed(() => (this.isMovie() ? (this.media() as MediaCard | null) : null));
  show = computed(() => (this.isShow() ? (this.media() as ShowCard | null) : null));

  title = computed(() => this.media()?.title ?? '');
  year = computed(() => this.media()?.year ?? 0);
  posterUrl = computed(() => this.media()?.posterUrl ?? null);
  sizeOnDisk = computed(() => this.media()?.sizeOnDisk ?? 0);
  seerr = computed(() => this.media()?.seerr ?? null);

  countedUsers = computed(() => this.usersService.countedUsers());
  totalCounted = computed(
    () => this.countedUsers().length || this.auth.me()?.countedUsersTotal || 0,
  );

  plexUniqueViewers = computed(() => this.media()?.plexUniqueViewers ?? null);

  viewersTileValue = computed(() => {
    const viewers = this.plexUniqueViewers();
    const total = this.totalCounted();
    if (viewers === null || total === 0) return '–';
    return `${viewers}/${total}`;
  });

  viewersAccent = computed(() => {
    const viewers = this.plexUniqueViewers();
    const total = this.totalCounted();
    return viewers !== null && total > 0 && viewers === total ? this.accent : null;
  });

  addedAtLabel = computed(() => {
    const at = this.media()?.addedAt;
    if (!at) return '';
    return 'ajouté ' + relativeDate(at);
  });

  viewerUserPills = computed(() => {
    const viewers = this.plexUniqueViewers() ?? 0;
    return this.countedUsers().map((u, i) => ({ user: u, watched: i < viewers }));
  });

  seasons = computed(() => this.show()?.seasons ?? []);

  loading = computed(() =>
    this.isMovie() ? this.library.moviesLoading() : this.library.showsLoading(),
  );

  modalTitle = computed(() => {
    const a = this.modalAction();
    if (!a) return '';
    if (a.type === 'media') return 'Mettre à la corbeille';
    return `Corbeille — Saison ${a.season.seasonNumber}`;
  });

  modalMessage = computed(() => {
    const a = this.modalAction();
    if (!a) return '';
    if (a.type === 'media') {
      return `Envoyer « ${this.title()} » à la corbeille ? L'item sera supprimé définitivement lors de la prochaine purge.`;
    }
    return `Envoyer la saison ${a.season.seasonNumber} de « ${this.title()} » à la corbeille ?`;
  });

  constructor() {
    if (this.usersService.allUsers().length === 0) {
      this.usersService.loadUsers();
    }
    // Lazy-load the matching list when navigating directly to a detail URL.
    effect(() => {
      const id = this.id();
      if (id.startsWith('movie-') && this.library.movies().length === 0) {
        this.library.loadMovies();
      } else if (id.startsWith('show-') && this.library.shows().length === 0) {
        this.library.loadShows();
      }
    });
  }

  ngOnDestroy() {
    this.layout.resetAccent();
  }

  onTrashMedia() {
    this.immediate.set(false);
    this.modalAction.set({ type: 'media' });
  }

  onTrashSeason(season: SeasonCard) {
    this.immediate.set(false);
    this.modalAction.set({ type: 'season', season });
  }

  onConfirmTrash() {
    const action = this.modalAction();
    if (!action) return;

    const immediate = this.isAdmin() && this.immediate();
    this.trashing.set(true);

    if (action.type === 'media') {
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
          this.trashing.set(false);
          this.modalAction.set(null);
          this.close.emit();
        },
        error: () => {
          this.toast.show(messages.trash.trashError, 'error');
          this.trashing.set(false);
          this.modalAction.set(null);
        },
      });
    } else {
      const s = this.show()!;
      this.trashService.trashSeason(s.sonarrId, action.season.seasonNumber, immediate).subscribe({
        next: () => {
          this.library.removeSeason(s.sonarrId, action.season.seasonNumber);
          this.toast.show(
            messages.trash.seasonTrashed(action.season.seasonNumber, this.title()),
          );
          this.trashing.set(false);
          this.modalAction.set(null);
        },
        error: () => {
          this.toast.show(messages.trash.trashError, 'error');
          this.trashing.set(false);
          this.modalAction.set(null);
        },
      });
    }
  }

  onCancelTrash() {
    this.modalAction.set(null);
  }

  toggleImmediate(checked: boolean): void {
    this.immediate.set(checked);
  }

  onClose() {
    this.close.emit();
  }
}
