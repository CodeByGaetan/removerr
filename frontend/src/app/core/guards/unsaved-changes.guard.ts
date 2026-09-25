import { CanDeactivateFn } from '@angular/router';

export interface LeaveConfirmable {
  confirmLeave(): boolean | Promise<boolean>;
}

export const unsavedChangesGuard: CanDeactivateFn<LeaveConfirmable> = (component) =>
  component.confirmLeave();
