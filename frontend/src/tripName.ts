const destinationNames: Record<string, string> = {
  'destination-sfo': 'San Francisco',
  'destination-muc': 'Munich',
  'destination-mex': 'Mexico City',
};

export function tripBaseName(destinationKey: string, startDate: string, endDate: string): string {
  if (!startDate || !endDate) return '';
  const city = destinationNames[destinationKey];
  return city ? `${city} - ${startDate} to ${endDate}` : '';
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
