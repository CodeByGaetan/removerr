import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { TrashService } from '../core/api/trash.service';

interface NavItem {
  label: string;
  path: string;
  adminOnly?: boolean;
}

const NAV_ITEMS: NavItem[] = [
  { label: 'Bibliothèque', path: '/library' },
  { label: 'Corbeille', path: '/trash' },
  { label: 'Audit', path: '/audit', adminOnly: true },
  { label: 'Utilisateurs', path: '/users', adminOnly: true },
  { label: 'Paramètres', path: '/settings', adminOnly: true },
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
  readonly navItems = computed(() =>
    NAV_ITEMS.filter((item) => !item.adminOnly || this.auth.isAdmin()),
  );

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
