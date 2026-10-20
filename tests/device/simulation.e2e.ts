import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

test('both synthetic profiles start and stop the real local simulation', { timeout: 180_000 }, async ({ app, screen }) => {
  await app.open();
  for (const name of ['Erika Mustermann', 'Max Mustermann']) {
    await screen.getByTestId('nav-Profile').getByText('Profile', { exact: true }).tap();
    await screen.getByText(name.startsWith('Erika') ? 'ePassport' : 'eID', { exact: true }).tap();
    await screen.getByText(name, { exact: true }).tap();
    if (name.startsWith('Erika')) await screen.getByText('Simulation', { exact: true }).tap();
    await screen.getByText('Simulation starten', { exact: true }).tap();
    await expect(screen.getByText('Bereit für Lesegerät', { exact: true })).toBeVisible({ timeout: 60_000 });
    await expect(screen.getByText('Simulation beenden', { exact: true })).toBeVisible();
    console.log(await app.screenshot(name.startsWith('Erika') ? 'passport-active' : 'eid-active'));
    await screen.getByText('Simulation beenden', { exact: true }).tap();
    await expect(screen.getByText('Simulation beendet', { exact: true })).toBeVisible();
    await expect(screen.getByText('Simulation starten', { exact: true })).toBeVisible();
  }
  await screen.getByTestId('nav-Profile').getByText('Profile', { exact: true }).tap();
});
