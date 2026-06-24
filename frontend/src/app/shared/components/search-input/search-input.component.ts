import { Component, input, model } from '@angular/core';

@Component({
  selector: 'search-input',
  standalone: true,
  templateUrl: './search-input.component.html',
  styleUrl: './search-input.component.css',
})
export class SearchInputComponent {
  placeholder = input<string>('Rechercher…');
  value = model<string>('');

  onInput(event: Event) {
    this.value.set((event.target as HTMLInputElement).value);
  }
}
