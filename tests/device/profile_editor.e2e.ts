import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('editor shows actual eID values, a document, and saves only valid settings',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await expect(screen.getByText('Personalausweis Vorderseite',{exact:true})).toBeVisible();
 await expect(screen.getByText('ID',{exact:true})).toBeVisible();
 await expect(screen.getByText('Dokumenttyp',{exact:true})).toBeVisible();
 await screen.getByText('Einstellungen',{exact:true}).tap();
 await expect(screen.getByText('Änderungen speichern',{exact:true})).not.toBeVisible();
 await expect(screen.getByText('Profilname',{exact:true})).not.toBeVisible();
 await app.screenshot('unified-eid-editor-settings');
});

test('passport editor displays loaded MRZ fields and flags an unsupported original',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('ePassport',{exact:true}).tap();
 await screen.getByText('Erika Mustermann',{exact:true}).tap();
 await expect(screen.getByText('Reisepass Innenseite',{exact:true})).toBeVisible();
 await expect(screen.getByText(/Familienname: MUSTERMANN/)).toBeVisible();
 await app.screenshot('unified-passport-editor-values');
 await screen.getByRole('button',{name:'Zurück',exact:true}).tap();
 await screen.getByText('Depp',{exact:true}).first().tap();
 await expect(screen.getByText(/Nicht simulierbar: Nicht unterstütztes ePassport-Profilformat/)).toBeVisible();
 await screen.getByRole('button',{name:'Profil in Simulation übernehmen',exact:true}).tap();
 await expect(screen.getByRole('group').filter({hasText:'Simulation starten',has:screen.getByRole('button')}).last()).toBeDisabled();
 await expect(screen.getByText(/Nicht simulierbar: Nicht unterstütztes ePassport-Profilformat/)).toBeVisible();
 await app.screenshot('unsupported-original-passport');
});
