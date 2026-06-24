import { Component, OnInit, OnDestroy, signal } from '@angular/core';

@Component({
  selector: 'install-prompt',
  standalone: true,
  templateUrl: './install-prompt.component.html',
})
export class InstallPromptComponent implements OnInit, OnDestroy {
  visible = signal(false);

  private deferredPrompt: any = null;
  private onBeforeInstall = (e: Event) => {
    e.preventDefault();
    this.deferredPrompt = e;
    this.visible.set(true);
  };
  private onAppInstalled = () => {
    this.visible.set(false);
    this.deferredPrompt = null;
  };

  ngOnInit() {
    window.addEventListener('beforeinstallprompt', this.onBeforeInstall);
    window.addEventListener('appinstalled', this.onAppInstalled);
  }

  ngOnDestroy() {
    window.removeEventListener('beforeinstallprompt', this.onBeforeInstall);
    window.removeEventListener('appinstalled', this.onAppInstalled);
  }

  async install() {
    if (!this.deferredPrompt) return;
    this.deferredPrompt.prompt();
    const { outcome } = await this.deferredPrompt.userChoice;
    if (outcome === 'accepted') this.visible.set(false);
    this.deferredPrompt = null;
  }

  dismiss() {
    this.visible.set(false);
  }
}
