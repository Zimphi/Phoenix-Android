//! Opt-in integration check using only a private copy of synthetic Android state.
#[path = "../src/provision.rs"] mod provision;
use anyhow::Result;
use phoenix_simulator::runtime::Native;
use serde_json::json;
fn main()->Result<()> {
 let directory=std::env::args().nth(1).expect("private test directory required");
 let root=std::path::Path::new(&directory);
 let mut native=Native::new(provision::config(root)?);
 let catalog:Vec<serde_json::Value>=serde_json::from_str(include_str!("../../app/src/main/assets/persosim-profiles.json"))?;
 anyhow::ensure!(catalog.len()==16);
 for mut draft in catalog {
 draft["settings"]["pin_retries"]=json!(2);draft["settings"]["puk_uses"]=json!(7);
 let name=draft["name"].as_str().unwrap().to_owned();
 let mut application=None;
 let mut changed=draft.clone();changed["groups"]["DG4"]=json!("CHANGED");
 anyhow::ensure!(provision::mount(&mut native,root,changed,&mut application).is_err(),"Changed signed identity must be rejected");
 let mounted=provision::mount(&mut native,root,draft,&mut application)?;
 let passphrase=std::fs::read(root.join("profile-passphrase"))?;
 let state=phoenix_eid_core::card_state::CardState::open(&std::fs::read(root.join("profiles/eid").join(mounted["profile"].as_str().unwrap()).join("state/card-state.bin"))?,&passphrase)?;
 let credentials=state.credentials()?;
 anyhow::ensure!(credentials.pin_retries()==2&&credentials.puk_uses()==7&&credentials.pin_active(),"Draft counters not preserved");
 let response=application.as_mut().unwrap().transmit(&phoenix_eid_core::transport::unhex("00a4040c09e80704007f00070302")?)?;
 anyhow::ensure!(response.ends_with(&[0x90,0]),"eID SELECT failed");
 native.close();println!("{name}: import, immutable signed DGs and SELECT verified");
 }
 Ok(())
}
