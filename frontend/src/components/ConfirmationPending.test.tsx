import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {describe, expect, it, vi} from 'vitest';
import {ConfirmDeleteModal} from './ConfirmDeleteModal';
import {ConfirmRemoveModal} from './ConfirmRemoveModal';
import {CancelBookingModal} from './CancelBookingModal';

describe('pending destructive confirmations', () => {
  it('keeps Delete open through Escape, backdrop, and close control while pending', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<ConfirmDeleteModal isOpen target={{kind: 'draft', tripId: 'trip-1', draftId: 'draft-1', draftVersion: 1}} pending errorMessage={undefined} onClose={onClose} onConfirm={vi.fn()} />);
    const dialog = screen.getByRole('dialog', {name: 'Delete draft alternative'});
    expect(screen.getByRole('button', {name: 'Close dialog'})).toBeDisabled();
    expect(dialog).toHaveFocus();
    await user.tab();
    expect(dialog).toHaveFocus();
    await user.keyboard('{Escape}');
    await user.click(dialog.parentElement!);
    expect(onClose).not.toHaveBeenCalled();
    expect(dialog).toBeInTheDocument();
  });

  it('keeps Remove open through Escape and backdrop while pending', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<ConfirmRemoveModal isOpen componentTitle="Airfare" formattedPrice="$540.00" pending errorMessage={undefined} onClose={onClose} onConfirm={vi.fn()} />);
    const dialog = screen.getByRole('dialog', {name: 'Remove Airfare?'});
    expect(dialog).toHaveFocus();
    await user.tab();
    expect(dialog).toHaveFocus();
    await user.keyboard('{Escape}');
    await user.click(dialog.parentElement!);
    expect(onClose).not.toHaveBeenCalled();
    expect(dialog).toBeInTheDocument();
  });

  it('keeps pending Booking cancellation focus inside its dialog', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<CancelBookingModal isOpen tripLabel="Munich Trip" bookingReference="DT-123" pending errorMessage={undefined} onClose={onClose} onConfirm={vi.fn()} />);
    const dialog = screen.getByRole('dialog', {name: 'Cancel Booking'});
    expect(dialog).toHaveFocus();
    await user.tab();
    expect(dialog).toHaveFocus();
    await user.keyboard('{Escape}');
    expect(onClose).not.toHaveBeenCalled();
  });
});
