import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';


test('editor shows actual eID values, a document, and saves only valid settings',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await expect(screen.getByText('Personalausweis Vorderseite',{exact:true})).toBeVisible();
 await expect(screen.getByText('ID',{exact:true})).toBeVisible();
 await expect(screen.getByText('Dokumenttyp',{exact:true})).toBeVisible();
 await expect(screen.getByText('Profil importieren',{exact:true})).not.toBeVisible();
 await expect(screen.getByText('Profil exportieren',{exact:true})).not.toBeVisible();
 await screen.getByText('Einstellungen',{exact:true}).tap();
 await expect(screen.getByText('Änderungen speichern',{exact:true})).not.toBeVisible();
 await expect(screen.getByText('Profilname',{exact:true})).not.toBeVisible();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Profil exportieren',{exact:true}));
 await expect(screen.getByText('Profil importieren',{exact:true})).toBeVisible();
 await expect(screen.getByText('Profil exportieren',{exact:true})).toBeVisible();
 await app.screenshot('unified-eid-editor-settings');
});

test('new national passport is loaded with its real values and retired fixtures disappear',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('ePassport',{exact:true}).tap();
 await expect(screen.getByText('Depp',{exact:true})).not.toBeVisible();
 await screen.getByText('Mara Linden - Deutschland',{exact:true}).tap();
 await expect(screen.getByText('Reisepass Innenseite',{exact:true})).toBeVisible();
 await expect(screen.getByText(/Familienname: LINDEN/)).toBeVisible();
 await expect(screen.getByText(/Vorname.*MARA/)).toBeVisible();
 await expect(screen.getByText('Synthetisches Gesichtsbild',{exact:true})).toBeVisible();
 await app.screenshot('icao-passport-data-page');
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Gesichtsbild ansehen',{exact:true}));
 await screen.getByText('Gesichtsbild ansehen',{exact:true}).tap();
 await expect(screen.getByText('DG2 · Gesichtsbild',{exact:true})).toBeVisible();
 await app.screenshot('passport-dg2-portrait');
 await screen.getByRole('group').filter({hasText:'Schließen',has:screen.getByRole('button')}).last().getByRole('button').tap();
 await screen.getByRole('button',{name:'Profil in Simulation übernehmen',exact:true}).tap();
 await expect(screen.getByText('Profil geladen',{exact:true})).toBeVisible();
 await expect(screen.getByRole('group').filter({hasText:'Simulation starten',has:screen.getByRole('button')}).last()).toBeEnabled();
 await expect(screen.getByText('Simulation beenden',{exact:true})).not.toBeVisible();
 await app.screenshot('synthetic-national-passport');
});

test('passport DG1 fields generate MRZ without changing the draft when cancelled',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('ePassport',{exact:true}).tap();
 await screen.getByText('Erika Mustermann',{exact:true}).tap();
 await screen.getByText(/Familienname: MUSTERMANN/).tap();
 await expect(screen.getByText('DG1 · Personendaten',{exact:true})).toBeVisible();
 await screen.getByRole('textbox').nth(0).fill('Mueller');
 await app.back();
 await screen.getByRole('group').filter({hasText:'Ausstellender Staat',has:screen.getByRole('button')}).last().getByRole('button').tap();
 await screen.getByRole('textbox').fill('FRA');
 await screen.getByText('Frankreich',{exact:true}).tap();
 await screen.swipe({direction:'down'});
 await expect(screen.getByText(/P<FRA.*MUELLER<<ERIKA/)).toBeVisible();
 await expect(screen.getByRole('group').filter({hasText:'Übernehmen',has:screen.getByRole('button')}).last().getByRole('button')).toBeEnabled();
 await app.screenshot('structured-passport-dg1-and-mrz');
 await screen.getByRole('group').filter({hasText:'Abbrechen',has:screen.getByRole('button')}).last().getByRole('button').tap();
 await expect(screen.getByText(/Familienname: MUSTERMANN/)).toBeVisible();
});
