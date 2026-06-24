import { Component, input } from '@angular/core';
import { SeerrInfo } from '../../../core/api/library.service';
import { UserAvatarComponent } from '../user-avatar/user-avatar.component';

@Component({
  selector: 'requester-chip',
  standalone: true,
  imports: [UserAvatarComponent],
  templateUrl: './requester-chip.component.html',
})
export class RequesterChipComponent {
  seerr = input<SeerrInfo | null>(null);
}
