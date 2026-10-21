import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';
const button=(screen:any,label:string)=>screen.getByRole('group').filter({hasText:label,has:screen.getByRole('button')}).last().getByRole('button');

test('eID editor exposes persisted RI pairs and expert policy',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByTestId('profile-list').scrollUntilVisible(screen.getByText('Max Mustermann',{exact:true}));
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await screen.getByText('Einstellungen',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('RI-Schlüssel laden',{exact:true}));
 await button(screen,'RI-Schlüssel laden').tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Schlüssel anzeigen / ändern',{exact:true}));
 await button(screen,'Schlüssel anzeigen / ändern').tap();
 await expect(screen.getByText('RI-Schlüssel bearbeiten',{exact:true})).toBeVisible();
 await expect(screen.getByRole('textbox')).toHaveCount(4);
 const publicKey=await screen.getByRole('textbox').nth(1).inputValue();
 for(let i=0;i<4;i++)expect((await screen.getByRole('textbox').nth(i).inputValue()).length).toBeGreaterThan(20);
 await screen.getByRole('group').filter({hasText:'Abbrechen',has:screen.getByRole('button')}).last().getByRole('button').tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('RI bei Datenänderungen beibehalten',{exact:true}));
 await expect(screen.getByText('RI bei Datenänderungen beibehalten',{exact:true})).toBeVisible();
 await expect(screen.getByText(publicKey,{exact:true})).not.toBeVisible();
 await app.screenshot('ri-settings');
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Schattenriss',{exact:true}));
 await expect(screen.getByTestId('portrait-settings')).toBeVisible();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Profil übertragen',{exact:true}));
 await expect(screen.getByTestId('profile-transfer-settings')).toBeVisible();
 await app.screenshot('profile-settings-sections');
});
