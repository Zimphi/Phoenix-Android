import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';

if(process.env.PHOENIX_TEST_PANSTAR_ANDROID==='1') {
 const reader=process.env.PHOENIX_PANSTAR_READER??'remote-ifd://192.168.10.171:24727?slot=Phoenix%20virtual%20eID%20card';
 const ifd=reader.startsWith('remote-ifd://');
 const metadata=process.env.PHOENIX_PANSTAR_METADATA?JSON.parse(readFileSync(process.env.PHOENIX_PANSTAR_METADATA,'utf8')):[];
 const ids=process.env.PHOENIX_PANSTAR_PROFILE_IDS?.split(',')??(metadata.length?metadata.map((p:any)=>p.id):['eid-persosim03']);
 for(const id of ids)test(`Android ${id} completes Panstar EAC2 over ${ifd?'paired IFD':'NFC/PCSC'}`,{timeout:180_000},async({screen,device})=>{
  await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();
  const serial=metadata.find((p:any)=>p.id===id)?.serial;
  if(serial)await screen.getByTestId('profile-search').fill(serial);
  else await screen.getByTestId('profile-list').scrollUntilVisible(screen.getByTestId(`profile-${id}`));
  await screen.getByTestId(`profile-${id}`).tap();await screen.getByText('Einstellungen',{exact:true}).tap();
  const pin=await screen.getByRole('textbox').filter({has:screen.getByText('PIN',{exact:true})}).inputValue();
  await screen.getByRole('button',{name:'Profil in Simulation übernehmen',exact:true}).tap();
  await expect(screen.getByText('Profil geladen',{exact:true})).toBeVisible({timeout:60_000});
  await screen.getByRole('group',{name:ifd?'IFD-Schnittstelle':'NFC',exact:true}).tap();
  await screen.getByText('Simulation starten',{exact:true}).tap();
  try {
   await expect(screen.getByText(ifd?'IFD-Schnittstelle aktiv':'Bereit für Lesegerät',{exact:true})).toBeVisible({timeout:60_000});
   const raw=execFileSync(process.env.PYTHON??'python3',['tests/device/panstar_probe.py'],{input:JSON.stringify({reader,pin,rights:process.env.PHOENIX_PANSTAR_RIGHTS??'identity'}),encoding:'utf8',timeout:120_000});
   const result=JSON.parse(raw);console.log(JSON.stringify({profile:id,...result}));
   expect(result.authenticated).toBe(true);expect(result.service_http).toBe(200);expect(result.service_result_page).toBe(true);
  } finally {
   if(await screen.getByText('OK',{exact:true}).isVisible())await screen.getByText('OK',{exact:true}).tap();
   if(await screen.getByText('Simulation beenden',{exact:true}).isVisible())await screen.getByText('Simulation beenden',{exact:true}).tap();
   await screen.getByTestId('nav-Profile').tap();
  }
 });
}
