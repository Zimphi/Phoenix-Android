import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';
test('native cloud screen preserves existing accounts and offers registration without a browser',async({app,screen,device})=>{
 await app.open();
 await screen.getByRole('button',{name:'Einstellungen',exact:true}).tap();
 await screen.getByText('Cloud-Anbindung',{exact:true}).tap();
 await expect(screen.getByTestId('cloud-registration')).toBeVisible();
 if(await screen.getByText('Dein Konto ist eingerichtet und diese App verbunden.',{exact:true}).isVisible()){
   await expect(screen.getByText('Zu meinen Profilen',{exact:true})).toBeVisible();
   await app.screenshot('native-cloud-existing-account-preserved');
   return;
 }
 await expect(screen.getByText('Dein Phoenix.\nÜberall verbunden.',{exact:true})).toBeVisible();
 await app.screenshot('native-cloud-welcome');
 if(await screen.getByText('Registrierung fortsetzen',{exact:true}).isVisible())return;
 await screen.getByText('Konto erstellen',{exact:true}).tap();
 await expect(screen.getByText('Beginnen wir mit\ndeiner E-Mail.',{exact:true})).toBeVisible();
 await screen.getByTestId('cloud-email').fill('synthetic@example.invalid');
 await device.back();
 await expect(screen.getByText('Bestätigungsmail senden',{exact:true})).toBeVisible();
 await app.screenshot('native-cloud-email');
 await screen.getByRole('group').filter({hasText:'‹ Zurück',has:screen.getByRole('button')}).last().getByRole('button').tap();
 await expect(screen.getByText('Dein Phoenix.\nÜberall verbunden.',{exact:true})).toBeVisible();
});
