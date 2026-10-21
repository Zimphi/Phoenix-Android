import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('eID portrait selection updates the document and survives restart',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await screen.getByText('Einstellungen',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByTestId('portrait-F'));
 await screen.getByTestId('editor-fields').swipe({direction:'down'});
 await screen.getByTestId('portrait-F').getByRole('checkbox').tap();
 await expect(screen.getByTestId('identity-portrait-F')).toBeVisible();
 await app.screenshot('female-portrait-and-phoenix-watermark');
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await expect(screen.getByTestId('identity-portrait-F')).toBeVisible();
 await screen.getByText('Einstellungen',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByTestId('portrait-M'));
 await screen.getByTestId('editor-fields').swipe({direction:'down'});
 await screen.getByTestId('portrait-M').getByRole('checkbox').tap();
 await expect(screen.getByTestId('identity-portrait-M')).toBeVisible();
 await app.screenshot('male-portrait-and-phoenix-watermark');
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await expect(screen.getByTestId('identity-portrait-M')).toBeVisible();
});
