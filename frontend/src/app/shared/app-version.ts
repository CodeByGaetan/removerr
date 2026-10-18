import { environment } from '../../environments/environment';

// Tags ship without the `v` prefix; `dev` means a build outside the release image.
export const APP_VERSION = environment.version === 'dev' ? 'v-dev' : `v${environment.version}`;
