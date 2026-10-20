import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { test } from '@e2e-dev/mobile';
import { expect } from 'e2e';

test('transport PIN and inactive eID remain separate and persist', { timeout: 180_000 }, async ({ app, screen, device }) => {
  await device.openApp('de.phoenix.simulator',{relaunch:true});
  const savedName='Max Mustermann';
  const testPuk=String(Date.now()%10_000_000_000).padStart(10,'0');
  const field=(label:string)=>screen.getByRole('textbox').filter({has:screen.getByText(label,{exact:true})});
  const openEditor=async()=>{
    await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();
    await screen.getByText('eID',{exact:true}).tap();
    await screen.getByText(savedName,{exact:true}).tap();
    await screen.getByText('Einstellungen',{exact:true}).tap();
  };
  const toggle=(label:string)=>screen.getByRole('group',{name:label,exact:true});
  const start=async()=>{
    await screen.getByTestId('nav-Simulation').getByText('Simulation',{exact:true}).tap();
    await screen.getByText('Simulation starten',{exact:true}).tap();
    await expect(screen.getByText('Bereit für Lesegerät',{exact:true})).toBeVisible({timeout:60_000});
  };
  const stop=async()=>{
    await screen.getByText('Simulation beenden',{exact:true}).tap();
    await expect(screen.getByText('Simulation beendet',{exact:true})).toBeVisible();
  };
  await openEditor();
  await expect(field('PIN')).toHaveValue('123456');
  await expect(field('PUK')).toHaveValue('1234567890');
  await expect(toggle('Transport-PIN-Modus')).not.toBeChecked();
  await expect(toggle('eID deaktivieren')).not.toBeChecked();
  try {
    await field('PUK').fill(testPuk);await expect(field('PUK')).toHaveValue(testPuk);await device.back();
    await toggle('Transport-PIN-Modus').tap();
    await field('Transport-PIN (5 Ziffern)').fill('12345');await device.back();
    await screen.getByText('Änderungen speichern',{exact:true}).tap();
    await openEditor();
    await expect(field('Transport-PIN (5 Ziffern)')).toHaveValue('12345');
    await expect(toggle('Transport-PIN-Modus')).toBeChecked();
    await start();
    try { if(process.env.PHOENIX_RF_TEST==='1')console.log(execFileSync(resolve('../Phoenix-ePassport-Core/.venv/bin/python'),[resolve('tests/device/pin_modes_probe.py'),'transport'],{encoding:'utf8',timeout:45_000})); }
    finally { await stop(); }
    await start();
    try { if(process.env.PHOENIX_RF_TEST==='1')console.log(execFileSync(resolve('../Phoenix-ePassport-Core/.venv/bin/python'),[resolve('tests/device/pin_modes_probe.py'),'personal'],{encoding:'utf8',timeout:45_000})); }
    finally { await stop(); }
    await openEditor();
    await toggle('Transport-PIN-Modus').tap();
    await field('PIN').fill('123456');await device.back();
    await toggle('eID deaktivieren').tap();
    await screen.getByText('Änderungen speichern',{exact:true}).tap();
    await openEditor();
    await expect(toggle('eID deaktivieren')).toBeChecked();
    await expect(toggle('Transport-PIN-Modus')).not.toBeChecked();
    await start();
    try { if(process.env.PHOENIX_RF_TEST==='1')console.log(execFileSync(resolve('../Phoenix-ePassport-Core/.venv/bin/python'),[resolve('tests/device/pin_modes_probe.py'),'inactive'],{encoding:'utf8',timeout:45_000})); }
    finally { await stop(); }
  } finally {
    await openEditor();
    if(await toggle('Transport-PIN-Modus').isChecked())await toggle('Transport-PIN-Modus').tap();
    if(await toggle('eID deaktivieren').isChecked())await toggle('eID deaktivieren').tap();
    await field('PIN').fill('123456');await device.back();
    await field('PUK').fill('1234567890');await expect(field('PUK')).toHaveValue('1234567890');await device.back();
    await screen.getByText('Änderungen speichern',{exact:true}).tap();
    await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();
    await screen.getByText('eID',{exact:true}).tap();
    await expect(screen.getByText('Max Mustermann',{exact:true})).toBeVisible();
  }
});
