import { Location } from '@angular/common';
import { Component, afterNextRender, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { LibraryService, MediaCard, ShowCard } from '../../core/api/library.service';
import { FileSizePipe } from '../../core/pipes/file-size.pipe';
import { GlassPillComponent, PillOption } from '../../shared/components/glass-pill/glass-pill.component';
import { LibraryCardComponent } from '../../shared/components/library-card/library-card.component';
import { SearchInputComponent } from '../../shared/components/search-input/search-input.component';
import { ToggleSegmentedComponent } from '../../shared/components/toggle-segmented/toggle-segmented.component';
import { LibraryDetailComponent } from './library-detail.component';

type Tab = 'movies' | 'shows';
type SortDir = 'asc' | 'desc';
type SortKey = 'title' | 'year' | 'size' | 'addedAt';
type WatchedFilter = 'all' | 'watched' | 'unwatched';
type AvailabilityFilter = 'all' | 'available' | 'unavailable';

// Natural direction when a sort criterion is first selected.
const DEFAULT_SORT_DIR: Record<SortKey, SortDir> = {
  title: 'asc',
  year: 'desc',
  size: 'desc',
  addedAt: 'asc',
};
const DEFAULT_SORT: SortKey = 'addedAt';

@Component({
  selector: 'app-library',
  standalone: true,
  imports: [
    LibraryCardComponent,
    LibraryDetailComponent,
    ToggleSegmentedComponent,
    GlassPillComponent,
    SearchInputComponent,
    FileSizePipe,
  ],
  templateUrl: './library.component.html',
  // Take the layout's content area out of flow so neither the list nor the
  // drawer fight the outer scroll container. The two carousel pages inside
  // each have their own vertical scroll.
  host: { class: 'block absolute inset-0 overflow-hidden' },
})
export class LibraryComponent {
  library = inject(LibraryService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private location = inject(Location);

  // Drawer state — driven by ?detail=… so the component instance is reused on open/close
  // (Angular's default RouteReuseStrategy keeps the component when only query params change).
  private queryParamMap = toSignal(this.route.queryParamMap, {
    initialValue: this.route.snapshot.queryParamMap,
  });
  selectedId = computed(() => this.queryParamMap().get('detail') ?? null);
  // The id rendered in page 2. Sticks to the last non-null value so the detail
  // stays mounted with stable inputs during the slide-back animation; cleared
  // on transitionend once the carousel is back at translateX(0).
  displayedId = signal<string | null>(this.selectedId());
  // Disable the transform transition for the very first render so a deep link
  // (initial ?detail=…) lands at translateX(-50%) without animating from 0.
  mounted = signal(false);
  // True when the drawer was opened in-app (so we can pop history instead of pushing).
  private openedFromList = signal(false);

  // Tabs
  currentTab = signal<Tab>('movies');
  readonly tabs = ['Films', 'Séries'];
  isMoviesTab = computed(() => this.currentTab() === 'movies');

  // Filter signals — hold stable keys, decoupled from the displayed labels.
  searchQuery = signal('');
  sortBy = signal<SortKey>(DEFAULT_SORT);
  sortDir = signal<SortDir>(DEFAULT_SORT_DIR[DEFAULT_SORT]);
  watchedFilter = signal<WatchedFilter>('all');
  availabilityFilter = signal<AvailabilityFilter>('all');

  sortIconFor = (value: string): string | null => {
    if (value !== this.sortBy()) return null;
    return this.sortDir() === 'asc' ? '↑' : '↓';
  };

  onSortClick(value: string) {
    const key = value as SortKey;
    if (key === this.sortBy()) {
      this.sortDir.update((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      this.sortBy.set(key);
      this.sortDir.set(DEFAULT_SORT_DIR[key]);
    }
  }

  setWatchedFilter(value: string) {
    this.watchedFilter.set(value as WatchedFilter);
  }

  setAvailabilityFilter(value: string) {
    this.availabilityFilter.set(value as AvailabilityFilter);
  }

  // Mobile: filter pills are hidden behind a "Filtres" button
  showFilters = signal(false);

  // Filter option lists — { value: logic key, label: UI text }.
  readonly sortOptions: PillOption[] = [
    { value: 'title', label: 'Titre' },
    { value: 'year', label: 'Année' },
    { value: 'size', label: 'Taille' },
    { value: 'addedAt', label: "Date d'ajout" },
  ];
  readonly watchedOptions: PillOption[] = [
    { value: 'all', label: 'Tous' },
    { value: 'watched', label: 'Vu' },
    { value: 'unwatched', label: 'Non vu' },
  ];
  readonly availabilityOptions: PillOption[] = [
    { value: 'all', label: 'Tous' },
    { value: 'available', label: 'Disponible' },
    { value: 'unavailable', label: 'Non disponible' },
  ];

  // Filtered lists (typed)
  filteredMovies = computed((): MediaCard[] => {
    let items = [...this.library.movies()];
    const q = this.searchQuery().trim().toLowerCase();
    if (q) items = items.filter((m) => m.title.toLowerCase().includes(q));
    items = this.applyAvailabilityFilter(items);
    const watched = this.watchedFilter();
    if (watched === 'watched') items = items.filter((m) => (m.plexViewCount ?? 0) > 0);
    else if (watched === 'unwatched') items = items.filter((m) => (m.plexViewCount ?? 0) === 0);
    return this.applySort(items);
  });

  filteredShows = computed((): ShowCard[] => {
    let items = [...this.library.shows()];
    const q = this.searchQuery().trim().toLowerCase();
    if (q) items = items.filter((s) => s.title.toLowerCase().includes(q));
    items = this.applyAvailabilityFilter(items);
    const watched = this.watchedFilter();
    if (watched === 'watched') items = items.filter((s) => (s.plexWatchedEpisodes ?? 0) > 0);
    else if (watched === 'unwatched') items = items.filter((s) => (s.plexWatchedEpisodes ?? 0) === 0);
    return this.applySort(items);
  });

  filteredCount = computed(() =>
    this.isMoviesTab() ? this.filteredMovies().length : this.filteredShows().length,
  );

  // Total (unfiltered) for header and denominator
  totalItems = computed(() => (this.isMoviesTab() ? this.library.movies() : this.library.shows()));
  totalSize = computed(() => this.totalItems().reduce((sum, m) => sum + m.sizeOnDisk, 0));

  // Loading / error
  activeLoading = computed(() =>
    this.isMoviesTab() ? this.library.moviesLoading() : this.library.showsLoading(),
  );
  activeError = computed(() =>
    this.isMoviesTab() ? this.library.moviesError() : this.library.showsError(),
  );

  hasActiveFilters = computed(
    () =>
      this.searchQuery().trim() !== '' ||
      this.sortBy() !== DEFAULT_SORT ||
      this.sortDir() !== DEFAULT_SORT_DIR[DEFAULT_SORT] ||
      this.watchedFilter() !== 'all' ||
      this.availabilityFilter() !== 'all',
  );

  readonly skeletons = Array(18);

  constructor() {
    this.library.loadMovies();
    if (this.currentTab() === 'shows') this.library.loadShows();
    // Deep link / refresh on /library?detail=show-XX: make sure shows are loaded too.
    const initialId = this.selectedId();
    if (initialId?.startsWith('show-')) this.library.loadShows();

    // Sync displayedId on open (set/switch). It's cleared on transitionend instead
    // of here, so the detail page stays mounted while the carousel slides back.
    effect(() => {
      const id = this.selectedId();
      if (id) this.displayedId.set(id);
    });

    afterNextRender(() => this.mounted.set(true));
  }

  onTabChange(label: string) {
    const tab: Tab = label === 'Séries' ? 'shows' : 'movies';
    this.currentTab.set(tab);
    if (tab === 'shows') this.library.loadShows();
  }

  resetFilters() {
    this.searchQuery.set('');
    this.sortBy.set(DEFAULT_SORT);
    this.sortDir.set(DEFAULT_SORT_DIR[DEFAULT_SORT]);
    this.watchedFilter.set('all');
    this.availabilityFilter.set('all');
  }

  retry() {
    this.isMoviesTab() ? this.library.loadMovies() : this.library.loadShows();
  }

  openDetail(id: string) {
    this.openedFromList.set(true);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { detail: id },
      queryParamsHandling: 'merge',
    });
  }

  closeDetail() {
    if (this.openedFromList()) {
      this.openedFromList.set(false);
      this.location.back();
    } else {
      this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { detail: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }
  }

  onCarouselTransitionEnd(event: TransitionEvent) {
    // Only react to the wrapper's own transform transition (ignore nested bubbles).
    if (event.target !== event.currentTarget || event.propertyName !== 'transform') return;
    // Back at the list — drop the detail content so it isn't kept warm forever.
    if (this.selectedId() === null) this.displayedId.set(null);
  }

  private applyAvailabilityFilter<T extends { sizeOnDisk: number }>(items: T[]): T[] {
    const a = this.availabilityFilter();
    if (a === 'available') return items.filter((m) => m.sizeOnDisk > 0);
    if (a === 'unavailable') return items.filter((m) => m.sizeOnDisk === 0);
    return items;
  }

  private applySort<
    T extends { title: string; year: number; sizeOnDisk: number; addedAt: string },
  >(items: T[]): T[] {
    const sort = this.sortBy();
    const sign = this.sortDir() === 'asc' ? 1 : -1;
    const sorted = [...items];
    if (sort === 'title') sorted.sort((a, b) => sign * a.title.localeCompare(b.title, 'fr'));
    else if (sort === 'year') sorted.sort((a, b) => sign * (a.year - b.year));
    else if (sort === 'size') sorted.sort((a, b) => sign * (a.sizeOnDisk - b.sizeOnDisk));
    else
      sorted.sort(
        (a, b) => sign * (new Date(a.addedAt).getTime() - new Date(b.addedAt).getTime()),
      );
    return sorted;
  }
}
