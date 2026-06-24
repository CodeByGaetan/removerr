import { Component, computed, input } from '@angular/core';

@Component({
  selector: 'action-chip',
  standalone: true,
  templateUrl: './action-chip.component.html',
})
export class ActionChipComponent {
  action = input.required<string>();

  // Colors aligned with design_handoff_library §7.5
  color = computed(() => {
    switch (this.action()) {
      case 'TRASH':           return 'var(--color-accent)';
      case 'RESTORE':         return 'var(--color-state-ok)';
      case 'PURGE_MANUAL':    return 'var(--color-state-danger)';
      case 'PURGE_AUTO':
      case 'PURGE_SCHEDULED': return 'var(--color-state-warn)';
      case 'PURGE':           return 'var(--color-state-warn)';
      case 'LOGIN':           return 'var(--color-state-info)';
      default:                return 'rgba(255,255,255,0.6)';
    }
  });

  // Spec : bg = color @ 0.10, border = color @ 0.33, glow @ 0.67
  bg = computed(() => `color-mix(in srgb, ${this.color()} 10%, transparent)`);
  border = computed(() => `color-mix(in srgb, ${this.color()} 33%, transparent)`);
  glow = computed(() => `0 0 6px color-mix(in srgb, ${this.color()} 67%, transparent)`);

  // Spec : labels mono uppercase originaux (TRASH / RESTORE / PURGE · MANUAL / PURGE · AUTO / LOGIN)
  label = computed(() => {
    switch (this.action()) {
      case 'PURGE_MANUAL':    return 'PURGE · MANUAL';
      case 'PURGE_AUTO':
      case 'PURGE_SCHEDULED': return 'PURGE · AUTO';
      default:                return this.action();
    }
  });
}
