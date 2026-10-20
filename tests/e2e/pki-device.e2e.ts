import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

test('settings offer QR device enrollment without claiming an active PKI connection', async ({app,screen,device}) => {
  await device.openApp('de.phoenix.simulator',{relaunch:true});
  await screen.getByRole('button',{name:'Einstellungen',exact:true}).tap();
  await screen.getByText('Zentrale PKI',{exact:true}).tap();
  await expect(screen.getByText('Noch kein PKI-Gerätezugang',{exact:true})).toBeVisible();
  await expect(screen.getByRole('group').filter({hasText:'PKI-QR-Code scannen',has:screen.getByRole('button')}).last()).toBeVisible();
  await expect(screen.getByText('PKI-Verbindung prüfen',{exact:true})).not.toBeVisible();
  await expect(screen.getByText('Gerät und zentrale PKI bereit',{exact:true})).not.toBeVisible();
  console.log(await app.screenshot('pki-device-settings'));
});
