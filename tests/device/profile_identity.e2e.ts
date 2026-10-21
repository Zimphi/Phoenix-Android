import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';
const button=(screen:any,label:string)=>screen.getByRole('group').filter({hasText:label,has:screen.getByRole('button')}).last().getByRole('button');

test('surname names and fictional serial numbers identify a profile after relaunch',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();
 await screen.getByTestId('profile-eid-max').tap();await screen.getByText('Einstellungen',{exact:true}).tap();
 const serial=await screen.getByTestId('profile-serial').inputValue();
 expect(serial).toMatch(/^[A-Z0-9]{1,9}$/);
 await screen.getByRole('button',{name:'Zurück',exact:true}).tap();
 await screen.getByTestId('profile-search').fill(serial);
 await expect(screen.getByTestId('profile-eid-max')).toBeVisible();
 await expect(screen.getByTestId('profile-eid-persosim01')).not.toBeVisible();
 await screen.getByTestId('profile-eid-max').tap();await screen.getByText('Datengruppen',{exact:true}).tap();
 await screen.getByText('Familienname',{exact:true}).tap();
 const surname=await screen.getByRole('textbox').inputValue();
 await screen.getByRole('textbox').fill('BEISPIEL');await app.back();await button(screen,'Übernehmen').tap();
 await expect(screen.getByText('BEISPIEL',{exact:true}).first()).toBeVisible();
 try {
  await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();
  await screen.getByTestId('profile-search').fill(serial);await expect(screen.getByText('BEISPIEL',{exact:true})).toBeVisible();
  await screen.getByTestId('profile-eid-max').tap();await screen.getByText('Einstellungen',{exact:true}).tap();
  await expect(screen.getByTestId('profile-serial')).toHaveValue(serial);
 } finally {
  await screen.getByText('Datengruppen',{exact:true}).tap();await screen.getByText('Familienname',{exact:true}).tap();
  await screen.getByRole('textbox').fill(surname);await app.back();await button(screen,'Übernehmen').tap();
 }
 await app.screenshot('surname-and-serial-profile');
});

test('PKI always selects a real option with BSI on signed eID defaults',async({screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();
 await screen.getByTestId('profile-list').scrollUntilVisible(screen.getByTestId('profile-eid-persosim03'));
 await screen.getByTestId('profile-eid-persosim03').tap();await screen.getByText('Einstellungen',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('BSI-Testinfrastruktur',{exact:true}));
 await expect(screen.getByTestId('pki-bsi')).toBeChecked();
 await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('ePassport',{exact:true}).tap();
 await screen.getByText('Erika Mustermann',{exact:true}).tap();await screen.getByText('Einstellungen',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Profil-Testinfrastruktur',{exact:true}));
 await expect(screen.getByTestId('pki-profile')).toBeChecked();
});

test('a structured German address updates DG18 using the offline municipality catalogue',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();await screen.getByTestId('profile-eid-max').tap();
 async function address(direction:'up'|'down'='down'){await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Anschrift',{exact:true}),{direction});await screen.getByText('Anschrift',{exact:true}).tap();}
 await address();const postal=await screen.getByTestId('address-zipcode').inputValue(),city=await screen.getByTestId('address-city').inputValue();
 await screen.getByTestId('address-zipcode').fill('22043');await screen.getByTestId('address-city').fill('HAMBURG');await app.back();await button(screen,'Übernehmen').tap();
 try {
  await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByTestId('group-DG18'));
  await expect(screen.getByTestId('group-DG18').getByText('02760200000000',{exact:true})).toBeVisible();
 } finally {
  await address('up');await screen.getByTestId('address-zipcode').fill(postal);await screen.getByTestId('address-city').fill(city);await app.back();await button(screen,'Übernehmen').tap();
 }
});
