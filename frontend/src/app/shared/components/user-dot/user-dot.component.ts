import { Component, computed, input } from '@angular/core';
import { PlexUser } from '../../../core/api/users.service';
import { userGradient } from '../../user-color';

@Component({
  selector: 'user-dot',
  standalone: true,
  templateUrl: './user-dot.component.html',
})
export class UserDotComponent {
  user = input.required<PlexUser>();
  watched = input<boolean>(false);

  initial = computed(() => (this.user().name || '?')[0].toUpperCase());
  background = computed(() => this.watched() ? userGradient(this.user().name) : 'transparent');
}
