import { Component, ElementRef, HostListener, computed, inject, input, model, output, signal } from '@angular/core';

/** A selectable option: a stable `value` used by the host's logic and a display `label`. */
export interface PillOption {
  value: string;
  label: string;
}

@Component({
  selector: 'glass-pill',
  standalone: true,
  templateUrl: './glass-pill.component.html',
})
export class GlassPillComponent {
  label = input<string>('');
  options = input<PillOption[]>([]);
  // Holds the active option's `value` (not its label).
  value = model<string>('');
  // Optional icon (e.g. arrow) appended to the active option in the trigger and the dropdown.
  // Receives the option's value, returns the icon string or null.
  iconFor = input<((value: string) => string | null) | null>(null);
  // Fires on every option click, even when the user reselects the current value
  // (model.valueChange does not emit on same value). Emits the option's value.
  optionClick = output<string>();

  isOpen = signal(false);
  private el = inject(ElementRef);

  activeLabel = computed(
    () => this.options().find((o) => o.value === this.value())?.label ?? '',
  );

  toggle() {
    this.isOpen.update((v) => !v);
  }

  select(opt: PillOption) {
    this.value.set(opt.value);
    this.optionClick.emit(opt.value);
    this.isOpen.set(false);
  }

  icon(value: string): string | null {
    const fn = this.iconFor();
    return fn ? fn(value) : null;
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (!this.el.nativeElement.contains(event.target)) {
      this.isOpen.set(false);
    }
  }
}
