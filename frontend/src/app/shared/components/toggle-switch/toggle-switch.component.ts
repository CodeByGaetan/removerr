import { Component, input, output } from '@angular/core';

@Component({
  selector: 'toggle-switch',
  standalone: true,
  templateUrl: './toggle-switch.component.html',
})
export class ToggleSwitchComponent {
  checked = input.required<boolean>();
  label = input.required<string>();
  disabled = input(false);
  toggled = output<void>();
}
