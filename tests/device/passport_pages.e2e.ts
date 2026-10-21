import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('passport flips from landscape front to the approved portrait page without moving editor controls',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('ePassport',{exact:true}).tap();
 await screen.getByTestId('profile-passport-erika').tap();
 const front=screen.getByTestId('passport-page-front'),details=screen.getByTestId('passport-page-details');
 await expect(front).toBeVisible();
 const frame=await screen.getByTestId('document-preview-frame').boundingBox();
 const fields=await screen.getByTestId('editor-fields').boundingBox();
 expect(frame).not.toBeNull();expect(fields).not.toBeNull();expect(frame!.width).toBeGreaterThan(frame!.height);
 await app.screenshot('passport-landscape-front');
 await front.tap();await expect(details).toBeVisible();
 await expect(screen.getByText(/Reisepass zweite Innenseite · Hochformat .* CAN: 123456/)).toBeVisible();
 const portrait=await details.boundingBox();expect(portrait).not.toBeNull();
 expect(portrait!.width/portrait!.height).toBeCloseTo(1051/1496,2);
 expect(await screen.getByTestId('document-preview-frame').boundingBox()).toEqual(frame);
 expect(await screen.getByTestId('editor-fields').boundingBox()).toEqual(fields);
 await app.screenshot('passport-portrait-inner-page');
 await details.tap();await expect(front).toBeVisible();
 await expect(screen.getByText('Synthetisches Gesichtsbild',{exact:true})).toBeVisible();
 await front.tap();await expect(details).toBeVisible();await details.tap();
 await expect(front).toBeVisible();
 expect(await screen.getByTestId('editor-fields').boundingBox()).toEqual(fields);
});

test('catalogue passport uses the same portrait page and returns to its own front',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('ePassport',{exact:true}).tap();
 await screen.getByText('Mara Linden - Deutschland',{exact:true}).tap();
 await screen.getByTestId('passport-page-front').tap();await expect(screen.getByTestId('passport-page-details')).toBeVisible();
 await expect(screen.getByText(/Größe: keine Angabe · Augenfarbe: keine Angabe/)).toBeVisible();
 await screen.getByTestId('passport-page-details').tap();await expect(screen.getByTestId('passport-page-front')).toBeVisible();
 await expect(screen.getByText('MARA LINDEN',{exact:true})).toBeVisible();
 await app.screenshot('catalogue-passport-front-after-flip');
});

test('identity card still flips inside its unchanged landscape preview',async({screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});await screen.getByText('eID',{exact:true}).tap();
 await screen.getByTestId('profile-eid-max').tap();
 await screen.getByText(/^Personalausweis Vorderseite · Seriennummer /).tap();
 await expect(screen.getByText(/^Personalausweis Rückseite · Seriennummer /)).toBeVisible();
 const frame=await screen.getByTestId('document-preview-frame').boundingBox();expect(frame!.width).toBeGreaterThan(frame!.height);
 await screen.getByText(/^Personalausweis Rückseite · Seriennummer /).tap();
 await expect(screen.getByText(/^Personalausweis Vorderseite · Seriennummer /)).toBeVisible();
});
