import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('a signed eID opens an editable copy and saves a DG immediately',{timeout:180_000},async({app,screen,device})=>{
 const original=screen.getByTestId('profile-eid-persosim03'),copy=screen.getByTestId('profile-source-eid-persosim03').first();
 const button=(label:string)=>screen.getByRole('group').filter({hasText:label,has:screen.getByRole('button')}).last().getByRole('button');
 async function list(){await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();}
 await list();
 const existing=await screen.getByTestId('profile-list').scrollUntilVisible(copy,{timeout:20000}).then(()=>true,()=>false);
 if(existing){
  await copy.tap();await screen.getByText('Familienname',{exact:true}).tap();
 }else{
  await list();await screen.getByTestId('profile-list').scrollUntilVisible(original);await original.tap();
  await screen.getByText('Familienname',{exact:true}).tap();
  await expect(screen.getByText('Originalprofil bearbeiten',{exact:true})).toBeVisible();
  await button('Kopie bearbeiten').tap();
 }
 await expect(screen.getByRole('textbox')).toHaveValue('MUSTERMANN');
 await screen.getByRole('textbox').tap();
 await expect(screen.getByRole('textbox')).toBeFocused();
 await screen.getByRole('textbox').fill('LINDEN');
 await expect(screen.getByRole('textbox')).toHaveValue('LINDEN');await app.back();await button('Übernehmen').tap();
 try {
  await expect(screen.getByText('LINDEN',{exact:true})).toBeVisible();
  await app.screenshot('persosim-copy-dg5-saved');
  await list();await screen.getByTestId('profile-list').scrollUntilVisible(copy);await copy.tap();
  await expect(screen.getByText('LINDEN',{exact:true})).toBeVisible();
 } finally {
  await list();await screen.getByTestId('profile-list').scrollUntilVisible(copy);await copy.tap();
  await screen.getByText('Familienname',{exact:true}).tap();
  await screen.getByRole('textbox').fill('MUSTERMANN');await app.back();await button('Übernehmen').tap();
 }
 await expect(screen.getByText('MUSTERMANN',{exact:true}).first()).toBeVisible();
});
