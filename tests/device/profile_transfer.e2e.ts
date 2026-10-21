import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('profile settings export through Android document storage',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await expect(screen.getByText('Profil exportieren',{exact:true})).not.toBeVisible();
 await screen.getByText('Einstellungen',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Profil exportieren',{exact:true}));
 try {
 await screen.getByText('Profil exportieren',{exact:true}).tap();
 await screen.getByRole('textbox').fill(`phoenix-export-check-${Date.now()}.json`);
 await app.screenshot('android-export-file-picker');
 await app.back();
 await screen.getByRole('button',{name:/speichern/i}).tap();
 await expect(screen.getByText('Profil exportiert',{exact:true})).toBeVisible();
 await screen.getByRole('group').filter({hasText:'OK',has:screen.getByRole('button')}).last().getByRole('button').tap();
 await screen.getByText('Profil importieren',{exact:true}).tap();
 await app.screenshot('android-import-file-picker');
 await app.back();
 await expect(screen.getByText('Profil exportieren',{exact:true})).toBeVisible();
 } finally {
  for(let attempt=0;attempt<2&&await screen.getByTestId('com.google.android.documentsui:id/toolbar').first().isVisible();attempt++)await app.back();
 }
});
