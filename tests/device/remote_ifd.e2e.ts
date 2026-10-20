import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('IFD pairing belongs to settings and simulation has two interface icons',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByRole('button',{name:'Einstellungen',exact:true}).tap();
 await screen.getByText('IFD-Schnittstelle',{exact:true}).tap();
 await expect(screen.getByText('Smartphone als Kartenleser',{exact:true})).toBeVisible();
 await screen.getByText('Kopplung starten',{exact:true}).tap();
 try{
  await expect(screen.getByText('Kopplungscode',{exact:true})).toBeVisible({timeout:60_000});
 }finally{
  await screen.getByText('Kopplung beenden',{exact:true}).tap();
  await expect(screen.getByText('Kopplung starten',{exact:true})).toBeVisible();
 }
 await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('PersoSim 03 Testausweis',{exact:true}).tap();
 await screen.getByTestId('nav-Simulation').getByText('Simulation',{exact:true}).tap();
 const profileBounds=await screen.getByText('PersoSim 03 Testausweis',{exact:true}).boundingBox();
 const startBounds=await screen.getByText('Simulation starten',{exact:true}).boundingBox();
 for(let pass=0;pass<3;pass++){
 await screen.getByRole('radio').nth(1).tap();
 await expect(screen.getByText('Smartphone als Kartenleser',{exact:true})).toBeVisible();
 expect(await screen.getByText('PersoSim 03 Testausweis',{exact:true}).boundingBox()).toEqual(profileBounds);
 expect(await screen.getByText('Simulation starten',{exact:true}).boundingBox()).toEqual(startBounds);
 await expect(screen.getByText('Kopplung starten',{exact:true})).not.toBeVisible();
 await screen.getByRole('radio').nth(0).tap();
 await expect(screen.getByText('Smartphone an das Lesegerät halten',{exact:true})).toBeVisible();
 expect(await screen.getByText('PersoSim 03 Testausweis',{exact:true}).boundingBox()).toEqual(profileBounds);
 expect(await screen.getByText('Simulation starten',{exact:true}).boundingBox()).toEqual(startBounds);
 }
 await app.screenshot('stable-interface-layout');
});

if(process.env.PHOENIX_IFD_PAIRED_TEST==='1') {
 test('stored IFD pairing restarts and removal can be cancelled',async({app,screen,device})=>{
  await device.openApp('de.phoenix.simulator',{relaunch:true});
  try {
   await screen.getByText('eID',{exact:true}).tap();
   await screen.getByText('PersoSim 03 Testausweis',{exact:true}).tap();
   await screen.getByTestId('nav-Simulation').getByText('Simulation',{exact:true}).tap();
   await screen.getByRole('radio').nth(1).tap();
   await screen.getByText('Simulation starten',{exact:true}).tap();
   await expect(screen.getByText('IFD-Schnittstelle aktiv',{exact:true})).toBeVisible({timeout:60_000});
   await app.screenshot('ifd-final-active');
   await screen.getByText('Simulation beenden',{exact:true}).tap();
   await expect(screen.getByText('Simulation beendet',{exact:true})).toBeVisible();
   await screen.getByRole('button',{name:'Einstellungen',exact:true}).tap();
   await screen.getByText('IFD-Schnittstelle',{exact:true}).tap();
   await expect(screen.getByText('AusweisApp gekoppelt',{exact:true})).toBeVisible();
   await screen.getByRole('group').filter({has:screen.getByText('Kopplungen entfernen',{exact:true})}).last().getByRole('button').tap();
   await expect(screen.getByText('Kopplungen entfernen?',{exact:true})).toBeVisible();
   await screen.getByRole('group').filter({has:screen.getByText('Abbrechen',{exact:true})}).last().getByRole('button').tap();
   await expect(screen.getByText('AusweisApp gekoppelt',{exact:true})).toBeVisible();
   await app.screenshot('ifd-pairing-settings');
  }finally{await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();}
 });
}
