import { Component, input, computed } from '@angular/core';
import { userGradient } from '../../user-color';

@Component({
  selector: 'user-avatar',
  standalone: true,
  templateUrl: './user-avatar.component.html',
})
export class UserAvatarComponent {
  name = input<string>('?');
  size = input<number>(28);

  initial    = computed(() => (this.name() || '?')[0].toUpperCase());
  background = computed(() => userGradient(this.name()));
  fontSize   = computed(() => Math.max(7, Math.round(this.size() * 0.4)) + 'px');
}
