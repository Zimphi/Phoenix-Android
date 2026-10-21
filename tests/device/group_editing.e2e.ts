import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('originals offer an editable copy without changing the original when cancelled',async({screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('ePassport',{exact:true}).tap();
 await screen.getByText('Mara Linden - Deutschland',{exact:true}).tap();
 await screen.getByText('MARA LINDEN',{exact:true}).tap();
 await expect(screen.getByText('Originalprofil bearbeiten',{exact:true})).toBeVisible();
 await expect(screen.getByText('Kopie bearbeiten',{exact:true})).toBeVisible();
 await screen.getByRole('group').filter({hasText:'Abbrechen',has:screen.getByRole('button')}).last().getByRole('button').tap();
 await expect(screen.getByText('Originalprofil bearbeiten',{exact:true})).not.toBeVisible();
 await expect(screen.getByText('MARA LINDEN',{exact:true})).toBeVisible();
});

test('own eID birth place opens as plain text and cancellation preserves it',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Geburtsort',{exact:true}));
 await screen.getByText('Geburtsort',{exact:true}).tap();
 await expect(screen.getByText('DG9 · Geburtsort',{exact:true})).toBeVisible();
 const original=await screen.getByRole('textbox').inputValue();
 if(original.startsWith('{'))throw new Error('Geburtsort muss als Klartext bearbeitbar sein');
 await screen.getByRole('textbox').fill(original==='HAMBURG'?'BERLIN':'HAMBURG');
 await app.back();
 await screen.getByRole('group').filter({hasText:'Abbrechen',has:screen.getByRole('button')}).last().getByRole('button').tap();
 await expect(screen.getByText('DG9 · Geburtsort',{exact:true})).not.toBeVisible();
 await screen.getByText('Geburtsort',{exact:true}).tap();
 await expect(screen.getByRole('textbox')).toHaveValue(original);
 await screen.getByRole('group').filter({hasText:'Abbrechen',has:screen.getByRole('button')}).last().getByRole('button').tap();
});


test('own eID data changes persist after relaunch and can be restored',{timeout:180_000},async({app,screen,device})=>{
 const button=(label:string)=>screen.getByRole('group').filter({hasText:label,has:screen.getByRole('button')}).last().getByRole('button');
 async function open(){await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();await screen.getByText('Max Mustermann',{exact:true}).tap();}
 async function edit(value:string){
  await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Geburtsort',{exact:true}));
  await screen.getByText('Geburtsort',{exact:true}).tap();
  await expect(screen.getByText('DG9 · Geburtsort',{exact:true})).toBeVisible();
  await screen.getByRole('textbox').fill(value);
  await app.back();
  await button('Übernehmen').tap();
  await expect(screen.getByText('DG9 · Geburtsort',{exact:true})).not.toBeVisible();
 }
 await open();
 try {
  await edit('HAMBURG');
  await expect(screen.getByText('HAMBURG',{exact:true})).toBeVisible();
  await screen.getByText('Geburtsort',{exact:true}).tap();
  await expect(screen.getByRole('textbox')).toHaveValue('HAMBURG');
  await button('Abbrechen').tap();
  await open();
  await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Geburtsort',{exact:true}));
  await expect(screen.getByText('HAMBURG',{exact:true})).toBeVisible();
  await app.screenshot('eid-dg9-saved-after-relaunch');
 } finally {await open();await edit('BERLIN');}
 await open();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Geburtsort',{exact:true}));
 await expect(screen.getByText('BERLIN',{exact:true})).toBeVisible();
});
