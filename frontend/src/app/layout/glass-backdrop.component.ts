import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'glass-backdrop',
  standalone: true,
  templateUrl: './glass-backdrop.component.html',
})
export class GlassBackdropComponent {
  accent = input<string>('var(--color-accent)');
  blob1Bg = computed(() => `radial-gradient(circle, ${this.accent()}, transparent 60%)`);
}
