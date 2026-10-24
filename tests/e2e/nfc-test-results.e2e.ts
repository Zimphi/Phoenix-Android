import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

const button=(screen:any,label:string)=>screen.getByRole('group').filter({hasText:label,has:screen.getByRole('button')}).last().getByRole('button');

test('real paired results export and repeat without a new pairing',{timeout:420_000},async({screen,app})=>{
  const passed=screen.getByText('Alle Sessions und Auswahltests bestanden',{exact:true});
  await expect(passed).toBeVisible({timeout:180_000});
  await app.screenshot('nfc-test-real-results-light');
  const save=screen.getByTestId('nfc-test-save').getByRole('button');
  await screen.scrollUntilVisible(save);
  await screen.swipe({direction:'down',momentum:'slow'});
  await save.tap();
  const filename=`phoenix-nfc-e2e-${Date.now()}.json`;
  await expect(screen.getByRole('textbox')).toBeVisible();
  await screen.getByRole('textbox').fill(filename);
  await app.back();
  await expect(screen.getByRole('textbox')).toHaveValue(filename);
  await screen.getByRole('button',{name:/^(SPEICHERN|Speichern|SAVE|Save)$/}).tap();
  await expect(screen.getByText('Log gespeichert',{exact:true})).toBeVisible({timeout:15_000});
  await expect(button(screen,'Test wiederholen')).toBeVisible();
  await button(screen,'Test wiederholen').tap();
  await expect(screen.getByText('Patchbedarf nicht bestimmbar – Test läuft. Die Bewertung folgt nach Abschluss beider Richtungen.',{exact:true})).toBeVisible();
  await expect(screen.getByTestId('nfc-test-progress')).toBeVisible();
  await expect(passed).toBeVisible({timeout:240_000});
  await screen.scrollUntilVisible(button(screen,'Test wiederholen'));
  await app.screenshot('nfc-test-repeat-retained-pairing');
  await expect(button(screen,'Test wiederholen')).toBeVisible();
  await screen.swipe({direction:'down',momentum:'slow'});
  await button(screen,'Verbindung trennen').tap();
  // The hardware harness checks both controllers reached the disconnected state.
});
