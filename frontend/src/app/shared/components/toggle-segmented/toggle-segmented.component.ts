import { Component, input, model } from '@angular/core';

@Component({
  selector: 'toggle-segmented',
  standalone: true,
  templateUrl: './toggle-segmented.component.html',
  styleUrl: './toggle-segmented.component.css',
})
export class ToggleSegmentedComponent {
  options = input<string[]>([]);
  value = model<string>('');

  select(opt: string) {
    this.value.set(opt);
  }
}
