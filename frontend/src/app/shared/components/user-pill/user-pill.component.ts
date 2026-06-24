import { Component, input, computed } from '@angular/core';
import { PlexUser } from '../../../core/api/users.service';
import { UserAvatarComponent } from '../user-avatar/user-avatar.component';
import { userColor } from '../../user-color';

@Component({
  selector: 'user-pill',
  standalone: true,
  imports: [UserAvatarComponent],
  templateUrl: './user-pill.component.html',
})
export class UserPillComponent {
  user = input.required<PlexUser>();
  watched = input<boolean>(false);

  userHue = computed(() => userColor(this.user().name));
}
