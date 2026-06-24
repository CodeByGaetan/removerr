import { Component, computed, input } from '@angular/core';

const R = 13;
const C = 2 * Math.PI * R;

@Component({
  selector: 'viewers-ring',
  standalone: true,
  templateUrl: './viewers-ring.component.html',
})
export class ViewersRingComponent {
  viewers = input<number>(0);
  total = input<number>(0);
  accent = input<string>('var(--color-accent)');

  readonly r = R;
  readonly circumference = C;

  fullyWatched = computed(() => this.viewers() === this.total() && this.total() > 0);
  noViewers = computed(() => this.viewers() === 0);
  arc = computed(() => (this.total() > 0 ? this.viewers() / this.total() : 0) * C);
  ringColor = computed(() => (this.fullyWatched() ? this.accent() : 'rgba(255,255,255,0.9)'));
}
