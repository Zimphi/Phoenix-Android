import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('real preview QR enrollment shows the user and logout persists',{timeout:300_000},async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByRole('button',{name:'Einstellungen',exact:true}).tap();
 await screen.getByText('Cloud-Anbindung',{exact:true}).tap();
 if(!await screen.getByRole('button',{name:'Benutzerkonto öffnen',exact:true}).isVisible())await screen.getByText('Mit QR-Code anmelden',{exact:true}).tap();
 await app.screenshot('qr-scanner-ready');
 await expect(screen.getByRole('button',{name:'Benutzerkonto öffnen',exact:true})).toBeVisible({timeout:240_000});
 await screen.getByRole('button',{name:'Benutzerkonto öffnen',exact:true}).tap();
 await expect(screen.getByText('Benutzerübersicht',{exact:true})).toBeVisible();
 await expect(screen.getByText('Benutzerkennung',{exact:true})).toBeVisible();
 await expect(screen.getByText(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/)).toBeVisible();
 await expect(screen.getByText('Preview',{exact:true})).toBeVisible();
 await app.screenshot('authenticated-user-overview');
 await screen.getByText('Abmelden',{exact:true}).tap();
 await expect(screen.getByRole('button',{name:'Benutzerkonto öffnen',exact:true})).not.toBeVisible();
 await screen.getByText('Cloud-Anbindung',{exact:true}).tap();
 await expect(screen.getByText('Nicht angemeldet',{exact:true})).toBeVisible();
 await expect(screen.getByRole('group').filter({hasText:'Mit QR-Code anmelden',has:screen.getByRole('button')}).last()).toBeEnabled();
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await expect(screen.getByText('Dokumenttyp auswählen',{exact:true})).toBeVisible();
 await expect(screen.getByRole('button',{name:'Benutzerkonto öffnen',exact:true})).not.toBeVisible();
 await app.screenshot('logout-persists-after-restart');
});
