import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class LayoutService {
  readonly accent = signal('var(--color-accent)');

  setAccent(color: string) { this.accent.set(color); }
  resetAccent() { this.accent.set('var(--color-accent)'); }
}
