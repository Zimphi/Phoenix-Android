import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';
import {execFileSync} from 'node:child_process';
import {writeFileSync} from 'node:fs';
const adb=process.env.ADB??'adb';
const diagnostic=(...args:string[])=>execFileSync(adb,[...(process.env.ANDROID_SERIAL?['-s',process.env.ANDROID_SERIAL]:[]),'shell',...args],{encoding:'utf8'});

test('repeated document and navigation switches remain usable',{timeout:240_000},async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await expect(screen.getByText('Dokumenttyp auswählen',{exact:true})).toBeVisible();
 diagnostic('dumpsys','gfxinfo','de.phoenix.simulator','reset');
 for(let cycle=0;cycle<5;cycle++) {
  await screen.getByText('eID',{exact:true}).tap();await screen.getByTestId('profile-eid-max').tap();
  await expect(screen.getByText(/^Personalausweis Vorderseite · Seriennummer /)).toBeVisible();
  await screen.getByText('Einstellungen',{exact:true}).tap();await screen.getByText('Datengruppen',{exact:true}).tap();
  await screen.getByText(/^Personalausweis Vorderseite · Seriennummer /).tap();await expect(screen.getByText(/^Personalausweis Rückseite · Seriennummer /)).toBeVisible();
  await screen.getByRole('button',{name:'Zurück',exact:true}).tap();await screen.getByRole('button',{name:'Zurück',exact:true}).tap();
  await screen.getByText('ePassport',{exact:true}).tap();await screen.getByTestId('profile-passport-erika').tap();
  await screen.getByTestId('passport-page-front').tap();await expect(screen.getByTestId('passport-page-details')).toBeVisible();
  await screen.getByTestId('passport-page-details').tap();await expect(screen.getByText('Synthetisches Gesichtsbild',{exact:true})).toBeVisible();
  await screen.getByRole('button',{name:'Zurück',exact:true}).tap();await screen.getByRole('button',{name:'Zurück',exact:true}).tap();
  await screen.getByTestId('nav-Über').tap();await screen.getByTestId('nav-Profile').tap();
 }
 const stats=diagnostic('dumpsys','gfxinfo','de.phoenix.simulator');
 const p99=stats.match(/^99th percentile: (\d+)ms/m);
 expect(p99).not.toBeNull();expect(Number(p99![1])).toBeLessThan(500);
 console.log(stats.split('Pipeline=')[0]);
 if(process.env.PHOENIX_PERF_OUTPUT)writeFileSync(process.env.PHOENIX_PERF_OUTPUT,stats);
 await expect(screen.getByText('Dokumenttyp auswählen',{exact:true})).toBeVisible();
});
