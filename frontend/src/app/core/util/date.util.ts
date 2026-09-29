export const MS_PER_DAY = 86_400_000;

/** Human-readable French relative date. Months are approximated at 30 days. */
export function relativeDate(iso: string): string {
  const days = Math.floor((Date.now() - new Date(iso).getTime()) / MS_PER_DAY);
  if (days < 1) return "aujourd'hui";
  if (days < 30) return `il y a ${days} jour${days > 1 ? 's' : ''}`;
  const months = Math.floor(days / 30);
  if (months < 12) return `il y a ${months} mois`;
  const years = Math.floor(months / 12);
  return `il y a ${years} an${years > 1 ? 's' : ''}`;
}

/** "aujourd'hui à 15:37", "hier à 03:00", "demain à 03:00", else "12/10 à 03:00" (local time). */
export function dayAndTime(iso: string, now: Date = new Date()): string {
  const date = new Date(iso);
  const time = date.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  const dayOffset = Math.round((startOfDay(date) - startOfDay(now)) / MS_PER_DAY);
  const day =
    dayOffset === 0 ? "aujourd'hui"
    : dayOffset === -1 ? 'hier'
    : dayOffset === 1 ? 'demain'
    : `le ${date.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })}`;
  return `${day} à ${time}`;
}

function startOfDay(date: Date): number {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime();
}
