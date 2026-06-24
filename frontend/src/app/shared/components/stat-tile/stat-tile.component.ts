import { Component, input } from '@angular/core';

@Component({
  selector: 'stat-tile',
  standalone: true,
  templateUrl: './stat-tile.component.html',
})
export class StatTileComponent {
  label = input<string>('');
  value = input<string>('–');
  accent = input<string | null>(null);
}
