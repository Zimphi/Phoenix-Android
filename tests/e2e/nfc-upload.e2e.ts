import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

const button=(screen:any,label:string)=>screen.getByRole('group').filter({hasText:label,has:screen.getByRole('button')}).last().getByRole('button');
test('diagnostic upload requires consent and supports server-copy deletion',{timeout:90_000},async({screen,app})=>{
  await expect(screen.getByText('Bestätigte Sendungen: 0',{exact:true})).toBeVisible({timeout:30_000});
  await button(screen,'Log sicher senden').tap();
  await expect(screen.getByText('Empfänger: contact@epassport-simulator.de',{exact:true})).toBeVisible();
  await button(screen,'Abbrechen').tap();
  await expect(screen.getByText('Bestätigte Sendungen: 0',{exact:true})).toBeVisible();
  await button(screen,'Log sicher senden').tap();
  await button(screen,'Verschlüsselt senden').tap();
  if(process.env.PHOENIX_UPLOAD_FAILURE==='true'){
    await expect(screen.getByText('Senden fehlgeschlagen. Prüfe deine Phoenix-Anmeldung und versuche es erneut. Das lokale Log bleibt erhalten.',{exact:true})).toBeVisible();
    await button(screen,'Abbrechen').tap();
  }else{
    await expect(screen.getByText('Verschlüsselt gesendet',{exact:true})).toBeVisible();
    await screen.scrollUntilVisible(button(screen,'Serverkopie löschen'));
    await button(screen,'Serverkopie löschen').tap();
    await expect(screen.getByText('Serverkopie gelöscht',{exact:true})).toBeVisible();
    await app.screenshot('nfc-upload-consent-and-delete');
    await button(screen,'Fertig').tap();
  }
  await expect(screen.getByText('Bestätigte Sendungen: 1',{exact:true})).toBeVisible();
  await button(screen,'Test beenden').tap();
});
