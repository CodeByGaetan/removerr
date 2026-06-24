import { MS_PER_DAY, relativeDate } from './date.util';

/** ISO string for a date `days` in the past. */
function daysAgo(days: number): string {
  return new Date(Date.now() - days * MS_PER_DAY).toISOString();
}

describe('relativeDate', () => {
  it('returns "aujourd\'hui" for today', () => {
    expect(relativeDate(daysAgo(0))).toBe("aujourd'hui");
  });

  it('uses the singular for a single day', () => {
    expect(relativeDate(daysAgo(1))).toBe('il y a 1 jour');
  });

  it('uses the plural for several days', () => {
    expect(relativeDate(daysAgo(5))).toBe('il y a 5 jours');
  });

  it('switches to months past ~30 days', () => {
    expect(relativeDate(daysAgo(65))).toBe('il y a 2 mois');
  });

  it('uses the singular for a single year', () => {
    expect(relativeDate(daysAgo(370))).toBe('il y a 1 an');
  });

  it('uses the plural for several years', () => {
    expect(relativeDate(daysAgo(1100))).toBe('il y a 3 ans');
  });
});
