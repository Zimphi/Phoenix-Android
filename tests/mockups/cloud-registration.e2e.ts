import {test} from '@e2e-dev/web';
import {expect} from 'e2e';
test('native registration mockup follows mail-link, passkey and connected states',async({app,screen})=>{
 await app.open('/Phoenix-Android/design/cloud-registration/');
 await screen.getByRole('button',{name:'Einstieg',exact:true}).click();
 await screen.getByRole('button',{name:'Konto erstellen',exact:true}).click();
 await expect(screen.getByRole('heading',{name:'Beginnen wir mit deiner E-Mail.'})).toBeVisible();
 await screen.getByRole('button',{name:'Bestätigungsmail senden',exact:true}).click();
 await expect(screen.getByText('482 619',{exact:true})).toBeVisible();
 await screen.getByRole('button',{name:'In Phoenix-App bestätigen',exact:true}).click();
 await screen.getByRole('main').getByRole('button',{name:'Passkey erstellen',exact:true}).click();
 await expect(screen.getByText('Android · beispielhafter Systemdialog',{exact:true})).toBeVisible();
 await screen.getByRole('button',{name:'Bestätigung simulieren',exact:true}).click();
 await expect(screen.getByRole('heading',{name:'Willkommen bei Phoenix Cloud.'})).toBeVisible();
 await expect(screen.getByText(/^phx_[a-f0-9]{64}$/)).toBeVisible();
 await app.screenshot('phoenix-cloud-registration-complete');
});
test('OTP alternative and cancellation keep the mockup navigable',async({app,screen})=>{
 await app.open('/Phoenix-Android/design/cloud-registration/');
 await screen.getByRole('button',{name:'Einmalcode',exact:true}).click();
 await screen.getByRole('button',{name:'E-Mail bestätigen',exact:true}).click();
 await screen.getByRole('main').getByRole('button',{name:'Passkey erstellen',exact:true}).click();
 await screen.getByRole('button',{name:'Abbrechen',exact:true}).click();
 await expect(screen.getByRole('main').getByRole('button',{name:'Passkey erstellen',exact:true})).toBeVisible();
});
