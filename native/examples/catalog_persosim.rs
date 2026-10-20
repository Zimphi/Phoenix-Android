//! Generate the public synthetic draft catalog using the unchanged core decoder.
use anyhow::Result;
use serde_json::json;
use std::path::Path;
fn main()->Result<()> {
 let root=Path::new(env!("CARGO_MANIFEST_DIR")).join("..");
 let manifest:serde_json::Value=serde_json::from_str(&std::fs::read_to_string(root.join("persosim-fixtures.lock.json"))?)?;
 let trust=openssl::x509::X509::from_der(include_bytes!("../../.private/persosim-csca.cer"))?;
 let mut catalog=Vec::new();
 for entry in manifest["profiles"].as_array().unwrap() {
   let file=entry["file"].as_str().unwrap();
   let profile=phoenix_eid_core::persosim_profile::parse(&root.join(".private/persosim-profiles").join(file))?;
   phoenix_eid_core::passive::verify_security_object(&profile.card_security,&[trust.clone()],chrono::Utc::now().timestamp(),&[],false)?;
   let mut groups=serde_json::Map::new();
   for (fid,raw) in profile.eid_files {
    let number=(fid-0x100) as u8;
    groups.insert(format!("DG{number}"),serde_json::Value::String(serde_json::to_string(&phoenix_eid_core::profile::decode(number,&raw)?)?));
   }
   println!("{file}: {} groups, trusted signature verified",groups.len());
   catalog.push(json!({"format":"phoenix-android-draft-v1","id":entry["id"],"name":entry["name"],"kind":"eid","groups":groups,"settings":{"can":"123456","pin":"123456","puk":"1234567890"}}));
 }
 anyhow::ensure!(catalog.len()==16);
 std::fs::write(root.join("app/src/main/assets/persosim-profiles.json"),serde_json::to_vec_pretty(&catalog)?)?;
 Ok(())
}
