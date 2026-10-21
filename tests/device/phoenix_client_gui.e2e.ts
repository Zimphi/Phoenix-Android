import {existsSync,writeFileSync} from 'node:fs';
import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

// Desktop markers advance only after the real Phoenix client UI and service result confirm each phase.
if(process.env.PHOENIX_CLIENT_GUI_DIR) {
 for(const mode of ['nfc','ifd']) {
 if(process.env.PHOENIX_CLIENT_GUI_MODE&&process.env.PHOENIX_CLIENT_GUI_MODE!==mode)continue;
 test(`Phoenix client GUI reads the Phoenix profile over ${mode}`,{timeout:1800_000},async({app,screen,device})=>{
  const directory=process.env.PHOENIX_CLIENT_GUI_DIR!;
  const mark=(name:string,value='ready')=>writeFileSync(`${directory}/${name}`,value,{mode:0o600});
  const wait=async(name:string)=>expect.poll(()=>existsSync(`${directory}/${name}`),{timeout:900_000,interval:1000}).toBe(true);
  await device.openApp('de.phoenix.simulator',{relaunch:true});
  try {
   if(mode==='ifd') {
    await screen.getByRole('button',{name:'Einstellungen',exact:true}).tap();
    await screen.getByText('IFD-Schnittstelle',{exact:true}).tap();
    await screen.getByText('Kopplung starten',{exact:true}).tap();
    await expect(screen.getByText('Kopplungscode',{exact:true})).toBeVisible({timeout:60_000});
    mark('pairing-code',await screen.getByText(/^\d{4}$/).textContent());
    await wait('paired');
    await screen.getByText('Kopplung beenden',{exact:true}).tap();
   }
   await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();
   await screen.getByText('eID',{exact:true}).tap();
   await screen.getByTestId('profile-eid-max').tap();
   await screen.getByTestId('nav-Simulation').getByText('Simulation',{exact:true}).tap();
   await screen.getByRole('radio').nth(mode==='ifd'?1:0).tap();
   await screen.getByText('Simulation starten',{exact:true}).tap();
   await expect(screen.getByText(mode==='ifd'?'IFD-Schnittstelle aktiv':'Bereit für Lesegerät',{exact:true})).toBeVisible({timeout:60_000});
   await app.screenshot(`phoenix-profile-${mode}`);mark(`${mode}-ready`);await wait(`${mode}-done`);
   await screen.getByText('Simulation beenden',{exact:true}).tap();
   await expect(screen.getByText('Simulation beendet',{exact:true})).toBeVisible();
   mark(`${mode}-stopped`);
  } finally {await screen.getByTestId('nav-Profile').getByText('Profile',{exact:true}).tap();}
 });
 }
}
