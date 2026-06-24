import {
  Component,
  ElementRef,
  afterNextRender,
  input,
  output,
  viewChild,
} from '@angular/core';

@Component({
  selector: 'confirm-modal',
  standalone: true,
  templateUrl: './confirm-modal.component.html',
  styleUrl: './confirm-modal.component.css',
})
export class ConfirmModalComponent {
  title = input<string>('Confirmer');
  message = input<string>('');
  confirmLabel = input<string>('Confirmer');
  loading = input<boolean>(false);
  confirmed = output<void>();
  cancelled = output<void>();

  private dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  constructor() {
    afterNextRender(() => this.dialog().nativeElement.showModal());
  }

  onBackdropClick(event: MouseEvent) {
    if (event.target === this.dialog().nativeElement) this.cancelled.emit();
  }

  onCancel(event: Event) {
    event.preventDefault();
    this.cancelled.emit();
  }
}
