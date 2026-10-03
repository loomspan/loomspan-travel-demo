const destinationNames: Record<string, string> = {
  'destination-sfo': 'San Francisco',
  'destination-muc': 'Munich',
  'destination-mex': 'Mexico City',
};

export function tripBaseName(destinationKey: string, startDate: string, endDate: string): string {
  if (!startDate || !endDate) return '';
  const city = destinationNames[destinationKey];
  if (!city) return '';
  const start = new Date(`${startDate}T00:00:00Z`);
  const end = new Date(`${endDate}T00:00:00Z`);
  if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime())) return '';
  const month = (date: Date) => date.toLocaleDateString('en-US', {month: 'short', timeZone: 'UTC'});
  const first = `${month(start)} ${start.getUTCDate()}`;
  const last = `${month(end)} ${end.getUTCDate()}`;
  const sameYear = start.getUTCFullYear() === end.getUTCFullYear();
  const dates = !sameYear
    ? `${first}, ${start.getUTCFullYear()} – ${last}, ${end.getUTCFullYear()}`
    : start.getUTCMonth() !== end.getUTCMonth()
      ? `${first} – ${last}, ${end.getUTCFullYear()}`
      : startDate === endDate
        ? `${first}, ${start.getUTCFullYear()}`
        : `${first}–${end.getUTCDate()}, ${start.getUTCFullYear()}`;
  return `${city} — ${dates}`;
}

export function suggestedTripName(base: string, existingNames: string[] = []): string {
  if (!base) return '';
  const taken = new Set(existingNames.map(name => name.trim().toLocaleLowerCase()));
  if (!taken.has(base.toLocaleLowerCase())) return base;
  for (let index = 1; ; index++) {
    let value = index;
    let suffix = '';
    while (value > 0) {
      value--;
      suffix = String.fromCharCode(65 + value % 26) + suffix;
      value = Math.floor(value / 26);
    }
    const candidate = `${base} - ${suffix}`;
    if (!taken.has(candidate.toLocaleLowerCase())) return candidate;
  }
}
