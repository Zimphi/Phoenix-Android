import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';
const button=(s:any,label:string)=>s.getByRole('group').filter({hasText:label,has:s.getByRole('button')}).last().getByRole('button');

test('document type selects eAT and UB artwork and restores the original identity type',{timeout:180_000},async({app,screen,device})=>{
 async function open(){await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();await screen.getByTestId('profile-eid-max').tap();}
 async function type(label:string,direction:"up"|"down"="down"){
  await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Dokumenttyp',{exact:true}),{direction});
  await screen.getByText('Dokumenttyp',{exact:true}).tap();
  await button(screen,'Dokumenttyp').tap();
  await screen.getByText(label,{exact:true}).last().tap();
  await button(screen,'Übernehmen').tap();
 }
 await open();
 try {
 await type('AR · Aufenthaltstitel');
 await open();
 await expect(screen.getByText(/^Aufenthaltstitel Vorderseite · Seriennummer /)).toBeVisible();
 await app.screenshot('eat-front');
 await screen.getByText(/^Aufenthaltstitel Vorderseite · Seriennummer /).tap();
 await expect(screen.getByText(/^Aufenthaltstitel Rückseite · Seriennummer /)).toBeVisible();
 await app.screenshot('eat-back-generated-mrz');
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByTestId('group-DG19'));
 await expect(screen.getByTestId('group-DG19')).toBeVisible();
 await type('UB · eID-Karte für Unionsbürger','up');
 await open();
 await expect(screen.getByText(/^eID-Karte Vorderseite · Seriennummer /)).toBeVisible();
 await app.screenshot('ub-front-eid-lettering-phoenix');
 await screen.getByText(/^eID-Karte Vorderseite · Seriennummer /).tap();
 await expect(screen.getByText(/^eID-Karte Rückseite · Seriennummer /)).toBeVisible();
 await app.screenshot('ub-back-not-valid-for-travel');
 } finally {
 await open();
 await type('ID · Personalausweis');
 await open();
 }
 await expect(screen.getByText(/^Personalausweis Vorderseite · Seriennummer /)).toBeVisible();
});
