import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';
const button=(screen:any,label:string)=>screen.getByRole('group').filter({hasText:label,has:screen.getByRole('button')}).last().getByRole('button');

test('identity card flips by touch and automatically when scrolling without section headings',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await screen.getByText('Personalausweis Vorderseite',{exact:true}).tap();
 await expect(screen.getByText('Personalausweis Rückseite',{exact:true})).toBeVisible();
 await app.screenshot('identity-back-centered-watermark');
 await screen.getByText('Personalausweis Rückseite',{exact:true}).tap();
 await expect(screen.getByText('Personalausweis Vorderseite',{exact:true})).toBeVisible();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByTestId('group-DG22'));
 await expect(screen.getByText('Personalausweis Rückseite',{exact:true})).toBeVisible();
 await expect(screen.getByTestId('group-DG21')).toBeDisabled();
 await expect(screen.getByTestId('group-DG22')).toBeDisabled();
 await expect(screen.getByTestId('group-DG19')).not.toBeVisible();
 await expect(screen.getByText('Rückseite',{exact:true})).not.toBeVisible();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByTestId('group-DG5'),{direction:'up'});
 await expect(screen.getByText('Personalausweis Vorderseite',{exact:true})).toBeVisible();
});

test('eID dates validate day month year and address has separate fields',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await screen.getByTestId('group-DG8').tap();
 await expect(screen.getByTestId('Geburtsdatum-Jahr')).toHaveValue('1990');
 await screen.getByTestId('Geburtsdatum-Tag').fill('31');
 await screen.getByTestId('Geburtsdatum-Monat').fill('2');
 await app.back();
 await expect(screen.getByRole('group').filter({hasText:'Übernehmen',has:screen.getByRole('button')}).last()).toBeDisabled();
 await button(screen,'Abbrechen').tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByTestId('group-DG17'));
 await screen.getByTestId('group-DG17').tap();
 await expect(screen.getByText('Straße und Hausnummer',{exact:true})).toBeVisible();
 await expect(screen.getByText('Postleitzahl',{exact:true})).toBeVisible();
 await expect(screen.getByText('Ort',{exact:true})).toBeVisible();
 await expect(screen.getByText('Bundesland / Region',{exact:true})).toBeVisible();
 await app.screenshot('structured-address-editor');
 await button(screen,'Abbrechen').tap();
});

test('document type and doctorate use dropdowns',async({app,screen,device})=>{
 await device.openApp('de.phoenix.simulator',{relaunch:true});
 await screen.getByText('eID',{exact:true}).tap();
 await screen.getByText('Max Mustermann',{exact:true}).tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Dokumenttyp',{exact:true}));
 await screen.getByTestId('group-DG1').tap();
 await button(screen,'Dokumenttyp').tap();
 for(const label of ['ID · Personalausweis','AR · Aufenthaltstitel','AS · Aufenthaltserlaubnis Schweiz','AF · Aufenthaltskarte für EU-Familienangehörige','OA · Smart-eID','UB · eID-Karte für Unionsbürger'])await expect(screen.getByText(label,{exact:true}).last()).toBeVisible();
 await screen.getByText('AR · Aufenthaltstitel',{exact:true}).tap();
 await expect(screen.getByText('AR · Aufenthaltstitel',{exact:true})).toBeVisible();
 await button(screen,'Abbrechen').tap();
 await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Doktorgrad',{exact:true}),{direction:'up'});
 await screen.getByTestId('group-DG7').tap();
 await button(screen,'Doktorgrad').tap();
 await screen.getByText('Ja',{exact:true}).tap();
 await expect(screen.getByText('Ja',{exact:true})).toBeVisible();
 await app.screenshot('doctorate-yes-no-editor');
 await button(screen,'Abbrechen').tap();
});
