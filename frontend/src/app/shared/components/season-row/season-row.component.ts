import { Component, input, computed, output } from '@angular/core';
import { SeasonCard } from '../../../core/api/library.service';
import { PlexUser } from '../../../core/api/users.service';
import { FileSizePipe } from '../../../core/pipes/file-size.pipe';
import { UserDotComponent } from '../user-dot/user-dot.component';

@Component({
  selector: 'season-row',
  standalone: true,
  imports: [FileSizePipe, UserDotComponent],
  templateUrl: './season-row.component.html',
})
export class SeasonRowComponent {
  season = input.required<SeasonCard>();
  countedUsers = input<PlexUser[]>([]);
  accent = input<string>('var(--color-accent)');
  trash = output<SeasonCard>();

  watchedPct = computed(() => {
    const s = this.season();
    return s.totalEpisodes > 0 ? s.episodeFileCount / s.totalEpisodes : 0;
  });

  fullyDownloaded = computed(() => {
    const s = this.season();
    return s.episodeFileCount === s.totalEpisodes && s.totalEpisodes > 0;
  });

  barColor = computed(() => this.fullyDownloaded() ? this.accent() : 'rgba(255,255,255,0.55)');

  viewerPills = computed(() => {
    const viewers = this.season().plexUniqueViewers ?? 0;
    return this.countedUsers().map((u, i) => ({ user: u, watched: i < viewers }));
  });

  onTrash(e: MouseEvent) {
    e.stopPropagation();
    this.trash.emit(this.season());
  }
}
