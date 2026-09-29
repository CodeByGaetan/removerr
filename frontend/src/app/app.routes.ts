import { Routes } from '@angular/router';
import { adminGuard } from './core/auth/admin.guard';
import { authGuard } from './core/auth/auth.guard';
import { unsavedChangesGuard } from './core/guards/unsaved-changes.guard';

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
        canActivate: [adminGuard],
      },
      {
        path: 'settings',
        loadComponent: () =>
          import('./features/settings/settings-layout.component').then(
            (m) => m.SettingsLayoutComponent
          ),
        canActivate: [adminGuard],
        children: [
          { path: '', redirectTo: 'services', pathMatch: 'full' },
          {
            path: 'services',
            loadComponent: () =>
              import('./features/settings/services/services-settings.component').then(
                (m) => m.ServicesSettingsComponent
              ),
            canDeactivate: [unsavedChangesGuard],
          },
          {
            path: 'users',
            loadComponent: () =>
              import('./features/settings/users/users-settings.component').then(
                (m) => m.UsersSettingsComponent
              ),
          },
          {
            path: 'jobs',
            loadComponent: () =>
              import('./features/settings/jobs/jobs-settings.component').then(
                (m) => m.JobsSettingsComponent
              ),
          },
        ],
      },
    ],
  },
];
