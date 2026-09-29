import { MS_PER_DAY, dayAndTime, relativeDate } from './date.util';

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

describe('dayAndTime', () => {
  const now = new Date(2026, 9, 12, 15, 0);
  const at = (day: number, hour: number, minute = 0) => new Date(2026, 9, day, hour, minute).toISOString();

  it('names today, yesterday and tomorrow', () => {
    expect(dayAndTime(at(12, 15, 37), now)).toBe("aujourd'hui à 15:37");
    expect(dayAndTime(at(11, 3), now)).toBe('hier à 03:00');
    expect(dayAndTime(at(13, 3), now)).toBe('demain à 03:00');
  });

  it('falls back to the date further away', () => {
    expect(dayAndTime(at(9, 3), now)).toBe('le 09/10 à 03:00');
  });
});
