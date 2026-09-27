import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TrashService } from '../core/api/trash.service';
import { AuthService } from '../core/auth/auth.service';

@Component({
  selector: 'mobile-tab-bar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './mobile-tab-bar.component.html',
})
export class MobileTabBarComponent {
  private trashService = inject(TrashService);
  readonly isAdmin = inject(AuthService).isAdmin;
  trashCount = computed(() => this.trashService.trashItems().length);
}
