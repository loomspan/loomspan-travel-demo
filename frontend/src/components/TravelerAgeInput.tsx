import type {ComponentProps} from 'react';

type Props = Omit<ComponentProps<'input'>, 'type' | 'list' | 'value' | 'onChange'> & {
  value: string;
  onChange: (value: string) => void;
};

const ages = Array.from({length: 96}, (_, age) => String(age));

export function TravelerAgeInput({id, value, onChange, ...props}: Props) {
  const visibleChoices = ages.filter(age => age.startsWith(value.trim()));
  const listId = `${id}-options`;

  return <>
    <input {...props} id={id} type="text" inputMode="numeric" autoComplete="off" list={listId}
      value={value} onChange={event => onChange(event.target.value)} />
    <datalist id={listId}>
      {visibleChoices.map(age => <option key={age} value={age} label={age === '0' ? 'Under 1' : undefined} />)}
    </datalist>
  </>;
}
