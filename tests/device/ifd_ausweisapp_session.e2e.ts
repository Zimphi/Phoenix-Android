import {existsSync,writeFileSync} from 'node:fs';
import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

// Opt-in desktop coordination; never send test profiles to a model.
if(process.env.PHOENIX_IFD_DESKTOP_DIR) {
 test('AusweisApp IFD pairing disclosure reconnect and background stop',{timeout:1200_000},async({app,screen,device})=>{
  const directory=process.env.PHOENIX_IFD_DESKTOP_DIR!;
  const mark=(name:string,value='ready')=>writeFileSync(`${directory}/${name}`,value,{mode:0o600});
  const wait=async(name:string)=>expect.poll(()=>existsSync(`${directory}/${name}`),{timeout:600_000,interval:1000}).toBe(true);
  await device.openApp('de.phoenix.simulator',{relaunch:true});
  try {
   await screen.getByRole('button',{name:'Einstellungen',exact:true}).tap();
   await screen.getByText('IFD-Schnittstelle',{exact:true}).tap();
   await screen.getByText('Kopplung starten',{exact:true}).tap();
   await expect(screen.getByText('Kopplungscode',{exact:true})).toBeVisible({timeout:60_000});
   mark('pairing-code',await screen.getByText(/^\d{4}$/).textContent());
   await expect(screen.getByText('AusweisApp gekoppelt',{exact:true})).toBeVisible({timeout:300_000});
   await screen.getByText('Kopplung beenden',{exact:true}).tap();
   await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();
   await screen.getByText('eID',{exact:true}).tap();
   await screen.getByText('PersoSim 03 Testausweis',{exact:true}).tap();
   await screen.getByTestId('nav-Simulation').getByText('Simulation',{exact:true}).tap();
   await screen.getByRole('radio').nth(1).tap();
   const start=async()=>{
    await screen.getByText('Simulation starten',{exact:true}).tap();
    await expect(screen.getByText('IFD-Schnittstelle aktiv',{exact:true})).toBeVisible({timeout:60_000});
   };
   await start();await app.screenshot('ifd-active');mark('first-ready');await wait('first-done');
   await device.home();mark('background');await wait('background-checked');
   await device.openApp('de.phoenix.simulator');
   await expect(screen.getByText('Simulation beendet',{exact:true})).toBeVisible({timeout:20_000});
   await start();mark('second-ready');await wait('second-done');
   await screen.getByText('Simulation beenden',{exact:true}).tap();
   await expect(screen.getByText('Simulation beendet',{exact:true})).toBeVisible();
   mark('stopped');await wait('stop-checked');
  }finally {
   // Navigation stops the reader even when a desktop phase fails.
   await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();
  }
 });
}
