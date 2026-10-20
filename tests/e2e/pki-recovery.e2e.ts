import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('existing profiles remain accessible after restoring the installed build',async({app,screen,device})=>{
  await device.openApp('de.phoenix.simulator',{relaunch:true});
  await expect(screen.getByText('Lokale Testprofile',{exact:true})).toBeVisible();
  await expect(screen.getByText('eID',{exact:true})).toBeVisible();
  await expect(screen.getByText('ePassport',{exact:true})).toBeVisible();
  await expect(screen.getByText('Profilspeicher nicht lesbar. Vorhandene Daten bleiben erhalten.',{exact:true})).not.toBeVisible();
  console.log(await app.screenshot('profiles-after-pki-test'));
});
