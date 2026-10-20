import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

test('profiles open their editor and data groups; legal and actual versions are accessible', async ({ app, screen }) => {
  await app.open();
  await expect(screen.getByText('Lokale Testprofile')).toBeVisible();
  await expect(screen.getByText('Profil importieren')).not.toBeVisible();
  await screen.getByText('Erika Mustermann').tap();
  await expect(screen.getByText('Profil importieren')).toBeVisible();
  await screen.getByText('Datengruppen').tap();
  await screen.getByText('DG1', { exact: true }).tap();
  await expect(screen.getByText('DG1 bearbeiten')).toBeVisible();
  await screen.getByTestId('cancel-group').getByRole('button').tap();
  await screen.getByText('Über', { exact: true }).tap();
  await expect(screen.getByText('0.1.0-dev', { exact: true })).toBeVisible();
  await expect(screen.getByText('0.1.7', { exact: true })).toBeVisible();
  await screen.getByRole('button', { name: 'Einstellungen' }).tap();
  await expect(screen.getByText('Cloud-Anbindung', { exact: true })).toBeVisible();
  await screen.getByText('Lizenzen & Drittanbieter', { exact: true }).tap();
  await expect(screen.getByText(/Original application code|Licensing boundaries/)).toBeVisible();
  await screen.getByTestId('close-legal').getByRole('button').tap();
  await screen.getByText('Datenschutz', { exact: true }).tap();
  await expect(screen.getByText(/Diese Entwicklungsversion speichert/)).toBeVisible();
});
