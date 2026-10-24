import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

// Two concurrent runs, NFC_CAMERA_ROLE=master / peer; a person aligns the cameras.
test('camera QR scan establishes the temporary encrypted connection',{timeout:240_000},async({screen,device})=>{
  const master=process.env.NFC_CAMERA_ROLE==='master';
  await device.openApp('de.phoenix.simulator',{relaunch:true});
  await screen.getByRole('button',{name:'Einstellungen',exact:true}).tap();
  await screen.getByTestId('settings-nfc-test').tap();
  const action=screen.getByTestId(master?'nfc-test-create':'nfc-test-join').getByRole('button');
  await screen.scrollUntilVisible(action);
  await action.tap();
  if(master){
    await expect(screen.getByTestId('nfc-test-qr')).toBeVisible({timeout:30_000});
    await expect(screen.getByTestId('nfc-test-start')).toBeVisible({timeout:180_000});
    await expect(screen.getByText('Verschlüsselte Verbindung hergestellt',{exact:true})).toBeVisible();
  }else{
    await expect(screen.getByTestId('nfc-test-peer')).toBeVisible({timeout:180_000});
    await expect(screen.getByText('Verschlüsselt mit dem Master verbunden',{exact:true})).toBeVisible();
  }
});
