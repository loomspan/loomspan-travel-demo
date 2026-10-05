// Keep the search API's sort order within each group, without changing its results.
export function selectedOptionsFirst<T>(options: T[], isSelected: (option: T) => boolean): T[] {
  const selected: T[] = [];
  const alternatives: T[] = [];
  for (const option of options) {
    (isSelected(option) ? selected : alternatives).push(option);
  }
  return [...selected, ...alternatives];
}
