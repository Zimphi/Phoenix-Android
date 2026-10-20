import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('simulation selects document type then profile without opening the editor',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await app.screenshot('larger-phoenix-start-logo');
 await screen.getByTestId('nav-Simulation').getByText('Simulation',{exact:true}).tap();
 await screen.getByText('Profil auswählen',{exact:true}).tap();
 await expect(screen.getByText('Dokumenttyp auswählen',{exact:true})).toBeVisible();
 await screen.getByRole('checkbox').nth(0).tap();
 await screen.getByText('Erika Mustermann',{exact:true}).tap();
 await expect(screen.getByText('ePassport · Testprofil',{exact:true})).toBeVisible();
 await expect(screen.getByRole('group').filter({hasText:'IFD-Schnittstelle',has:screen.getByRole('radio')}).last()).toBeDisabled();
 await expect(screen.getByText('Datengruppen',{exact:true})).not.toBeVisible();
 await screen.getByText('Erika Mustermann',{exact:true}).tap();
 await screen.getByRole('checkbox').nth(1).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await expect(screen.getByText('eID · Testprofil',{exact:true})).toBeVisible();
 await expect(screen.getByRole('group').filter({hasText:'IFD-Schnittstelle',has:screen.getByRole('radio')}).last()).toBeEnabled();
 await expect(screen.getByText('Simulation starten',{exact:true})).toBeVisible();
 await app.screenshot('simulation-profile-selection');
 await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();
});

test('editor play transfers the saved profile to simulation without starting it',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await screen.getByRole('button',{name:'Profil in Simulation übernehmen',exact:true}).tap();
 await expect(screen.getByText('Max Mustermann',{exact:true})).toBeVisible();
 await expect(screen.getByText('Simulation starten',{exact:true})).toBeVisible();
 await expect(screen.getByText('Simulation beenden',{exact:true})).not.toBeVisible();
 await app.screenshot('editor-profile-in-simulation');
 await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();
});

test('editor play keeps an invalid draft in the editor',async({screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await screen.getByText('Einstellungen',{exact:true}).tap();
 await screen.getByRole('textbox').nth(0).fill('');
 await screen.getByRole('button',{name:'Profil in Simulation übernehmen',exact:true}).tap();
 await expect(screen.getByText('Profilname muss 1 bis 120 Zeichen enthalten',{exact:true})).toBeVisible();
 await screen.getByRole('button').tap();
 await expect(screen.getByText('Änderungen speichern',{exact:true})).toBeVisible();
 await expect(screen.getByText('Simulation starten',{exact:true})).not.toBeVisible();
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await expect(screen.getByText('Dokumenttyp auswählen',{exact:true})).toBeVisible();
});
