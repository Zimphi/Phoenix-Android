import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

for (const [kind, name] of [['eid', 'Max Mustermann'], ['epassport', 'Erika EAC-Test']]) {
  test(`${kind} full authentication with the local desktop client`, { timeout: 120_000 }, async ({ app, screen }) => {
    test.skip(!process.env.PHOENIX_AUTH_MATERIAL, 'Requires private synthetic terminal credentials and the physical reader');
    await app.open();
    await screen.getByTestId('nav-Profile').getByText('Profile', { exact: true }).tap();
    await screen.getByText(kind === 'eid' ? 'eID' : 'ePassport', { exact: true }).tap();
    await screen.getByText(name, { exact: true }).tap();
    await screen.getByTestId('nav-Simulation').getByText('Simulation', { exact: true }).tap();
    await screen.getByText('Simulation starten', { exact: true }).tap();
    await expect(screen.getByText('Bereit für Lesegerät', { exact: true })).toBeVisible({ timeout: 60_000 });
    try {
      console.log(execFileSync(resolve('../Phoenix-ePassport-Core/.venv/bin/python'), [resolve('tests/device/local_auth_probe.py'), kind], { encoding: 'utf8', timeout: 80_000 }));
      console.log(await app.screenshot(`${kind}-full-authentication`));
    } finally {
      await screen.getByText('Simulation beenden', { exact: true }).tap();
      await expect(screen.getByText('Simulation beendet', { exact: true })).toBeVisible();
    }
    await screen.getByTestId('nav-Profile').getByText('Profile', { exact: true }).tap();
  });
}
