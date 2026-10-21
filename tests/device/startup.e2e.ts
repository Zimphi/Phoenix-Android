import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('cold launch reaches the profile overview with the Phoenix brand',{video:'on'},async({app,screen,device})=>{
 for(let cycle=0;cycle<3;cycle++) {
  await device.openApp('de.phoenix.simulator',{relaunch:true});
  await expect(screen.getByText('Dokumenttyp auswählen',{exact:true})).toBeVisible();
  await expect(screen.getByRole('group',{name:'Phoenix Logo',exact:true})).toBeVisible();
 }
 await app.screenshot('phoenix-after-cold-launch');
});
