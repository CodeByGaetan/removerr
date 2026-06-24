import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'library', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () =>
      import('./layout/app-layout.component').then((m) => m.AppLayoutComponent),
    canActivate: [authGuard],
    children: [
      {
        path: 'library',
        loadComponent: () =>
          import('./features/library/library.component').then(
            (m) => m.LibraryComponent
          ),
      },
      {
        path: 'trash',
        loadComponent: () =>
          import('./features/trash/trash.component').then(
            (m) => m.TrashComponent
          ),
      },
      {
        path: 'audit',
        loadComponent: () =>
          import('./features/audit/audit.component').then(
            (m) => m.AuditComponent
          ),
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings.component').then(
            (m) => m.SettingsComponent
          ),
      },
    ],
  },
];
