//! Maps editable synthetic drafts into existing native profile/PKI APIs; no card protocol implementation.
use anyhow::{ensure,Context,Result};
use phoenix_eid_core::{profile::{EidProfile,Value as Group},pki::store::Authority,authority::Anchor,credentials::CredentialState,profile_creation::{self,CreationOptions,CreationPki},personalization::PersonalizedProfile};
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
    let mut trusted=vec![openssl::x509::X509::from_der(include_bytes!("../../.private/persosim-csca.cer"))?];
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
        profile.set(number,value)?;profile.activate(number)?;
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
fn governikus_anchor()->Result<Anchor>{
    let source=include_str!("../../.private/eid-reference/tools/data/governikus-DETESTeID00009-link.hex");
    ensure!(phoenix_simulator::runtime::hex(&openssl::sha::sha256(source.as_bytes())).eq_ignore_ascii_case("5a4795b6052ea140f23979e05109473b4d58050f970afed8be80f9155f7da8fd"),"Unexpected Governikus test trust data");
    let raw=phoenix_eid_core::transport::unhex(source.trim())?;
    let certificate=phoenix_license_native::eid::Certificate::parse(&raw,None)?;
    let today=chrono::Utc::now().format("%Y-%m-%d").to_string();
    ensure!(certificate.role()==3&&certificate.holder()==b"DETESTeID00009"&&today>=certificate.effective_date()&&today<=certificate.expiration_date(),"Governikus test CVCA is not current");
    // Explicit public-key pin from the versioned test fixture, not an unverified issuer chain.
    Ok(Anchor{reference:certificate.holder().to_vec(),key:certificate.public_key().public_key_to_der()?,rights:certificate.rights(),oid:None,chat:certificate.chat_oid().into()})
}
fn persosim_source(root:&Path)->Result<std::path::PathBuf>{
    let source=include_bytes!("../../.private/PersoSimProfile03.perso");
    ensure!(phoenix_simulator::runtime::hex(&openssl::sha::sha256(source)).eq_ignore_ascii_case("b915bdfe2c322c0fb9168933d8035d1137c08616ae7ad31f583d5a5df7e33764"),"Unexpected PersoSim test data");
    let path=root.join("test-fixtures/persosim-2b045789-v1/profiles/Profile03.perso");
    std::fs::create_dir_all(path.parent().unwrap())?;
    if !path.exists(){phoenix_eid_core::persistence::write_atomic(&path,source)?;}
    ensure!(std::fs::read(&path)?==source,"PersoSim test data changed");Ok(path)
}
fn profile_id(kind:&str,bytes:&[u8])->String{
    let digest=openssl::sha::sha256(bytes);
    let suffix=digest[..8].iter().map(|v|format!("{v:02x}")).collect::<String>();
    format!("{}{suffix}",if kind=="persosim"{"android_eid_ps03v1_"}else{"android_"})
}
pub fn mount(native:&mut Native,root:&Path,value:Value,application:&mut Option<phoenix_eid_core::application::Application>)->Result<Value>{
    let bytes=serde_json::to_vec(&value)?;ensure!(bytes.len()<=1024*1024,"Draft limit");
    let draft:Draft=serde_json::from_value(value)?;
    ensure!(draft.format=="phoenix-android-draft-v1"&&["eid","epassport"].contains(&draft.kind.as_str()),"Unsupported draft");
    pki_admin::identifier(&draft.id)?;
    if draft.id=="eid-persosim03" {
        let original:Draft=serde_json::from_str(include_str!("../../app/src/main/assets/persosim03-draft.json"))?;
        ensure!(draft.kind=="eid"&&draft.groups==original.groups,"Signierte PersoSim-Datengruppen müssen unverändert bleiben; für eigene Daten ein separates Profil verwenden");
    }
    let id=profile_id(if draft.id=="eid-persosim03"{"persosim"}else{&draft.kind},&bytes);pki_admin::identifier(&id)?;
    *application=None;native.close();
    // Validate and encode before generating any local PKI or writing a profile.
    let eid=if draft.kind=="eid"{Some(eid_profile(&draft.groups)?)}else{None};
    let passport=if draft.kind=="epassport"{Some(passport_groups(&draft.groups)?)}else{None};
    pki(native,root)?;
    if let Some(profile)=eid {
        if draft.id!="eid-persosim03" {
            if !root.join("profiles/eid").join(&id).exists(){
                native.manage_profile(serde_json::from_value(json!({"op":"create","id":id,"document":"android-document","signer":0,"terminal":"android-terminal","profile":profile,"credentials":{"pin":draft.settings.pin,"can":draft.settings.can,"puk":draft.settings.puk,"mrz":"<<<<<<<<<<<<<<<<<<<<<<<<","pin_retries":3}}))?)?;
            }
            return native.mount(&id);
        }
        let anchor=governikus_anchor()?;
        if !root.join("profiles/eid").join(&id).exists(){
            let config=config(root)?;
            let Authority::Document(document)=pki_admin::load(&config.data,"android-document",&config.passphrase)? else{anyhow::bail!("Document test authority required")};
            let signer=document.document_signers().first().context("Document signer required")?;
            let credentials=CredentialState::new(draft.settings.pin.as_bytes(),draft.settings.can.as_bytes(),draft.settings.puk.as_bytes(),b"<<<<<<<<<<<<<<<<<<<<<<<<",3,10,true,true)?;
            profile_creation::create(&config.profiles.join(&id),&config.passphrase,&profile,&credentials,
                &CreationPki{csca:document.certificate(),signer,cvca:&anchor.key},
                &CreationOptions{protocols:Default::default(),authenticated_pin_management:false,mobile_eid_type_info:None,time:chrono::Utc::now().timestamp(),card_date:None,cvca_lineage:vec![],cvca_is_lineage:vec![],cvca_public_key:None,cvca_anchor:Some(anchor.clone()),cvca_authorization:None})?;
        }
        let config=config(root)?;
        if phoenix_eid_core::profile_info::ProfilePkiInfo::load(&config.profiles.join(&id))?.document_pki_id!="persosim:bsi-test:Profile03" {
            PersonalizedProfile::import_persosim(&config.profiles.join(&id),&persosim_source(root)?,&config.passphrase,&config.trusted_cscas,chrono::Utc::now().timestamp(),&[],Some("persosim:bsi-test:Profile03"),&[]).context("PersoSim profile import")?;
        }
        // The existing PersoSim-aware loader validates the whole authentication unit before use.
        let profile=PersonalizedProfile::load(&config.profiles.join(&id),&config.passphrase,&config.trusted_cscas,chrono::Utc::now().timestamp(),&[])?;
        *application=Some(std::sync::Arc::new(profile.into_card()?).application()?);
        Ok(json!({"connected":true,"profile":id,"backend":"phoenix-eid-core-rust"}))
    }else {
        if !root.join("profiles/epassport").join(&id).exists(){
            native.manage_passport_profile(serde_json::from_value(json!({"op":"personalize","id":id,"name":draft.name,"can":draft.settings.can,"data_groups":passport.unwrap(),"passive_authentication":{"mode":"classic","classic_authority":"android-document","pqc_authority":null,"pqc_signer":null}}))?)?;
        }
        native.mount_passport(&id)
    }
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn eid_uses_governikus_public_pin_and_invalidates_old_native_cache(){
        let anchor=governikus_anchor().unwrap();assert_eq!(anchor.reference,b"DETESTeID00009");assert!(anchor.oid.is_none());assert!(!anchor.key.is_empty());
        let bytes=b"same draft";assert_ne!(profile_id("persosim",bytes),profile_id("epassport",bytes));assert!(profile_id("persosim",bytes).starts_with("android_eid_ps03v1_"));
    }
    #[test] fn fixture_preserves_signed_files_data_and_ca_keys(){
        let root=Path::new(env!("CARGO_MANIFEST_DIR")).join("../.private");
        let original=phoenix_eid_core::persosim_profile::parse(&root.join("persosim/de.persosim.simulator/personalization/profiles/Profile03.perso")).unwrap();
        let adapted=phoenix_eid_core::persosim_profile::parse(&root.join("PersoSimProfile03.perso")).unwrap();
        assert_eq!(original.card_access,adapted.card_access);assert_eq!(original.card_security,adapted.card_security);assert_eq!(original.chip_security,adapted.chip_security);assert_eq!(original.eid_files,adapted.eid_files);
        assert_eq!(original.ca_keys.len(),adapted.ca_keys.len());
        for (a,b) in original.ca_keys.iter().zip(&adapted.ca_keys){assert!(a.private_scalar==b.private_scalar);assert_eq!(a.public_point,b.public_point);assert_eq!(a.key_id,b.key_id);assert_eq!(a.parameter_id,b.parameter_id);assert_eq!(a.privileged,b.privileged);}
        assert!(adapted.cvca_certificate.is_none());assert!(adapted.approximate_date.is_none());
    }
    #[test] fn malformed_encoded_dg_and_mrz_fail_before_personalization(){assert!(passport_groups(&BTreeMap::from([("DG2".into(),"zz".into())])).is_err());assert!(passport_groups(&BTreeMap::from([("DG1".into(),"{}".into())])).is_err());}
    #[test] fn eid_values_use_the_original_core_validation(){assert!(eid_profile(&BTreeMap::from([("DG8".into(),"not-a-date".into())])).is_err());let profile=eid_profile(&BTreeMap::from([("DG4".into(),"MAX".into()),("DG5".into(),"MUSTERMANN".into())])).unwrap();assert_eq!(profile.values[&4],Group::Text("MAX".into()));}
}
