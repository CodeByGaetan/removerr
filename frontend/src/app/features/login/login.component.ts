import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { GlassBackdropComponent } from '../../layout/glass-backdrop.component';
import { environment } from '../../../environments/environment';

type LoginState = 'idle' | 'pending' | 'expired';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [GlassBackdropComponent],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css',
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  // Tags ship without the `v` prefix; add it for display.
  readonly version = `v${environment.version}`;

  state = signal<LoginState>('idle');
  loading = signal<boolean>(false);
  timeLeft = signal<number>(300);

  timeDisplay = computed(() => {
    const t = this.timeLeft();
    const m = Math.floor(t / 60).toString().padStart(2, '0');
    const s = (t % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  });

  private authUrl = '';
  private plexWindow: Window | null = null;
  private pollSub?: Subscription;
  private timerSub?: Subscription;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.pollSub?.unsubscribe();
      this.timerSub?.unsubscribe();
    });
  }

  startLogin() {
    this.loading.set(true);
    this.auth.startPin().subscribe({
      next: ({ pinId, authUrl }) => {
        this.authUrl = authUrl;
        this.timeLeft.set(300);
        this.state.set('pending');
        this.loading.set(false);

        this.plexWindow = window.open(authUrl, '_blank');
        this.startTimer();
        this.startPolling(pinId);
      },
      error: () => {
        this.loading.set(false);
      },
    });
  }

  openPlex() {
    window.open(this.authUrl, '_blank');
  }

  retry() {
    this.pollSub?.unsubscribe();
    this.timerSub?.unsubscribe();
    this.state.set('idle');
  }

  private startTimer() {
    this.timerSub?.unsubscribe();
    this.timerSub = interval(1000).subscribe(() => {
      this.timeLeft.update((t) => Math.max(0, t - 1));
    });
  }

  private startPolling(pinId: number) {
    this.pollSub?.unsubscribe();
    this.pollSub = this.auth.pollPin(pinId).subscribe({
      next: (res) => {
        if (res.status === 'SUCCESS') {
          this.plexWindow?.close();
          this.router.navigate(['/library']);
        } else if (res.status === 'EXPIRED') {
          this.state.set('expired');
          this.timerSub?.unsubscribe();
        }
      },
      error: () => {
        this.state.set('expired');
        this.timerSub?.unsubscribe();
      },
    });
  }
}
