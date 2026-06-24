/**
 * Single source of truth for user-facing runtime strings (toasts, service
 * errors) built in TypeScript. Static template copy stays in the templates.
 * The app is French-only; this is centralization, not i18n. If true
 * multi-language support is ever needed, migrate these to @angular/localize
 * and mark template text with i18n.
 */
export const messages = {
  trash: {
    itemTrashed: (title: string) => `« ${title} » mis à la corbeille`,
    seasonTrashed: (season: number, title: string) =>
      `Saison ${season} de « ${title} » mise à la corbeille`,
    trashError: 'Erreur lors de la mise à la corbeille',
    restored: (title: string) => `« ${title} » restauré`,
    restoreError: 'Erreur lors de la restauration',
    purged: (purged: number, failed: number) =>
      `${purged} item(s) purgé(s)${failed > 0 ? `, ${failed} échec(s)` : ''}`,
    purgeError: 'Erreur lors de la purge',
  },
  settings: {
    saved: 'Paramètres sauvegardés',
    saveError: 'Erreur lors de la sauvegarde',
    testError: 'Erreur lors du test',
  },
  library: {
    moviesLoadError: 'Impossible de charger les films.',
    showsLoadError: 'Impossible de charger les séries.',
  },
} as const;
