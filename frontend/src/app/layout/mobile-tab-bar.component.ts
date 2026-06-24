import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TrashService } from '../core/api/trash.service';

@Component({
  selector: 'mobile-tab-bar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './mobile-tab-bar.component.html',
})
export class MobileTabBarComponent {
  private trashService = inject(TrashService);
  trashCount = computed(() => this.trashService.trashItems().length);
}
