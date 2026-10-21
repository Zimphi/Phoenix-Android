import {test} from '@e2e-dev/mobile';
import {expect} from 'e2e';

test('passport selects both silhouettes as real DG2 images and loads each profile',{timeout:120_000},async({app,screen,device})=>{
 for(const label of ['Schattenriss männlich','Schattenriss weiblich','Bild 1']){
  await device.openApp('de.phoenix.simulator',{relaunch:true});
  await screen.getByText('ePassport',{exact:true}).tap();
  await screen.getByText('Erika Mustermann',{exact:true}).tap();
  await screen.getByTestId('editor-fields').scrollUntilVisible(screen.getByText('Gesichtsbild ansehen',{exact:true}));
  await screen.getByText('Gesichtsbild ansehen',{exact:true}).tap();
  await screen.getByRole('group').filter({hasText:'Bild auswählen',has:screen.getByRole('button')}).last().getByRole('button').tap();
  await expect(screen.getByText('Gesichtsbild auswählen',{exact:true})).toBeVisible();
  await screen.getByText(label,{exact:true}).tap();
  await expect(screen.getByText('Gesichtsbild auswählen',{exact:true})).not.toBeVisible();
  await screen.getByRole('button',{name:'Profil in Simulation übernehmen',exact:true}).tap();
  await expect(screen.getByText('Profil geladen',{exact:true})).toBeVisible();
  await expect(screen.getByText('Simulation beenden',{exact:true})).not.toBeVisible();
  await app.screenshot(label==='Bild 1'?'original-portrait-restored':label.endsWith('weiblich')?'passport-female-silhouette-loaded':'passport-male-silhouette-loaded');
 }
});
