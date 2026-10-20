//! Opt-in integration check using only a private copy of synthetic Android state.
#[path = "../src/provision.rs"] mod provision;
use anyhow::Result;
use phoenix_simulator::runtime::Native;
use serde_json::json;
fn main()->Result<()> {
 let directory=std::env::args().nth(1).expect("private test directory required");
 let root=std::path::Path::new(&directory);
 let mut native=Native::new(provision::config(root)?);
 let source=phoenix_eid_core::persosim_profile::parse(&std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../.private/persosim/de.persosim.simulator/personalization/profiles/Profile03.perso"))?;
 let mut groups=serde_json::Map::new();
 for (fid,raw) in source.eid_files { let n=(fid-0x100) as u8; groups.insert(format!("DG{n}"),serde_json::Value::String(serde_json::to_string(&phoenix_eid_core::profile::decode(n,&raw)?)?)); }
 let draft=json!({"format":"phoenix-android-draft-v1","id":"eid-persosim03","kind":"eid","name":"PersoSim 03 Testausweis","groups":groups,"settings":{"can":"123456","pin":"123456","puk":"1234567890"}});
 std::fs::write(root.join("persosim03-draft.json"),serde_json::to_vec_pretty(&draft)?)?;
 let mut application=None;
 let mut changed=draft.clone();changed["groups"]["DG4"]=json!("CHANGED");
 anyhow::ensure!(provision::mount(&mut native,root,changed,&mut application).is_err(),"Changed signed identity must be rejected");
 provision::mount(&mut native,root,draft,&mut application)?;
 let response=application.as_mut().unwrap().transmit(&phoenix_eid_core::transport::unhex("00a4040c09e80704007f00070302")?)?;
 anyhow::ensure!(response.ends_with(&[0x90,0]),"eID SELECT failed");
 native.close();println!("eID test profile created and validated with PersoSim authentication material");Ok(())
}
