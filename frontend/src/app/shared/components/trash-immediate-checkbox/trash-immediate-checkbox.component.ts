import { Component, input, output } from '@angular/core';

@Component({
  selector: 'trash-immediate-checkbox',
  standalone: true,
  templateUrl: './trash-immediate-checkbox.component.html',
})
export class TrashImmediateCheckboxComponent {
  isAdmin = input.required<boolean>();
  immediate = input.required<boolean>();
  disabled = input(false);
  change = output<boolean>();

  onChange(event: Event): void {
    event.stopPropagation();
    const checked = (event.target as HTMLInputElement).checked;
    this.change.emit(checked);
  }
}
