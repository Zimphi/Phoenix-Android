import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';
import {readFileSync} from 'node:fs';

// Opt-in checks inspect supplied synthetic profiles without resetting their data.
if(process.env.PHOENIX_PROFILE_COMPATIBILITY_METADATA) {
 const cases=JSON.parse(readFileSync(process.env.PHOENIX_PROFILE_COMPATIBILITY_METADATA,'utf8'));
 test('stored eID profiles expose only compatible BSI personalization',{timeout:180_000},async({app,screen,device})=>{
  for(const profile of cases) {
   await device.openApp('de.phoenix.simulator',{relaunch:true});
   await screen.getByText('eID',{exact:true}).tap();
   await screen.getByTestId('profile-search').fill(profile.serial);
   if(profile.expired)await expect(screen.getByText('eID · Abgelaufen · Negativtest',{exact:true})).toBeVisible();
   await screen.getByTestId(`profile-${profile.id}`).tap();
   await screen.getByText('Einstellungen',{exact:true}).tap();
   await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('BSI-Testinfrastruktur',{exact:true}));
   if(profile.bsiCompatible) {
    await expect(screen.getByTestId('pki-bsi')).toBeEnabled();
    await expect(screen.getByTestId('pki-bsi')).toBeChecked();
   }else {
    await expect(screen.getByTestId('pki-bsi')).toBeDisabled();
    await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Profil-Testinfrastruktur',{exact:true}));
    await expect(screen.getByTestId('pki-profile')).toBeChecked();
   }
   await app.screenshot(`pki-${profile.id}`);
  }
 });
}
