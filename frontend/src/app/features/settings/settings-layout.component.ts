import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { APP_VERSION } from '../../shared/app-version';

interface SettingsTab {
  label: string;
  path: string;
}

const TABS: SettingsTab[] = [
  { label: 'Services', path: 'services' },
  { label: 'Utilisateurs', path: 'users' },
  { label: 'Tâches', path: 'jobs' },
];

@Component({
  selector: 'app-settings-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './settings-layout.component.html',
})
export class SettingsLayoutComponent {
  readonly tabs = TABS;
  readonly version = APP_VERSION;
}
