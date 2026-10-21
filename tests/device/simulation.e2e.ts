import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

for (const [name,id] of [['Erika Mustermann','passport-erika'],['MUSTERMANN','eid-max'],['PersoSim 01','eid-persosim01'],['PersoSim 03','eid-persosim03']]) {
  test(`${name} starts and stops the real local simulation`, { timeout: 90_000 }, async ({ app, screen }) => {
    await app.open();
    await screen.getByTestId('nav-Profile').getByText('Profile', { exact: true }).tap();
    await screen.getByText(name.startsWith('Erika') ? 'ePassport' : 'eID', { exact: true }).tap();
    await screen.getByTestId('profile-list').scrollUntilVisible(screen.getByTestId(`profile-${id}`));
    await screen.getByTestId(`profile-${id}`).tap();
    if (name.startsWith('PersoSim')) {
      await expect(screen.getByText('eID · Signiertes PersoSim-Profil', { exact: true })).toBeVisible();
      await screen.getByText('Datengruppen', { exact: true }).tap();
      await screen.getByText('DG4', { exact: true }).tap();
      await expect(screen.getByText('Signiertes PersoSim-Testprofil: Datengruppen sind unveränderlich. Eigene Daten bitte in einem separaten Profil bearbeiten.', { exact: true })).toBeVisible();
      await screen.getByRole('button').tap();
    }
    await expect(screen.getByText('Simulation starten', { exact: true })).not.toBeVisible();
    await screen.getByTestId('nav-Simulation').getByText('Simulation', { exact: true }).tap();
    await screen.getByText('Simulation starten', { exact: true }).tap();
    await expect(screen.getByText('Bereit für Lesegerät', { exact: true })).toBeVisible({ timeout: 60_000 });
    await expect(screen.getByText('Simulation beenden', { exact: true })).toBeVisible();
    try {
      if (process.env.PHOENIX_RF_TEST === '1') console.log(execFileSync(resolve('../Phoenix-ePassport-Core/.venv/bin/python'), [resolve('tests/device/reader_probe.py'), name.startsWith('Erika') ? 'epassport' : 'eid', 'active'], { encoding: 'utf8', timeout: 45_000 }));
      console.log(await app.screenshot(name.startsWith('Erika') ? 'passport-active' : 'eid-active'));
    } finally {
      await screen.getByText('Simulation beenden', { exact: true }).tap();
      await expect(screen.getByText('Simulation beendet', { exact: true })).toBeVisible();
    }
    if (process.env.PHOENIX_RF_TEST === '1') console.log(execFileSync(resolve('../Phoenix-ePassport-Core/.venv/bin/python'), [resolve('tests/device/reader_probe.py'), name.startsWith('Erika') ? 'epassport' : 'eid', 'stopped'], { encoding: 'utf8', timeout: 15_000 }));
    await expect(screen.getByText('Simulation starten', { exact: true })).toBeVisible();
    await screen.getByTestId('nav-Profile').getByText('Profile', { exact: true }).tap();
  });
}
