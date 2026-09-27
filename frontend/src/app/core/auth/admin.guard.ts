import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

// Child guards may run before the parent authGuard has resolved, so me() can still be empty.
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const me = auth.me();

  return (me ? of(me) : auth.fetchMe()).pipe(
    map((user) => user.admin || router.createUrlTree(['/library'])),
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};
