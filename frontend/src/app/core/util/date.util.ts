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
