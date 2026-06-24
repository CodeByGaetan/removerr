import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { GlassBackdropComponent } from './glass-backdrop.component';
import { GlassNavComponent } from './glass-nav.component';
import { MobileTabBarComponent } from './mobile-tab-bar.component';
import { LayoutService } from '../core/layout.service';
import { ToastComponent } from '../shared/components/toast/toast.component';
import { InstallPromptComponent } from '../shared/components/install-prompt/install-prompt.component';
import { TrashService } from '../core/api/trash.service';

@Component({
  selector: 'app-layout',
  standalone: true,
  imports: [RouterOutlet, GlassBackdropComponent, GlassNavComponent, MobileTabBarComponent, ToastComponent, InstallPromptComponent],
  templateUrl: './app-layout.component.html',
})
export class AppLayoutComponent {
  layout = inject(LayoutService);

  constructor() {
    inject(TrashService).loadTrash();
  }
}
