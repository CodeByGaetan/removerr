/**
 * French label for the Spring cron expressions used by the backend jobs
 * ("sec min hour day month weekday"). The time of a daily job is taken from its next run,
 * so it shows in the browser's timezone. Unknown patterns fall back to the raw expression.
 */
export function describeSchedule(cron: string, nextRunAt?: string): string {
  const [, , hour, day, month, weekday] = cron.split(' ');
  const everyDay = day === '*' && month === '*' && weekday === '*';
  if (everyDay && hour === '*') return 'Toutes les heures';
  if (everyDay && nextRunAt) {
    const time = new Date(nextRunAt).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
    return `Tous les jours à ${time}`;
  }
  return cron;
}
