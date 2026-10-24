import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('real preview QR enrollment shows the user and logout persists',{timeout:300_000},async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByRole('button',{name:'Einstellungen',exact:true}).tap();
 await screen.getByText('Cloud-Anbindung',{exact:true}).tap();
 if(!await screen.getByTestId('open-account').isVisible())await screen.getByText('Mit QR-Code anmelden',{exact:true}).tap();
 await app.screenshot('qr-scanner-ready');
 await expect(screen.getByTestId('open-account')).toBeVisible({timeout:240_000});
 await screen.getByTestId('open-account').getByRole('button').tap();
 await expect(screen.getByText('Benutzerübersicht',{exact:true})).toBeVisible();
 await expect(screen.getByText('Benutzerkennung',{exact:true})).toBeVisible();
 await expect(screen.getByText(/^phx_[0-9a-f]{64}$/)).toBeVisible();
 await expect(screen.getByText('Preview',{exact:true})).toBeVisible();
 await app.screenshot('authenticated-user-overview');
 await screen.getByText('Abmelden',{exact:true}).tap();
 await expect(screen.getByTestId('open-account')).not.toBeVisible();
 await screen.getByText('Cloud-Anbindung',{exact:true}).tap();
 await expect(screen.getByText('Dein Phoenix.\nÜberall verbunden.',{exact:true})).toBeVisible();
 await expect(screen.getByRole('group').filter({hasText:'Mit QR-Code anmelden',has:screen.getByRole('button')}).last()).toBeEnabled();
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await expect(screen.getByText('Dokumenttyp auswählen',{exact:true})).toBeVisible();
 await expect(screen.getByTestId('open-account')).not.toBeVisible();
 await app.screenshot('logout-persists-after-restart');
});
