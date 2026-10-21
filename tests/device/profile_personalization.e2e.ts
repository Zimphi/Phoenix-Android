import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';
const button=(s:any,label:string)=>s.getByRole('group').filter({hasText:label,has:s.getByRole('button')}).last().getByRole('button');

test('eID restores personalization and retains it after relaunch',{timeout:180_000},async({app,screen,device})=>{
 async function open(){await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();await screen.getByText('Max Mustermann',{exact:true}).tap();}
 await open();
 await screen.getByText('Einstellungen',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Personalisierungsdaten zurücksetzen',{exact:true}));
 await button(screen,'Personalisierungsdaten zurücksetzen').tap();await button(screen,'Zurücksetzen').tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Geburtsort',{exact:true}));await screen.getByText('Geburtsort',{exact:true}).tap();
 await screen.getByRole('textbox').fill('HAMBURG');await expect(screen.getByRole('textbox')).toHaveValue('HAMBURG');await app.back();await button(screen,'Übernehmen').tap();
 await expect(screen.getByText('HAMBURG',{exact:true})).toBeVisible();
 await screen.getByText('Einstellungen',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('BSI-Testinfrastruktur',{exact:true}));
 await expect(screen.getByTestId('pki-bsi')).toBeEnabled();await expect(screen.getByTestId('pki-bsi')).not.toBeChecked();await screen.getByText('BSI-Testinfrastruktur',{exact:true}).tap();
 await expect(screen.getByTestId('pki-bsi')).toBeChecked();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Eigene Testinfrastruktur',{exact:true}));
 await expect(screen.getByTestId('pki-own')).toBeDisabled();
 if(await screen.getByText('Erfolgreiche Cloud-Anbindung erforderlich',{exact:true}).isVisible())await expect(screen.getByTestId('pki-phoenix')).toBeDisabled();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Personalisierungsdaten zurücksetzen',{exact:true}));
 await button(screen,'Personalisierungsdaten zurücksetzen').tap();await expect(screen.getByText('Personalisierungsdaten zurücksetzen?',{exact:true})).toBeVisible();
 await button(screen,'Zurücksetzen').tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Geburtsort',{exact:true}));
 await expect(screen.getByText('BERLIN',{exact:true})).toBeVisible();
 await open();await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Geburtsort',{exact:true}));await expect(screen.getByText('BERLIN',{exact:true})).toBeVisible();
 await app.screenshot('eid-personalization-restored');
});

test('ePassport settings offer reset and Phoenix PKI but no BSI PKI',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('ePassport',{exact:true}).tap();await screen.getByText('Erika Mustermann',{exact:true}).tap();await screen.getByText('Einstellungen',{exact:true}).tap();
 await expect(screen.getByTestId('credential-settings')).toBeVisible();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Eigene Testinfrastruktur',{exact:true}));
 await expect(screen.getByTestId('pki-bsi')).not.toBeVisible();
 await expect(screen.getByTestId('pki-settings')).toBeVisible();await expect(screen.getByTestId('pki-own')).toBeDisabled();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Personalisierungsdaten zurücksetzen',{exact:true}));
 await expect(screen.getByTestId('personalization-settings')).toBeVisible();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Profil übertragen',{exact:true}));
 await expect(screen.getByTestId('profile-transfer-settings')).toBeVisible();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Personalisierungsdaten zurücksetzen',{exact:true}));
 await button(screen,'Personalisierungsdaten zurücksetzen').tap();await expect(screen.getByText('Personalisierungsdaten zurücksetzen?',{exact:true})).toBeVisible();await button(screen,'Zurücksetzen').tap();
 await expect(screen.getByText('ERIKA MUSTERMANN',{exact:true})).toBeVisible();await app.screenshot('passport-personalization-restored');
});
