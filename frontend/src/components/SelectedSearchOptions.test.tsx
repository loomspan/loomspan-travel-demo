import {fireEvent, render, screen, within} from '@testing-library/react';
import {afterEach, describe, expect, it, vi} from 'vitest';
import {TripComparisonPage} from './TripComparisonPage';
import {tripsApi, type TripResponse} from '../api/tripsApi';
import {flight, stay, rental, flightOption, stayOption, rentalOption} from './planSearchFixtures';

const trip = {id: 'trip', version: 3, destinationKey: 'destination-sfo', startDate: '2027-03-10', endDate: '2027-03-14', travelerCount: 3, travelerAges: [30, 30, 10]} as TripResponse;
const props = {
  trip, draftId: 'plan', name: 'Primary plan',
  savedAirfare: flight, savedStay: stay,
  savedRental: {...rental, pickupAt: '2027-03-10T10:00:00-08:00', returnAt: '2027-03-14T10:00:00-07:00'},
  onSelectAirfare: vi.fn(), onSelectStay: vi.fn(), onSelectRental: vi.fn(),
  onRemoveAirfare: vi.fn(), onRemoveStay: vi.fn(), onRemoveRental: vi.fn(),
};
const selectedFlight = {...flightOption, combinationKey: 'saved', outbound: {...flightOption.outbound, carrier: 'Saved Air', flightInstanceId: 1}, returnFlight: {...flightOption.returnFlight, flightInstanceId: 2}};
const selectedStay = {...stayOption, propertyName: 'Saved hotel', accommodationUnitId: 1};
const selectedCar = {...rentalOption, vehicleClassName: 'Saved car', rentalUnitId: 1};
const searches = [
  {category: 'AIRFARE', label: 'flights', api: 'searchAirfare', names: ['New Air', 'Third Air'], options: [flightOption, {...flightOption, combinationKey: 'third', outbound: {...flightOption.outbound, carrier: 'Third Air'}}, selectedFlight]},
  {category: 'STAY', label: 'stays', api: 'searchStays', names: ['New hotel', 'Third hotel'], options: [stayOption, {...stayOption, propertyName: 'Third hotel', accommodationUnitId: 3}, selectedStay]},
  {category: 'RENTAL', label: 'cars', api: 'searchRentals', names: ['New car', 'Third car'], options: [rentalOption, {...rentalOption, vehicleClassName: 'Third car', rentalUnitId: 3}, selectedCar]},
] as const;

function resultButtons(label: string) {
  return within(screen.getByLabelText(label === 'flights' ? 'Flight search options' : label === 'stays' ? 'Stay search options' : 'Car search options')).getAllByRole('article').map(card => within(card).getByRole('button'));
}

afterEach(() => vi.restoreAllMocks());

describe('selected search results first', () => {
  it.each(searches)('pins saved $label while preserving alternative order after sort changes', async ({category, label, api, options, names}) => {
    const search = vi.spyOn(tripsApi, api).mockResolvedValueOnce({options} as never)
      .mockResolvedValueOnce({options: [options[1], options[2], options[0]]} as never);
    render(<TripComparisonPage {...props} initialSearch={category} />);
    await screen.findByRole('button', {name: 'Selected'});
    const before = resultButtons(label);
    expect(before[0]).toHaveTextContent('Selected');
    expect(before[0]).toBeDisabled();
    expect(before[1].closest('article')).toHaveTextContent(names[0]);
    expect(before[2].closest('article')).toHaveTextContent(names[1]);

    fireEvent.change(screen.getByLabelText('Sort by'), {target: {value: 'LOWEST_PRICE'}});
    await screen.findByRole('button', {name: 'Selected'});
    const after = resultButtons(label);
    expect(after[0]).toHaveTextContent('Selected');
    expect(after[1].closest('article')).toHaveTextContent(names[1]);
    expect(after[2].closest('article')).toHaveTextContent(names[0]);
    expect(search).toHaveBeenLastCalledWith('trip', 'plan', expect.objectContaining({sort: 'LOWEST_PRICE'}));
  });

  it.each(searches)('preserves $label order when the saved selection is absent from results', async ({category, label, api, options, names}) => {
    vi.spyOn(tripsApi, api).mockResolvedValue({options: options.slice(0, 2)} as never);
    render(<TripComparisonPage {...props} initialSearch={category} />);
    await screen.findAllByRole('button', {name: /^Replace /});
    expect(resultButtons(label)).toHaveLength(2);
    resultButtons(label).forEach((button, index) => expect(button.closest('article')).toHaveTextContent(names[index]));
    expect(screen.queryByRole('button', {name: 'Selected'})).not.toBeInTheDocument();
  });

  it('reorders guest choices immediately when selection props change, and restores API order on removal', async () => {
    vi.spyOn(tripsApi, 'searchPublicAirfare').mockResolvedValue({options: [flightOption, selectedFlight]} as never);
    const guestProps = {...props, savedAirfare: null, savedStay: null};
    const mounted = render(<TripComparisonPage {...guestProps} initialSearch="AIRFARE" guest guestAirfare={selectedFlight} />);
    await screen.findByRole('button', {name: 'Selected'});
    expect(resultButtons('flights')[0]).toHaveTextContent('Selected');
    mounted.rerender(<TripComparisonPage {...guestProps} initialSearch="AIRFARE" guest guestAirfare={flightOption} />);
    expect(resultButtons('flights')[0].closest('article')).toHaveTextContent('New Air');
    expect(resultButtons('flights')[0]).toHaveTextContent('Selected');
    mounted.rerender(<TripComparisonPage {...guestProps} initialSearch="AIRFARE" guest />);
    expect(resultButtons('flights').map(button => button.textContent)).toEqual(['Add flight', 'Add flight']);
    expect(resultButtons('flights')[0].closest('article')).toHaveTextContent('New Air');
    expect(tripsApi.searchPublicAirfare).toHaveBeenCalledOnce();
  });

  it('does not pin the same stay with a different room count', async () => {
    vi.spyOn(tripsApi, 'searchStays').mockResolvedValue({options: [stayOption, selectedStay]} as never);
    render(<TripComparisonPage {...props} savedStay={{...stay, unitCount: 1}} initialSearch="STAY" />);
    await screen.findAllByRole('button', {name: 'Replace stay'});
    expect(screen.queryByRole('button', {name: 'Selected'})).not.toBeInTheDocument();
    expect(resultButtons('stays')[0].closest('article')).toHaveTextContent('New hotel');
  });

  it('unpins a car when the searched rental interval changes', async () => {
    vi.spyOn(tripsApi, 'searchRentals').mockResolvedValue({options: [rentalOption, selectedCar]} as never);
    render(<TripComparisonPage {...props} initialSearch="RENTAL" />);
    await screen.findByRole('button', {name: 'Selected'});
    fireEvent.change(screen.getByLabelText('Return date & time'), {target: {value: '2027-03-13T10:00'}});
    await screen.findAllByRole('button', {name: 'Replace car'});
    expect(screen.queryByRole('button', {name: 'Selected'})).not.toBeInTheDocument();
    expect(resultButtons('cars')[0].closest('article')).toHaveTextContent('New car');
  });
});
