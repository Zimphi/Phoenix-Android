//! Maps editable synthetic drafts into existing native profile/PKI APIs; no card protocol implementation.
use anyhow::{ensure,Context,Result};
use phoenix_eid_core::{profile::{EidProfile,Value as Group},pki::store::Authority};
use phoenix_simulator::{runtime::{Config,Native},pki_admin};
use serde::Deserialize;
use serde_json::{json,Value};
use std::{collections::BTreeMap,path::Path};
use zeroize::Zeroizing;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Draft { format:String,id:String,kind:String,name:String,groups:BTreeMap<String,String>,settings:Settings }
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Settings {can:String,pin:String,puk:String}

pub fn config(root:&Path)->Result<Config>{
    let secret=Zeroizing::new(std::fs::read(root.join("profile-passphrase"))?);
    ensure!(secret.len()==32,"Invalid local profile secret");
    let data=root.join("runtime");
    let mut trusted=vec![];
    if data.join("pki/android-document.bin").exists(){
        let Authority::Document(pki)=pki_admin::load(&data,"android-document",&secret)? else{anyhow::bail!("Document test authority required")};
        trusted.push(pki.certificate().clone());
    }
    Ok(Config{data,profiles:root.join("profiles/eid"),passport_profiles:root.join("profiles/epassport"),trusted_cscas:trusted,passphrase:secret,..Config::default()})
}
fn pki(native:&mut Native,root:&Path)->Result<()> {
    if !root.join("runtime/pki/android-document.bin").exists(){
        native.manage_pki(serde_json::from_value(json!({"op":"create_csca","id":"android-document","config":{}}))?)?;
    }
    let document=native.manage_pki(serde_json::from_value(json!({"op":"inspect","id":"android-document"}))?)?;
    if document["signers"].as_array().context("Document signer inventory missing")?.is_empty(){
        native.manage_pki(serde_json::from_value(json!({"op":"signer","id":"android-document","name":"Phoenix Android TEST DS","document_types":["P","ID"]}))?)?;
    }
    if !root.join("runtime/pki/android-terminal.bin").exists(){
        native.manage_pki(serde_json::from_value(json!({"op":"create_cvca","id":"android-terminal","config":{}}))?)?;
    }
    *native=Native::new(config(root)?);
    Ok(())
}
fn eid_profile(groups:&BTreeMap<String,String>)->Result<EidProfile>{
    let mut profile=EidProfile::default();
    for (name,value) in groups {
        let number:u8=name.strip_prefix("DG").context("Invalid Data Group")?.parse()?;
        let value=if value.trim_start().starts_with('{'){serde_json::from_str::<Group>(value)?}else{Group::Text(value.clone())};
        profile.set(number,value)?;
    }
    profile.validate()?;Ok(profile)
}
fn passport_groups(groups:&BTreeMap<String,String>)->Result<BTreeMap<String,String>> {
    let mut result=BTreeMap::new();
    for (name,value) in groups {
        let number:u8=name.strip_prefix("DG").context("Invalid Data Group")?.parse()?;
        ensure!((1..=16).contains(&number),"Invalid passport Data Group");
        let encoded=if number==1 && value.trim_start().starts_with('{') {
            cav4_local_prototype::personalization::form::mrz(&serde_json::from_str(value)?)?
        }else if number==2&&value=="synthetic:erika" {
            cav4_local_prototype::personalization::form::face(include_bytes!("../../.private/simulator/GUI/assets/erika-mustermann-passfoto.jpg"),"iso19794-5")?
        }else {phoenix_eid_core::transport::unhex(value)?};
        result.insert(number.to_string(),phoenix_simulator::runtime::hex(&encoded));
    }
    Ok(result)
}
pub fn mount(native:&mut Native,root:&Path,value:Value)->Result<Value>{
    let bytes=serde_json::to_vec(&value)?;ensure!(bytes.len()<=1024*1024,"Draft limit");
    let draft:Draft=serde_json::from_value(value)?;
    ensure!(draft.format=="phoenix-android-draft-v1"&&["eid","epassport"].contains(&draft.kind.as_str()),"Unsupported draft");
    pki_admin::identifier(&draft.id)?;
    let digest=openssl::sha::sha256(&bytes);
    let suffix=digest[..8].iter().map(|v|format!("{v:02x}")).collect::<String>();
    let id=format!("android_{suffix}");pki_admin::identifier(&id)?;
    native.close();
    // Validate and encode before generating any local PKI or writing a profile.
    let eid=if draft.kind=="eid"{Some(eid_profile(&draft.groups)?)}else{None};
    let passport=if draft.kind=="epassport"{Some(passport_groups(&draft.groups)?)}else{None};
    pki(native,root)?;
    if let Some(profile)=eid {
        if !root.join("profiles/eid").join(&id).exists(){
            native.manage_profile(serde_json::from_value(json!({"op":"create","id":id,"document":"android-document","signer":0,"terminal":"android-terminal","profile":profile,"credentials":{"pin":draft.settings.pin,"can":draft.settings.can,"puk":draft.settings.puk,"mrz":"<<<<<<<<<<<<<<<<<<<<<<<<","pin_retries":3}}))?)?;
        }
        native.mount(&id)
    }else {
        if !root.join("profiles/epassport").join(&id).exists(){
            native.manage_passport_profile(serde_json::from_value(json!({"op":"personalize","id":id,"name":draft.name,"can":draft.settings.can,"data_groups":passport.unwrap(),"passive_authentication":{"mode":"classic","classic_authority":"android-document","pqc_authority":null,"pqc_signer":null}}))?)?;
        }
        native.mount_passport(&id)
    }
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn malformed_encoded_dg_and_mrz_fail_before_personalization(){assert!(passport_groups(&BTreeMap::from([("DG2".into(),"zz".into())])).is_err());assert!(passport_groups(&BTreeMap::from([("DG1".into(),"{}".into())])).is_err());}
    #[test] fn eid_values_use_the_original_core_validation(){assert!(eid_profile(&BTreeMap::from([("DG8".into(),"not-a-date".into())])).is_err());let profile=eid_profile(&BTreeMap::from([("DG4".into(),"MAX".into()),("DG5".into(),"MUSTERMANN".into())])).unwrap();assert_eq!(profile.values[&4],Group::Text("MAX".into()));}
}
