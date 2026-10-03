import { describeSchedule } from './cron.util';

describe('describeSchedule', () => {
  it('labels an hourly job', () => {
    expect(describeSchedule('0 0 * * * *')).toBe('Toutes les heures');
  });

  it('labels a daily job with its local time', () => {
    const nextRunAt = new Date(2026, 9, 13, 3, 0).toISOString();
    expect(describeSchedule('0 0 3 * * *', nextRunAt)).toBe('Tous les jours à 03:00');
  });

  it('falls back to the raw expression', () => {
    expect(describeSchedule('0 0 3 * * MON')).toBe('0 0 3 * * MON');
  });
});
