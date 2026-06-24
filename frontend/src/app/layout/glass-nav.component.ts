import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { TrashService } from '../core/api/trash.service';

interface NavItem {
  label: string;
  path: string;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Bibliothèque', path: '/library' },
  { label: 'Corbeille', path: '/trash' },
  { label: 'Audit', path: '/audit' },
  { label: 'Paramètres', path: '/settings' },
];

@Component({
  selector: 'glass-nav',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './glass-nav.component.html',
})
export class GlassNavComponent {
  private auth = inject(AuthService);
  private trashService = inject(TrashService);
  readonly navItems = NAV_ITEMS;

  trashCount = computed(() => this.trashService.trashItems().length);

  userInitial() {
    const u = this.auth.me();
    return u ? u.username[0].toUpperCase() : 'A';
  }

  userName() {
    const u = this.auth.me();
    return u ? u.username : 'admin';
  }

  logout() {
    this.auth.logout().subscribe();
  }
}
