//! Maps editable synthetic drafts into existing native profile/PKI APIs; no card protocol implementation.
use anyhow::{ensure,Context,Result};
use phoenix_eid_core::{profile::{EidProfile,Value as Group},pki::store::Authority,authority::Anchor,credentials::CredentialState,profile_creation::{self,CreationOptions,CreationPki},personalization::PersonalizedProfile};
use phoenix_simulator::{runtime::{Config,Native},pki_admin};
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use std::{collections::BTreeMap,path::Path};
use zeroize::Zeroizing;

#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Draft { format:String,id:String,kind:String,name:String,groups:BTreeMap<String,String>,settings:Settings,
    #[serde(default)] ri:Option<Value>,
    #[serde(default)] baseline_groups:Option<BTreeMap<String,String>>,
    #[serde(default)] pki_infrastructure:Option<String>,
    #[serde(default)] persosim_source:Option<String>,
    #[serde(default)] personalization_generation:Option<String>,
}
#[derive(Clone,PartialEq,Deserialize,Serialize)]
#[serde(deny_unknown_fields)]
struct Settings {can:String,pin:String,puk:String,
    #[serde(default="pin_retries")] pin_retries:u8,
    #[serde(default="puk_uses")] puk_uses:u8,
    #[serde(default)] transport_pin:bool,
    #[serde(default="eid_active")] eid_active:bool,
}
fn pin_retries()->u8{3}
fn puk_uses()->u8{10}
fn eid_active()->bool{true}
#[path="persosim_sources.rs"] mod persosim_sources;
fn fixture(id:&str)->Option<(&'static str,&'static [u8])>{
    persosim_sources::SOURCES.iter().find(|(key,_,_)|*key==id).map(|(_,file,bytes)|(*file,*bytes))
}

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
        ensure!((1..=22).contains(&number)&&number!=16,"Invalid eID Data Group");
        let value=if value.trim_start().starts_with('{'){serde_json::from_str::<Group>(value)?}else{Group::Text(value.clone())};
        profile.values.insert(number,value);
    }
    let Some(Group::Text(document))=profile.values.get(&1) else{anyhow::bail!("Document type must be text")};
    let allowed=phoenix_eid_core::profile::allowed_data_groups(document);
    // Validate the complete draft; a document-type change can invalidate the previous active set.
    profile.active.retain(|number|allowed.contains(number));
    for name in groups.keys(){
        let number:u8=name[2..].parse()?;
        profile.encode(number)?;
        if allowed.contains(&number){profile.active.insert(number);}
    }
    profile.validate()?;Ok(profile)
}
fn profile_changed(before:&EidProfile,after:&EidProfile)->Result<bool> {
    if before.active!=after.active{return Ok(true);}
    for number in &after.active {if before.encode(*number)?!=after.encode(*number)?{return Ok(true);}}
    Ok(false)
}
fn community_check(profile:&EidProfile)->Value {
    use phoenix_eid_core::profile::GeneralPlace;
    let place=match profile.values.get(&17) {
        Some(Group::Place(place))|Some(Group::GeneralPlace(GeneralPlace::Structured(place)))=>Some(place),
        Some(Group::Places(places)) if places.len()==1=>match &places[0]{GeneralPlace::Structured(place)=>Some(place),_=>None},
        _=>None,
    };
    let Some(place)=place else{return json!({"status":"unstructured"});};
    if !["D","DEU"].contains(&place.country.as_str()){return json!({"status":"foreign"});}
    let postal=place.zipcode.as_deref().unwrap_or("");
    let current=match profile.values.get(&18){Some(Group::Text(text))=>text.clone(),Some(Group::Bytes(bytes))=>phoenix_simulator::runtime::hex(bytes),_=>String::new()};
    match phoenix_eid_core::community_id::lookup_community_id(postal,&place.city) {
        Ok(found)=>json!({"status":if current==found.encoded_community_id{"matching"}else{"mismatch"},"current":current,"expected":found.encoded_community_id,"ags":found.community_id,"postal":postal,"city":place.city}),
        Err(error)=>json!({"status":"unresolved","message":error.to_string(),"postal":postal,"city":place.city}),
    }
}
pub fn describe(value:Value)->Result<Value>{
    ensure!(serde_json::to_vec(&value)?.len()<=1024*1024,"Draft limit");
    let draft:Draft=serde_json::from_value(value)?;
    ensure!(draft.format=="phoenix-android-draft-v1","Unsupported draft");
    if draft.kind=="eid" {
        let profile=eid_profile(&draft.groups)?;
        let community=community_check(&profile);
        let mut groups=BTreeMap::new();
        for (number,value) in profile.values {
            if number==16 {continue;}
            groups.insert(format!("DG{number}"),match value {Group::Text(text)=>text,other=>serde_json::to_string(&other)?});
        }
        Ok(json!({"groups":groups,"active":profile.active,"community":community}))
    }else{
        ensure!(draft.kind=="epassport","Unsupported draft");
        let mut groups=draft.groups;
        let mrz=if let Some(raw)=groups.get("DG1") {
            let encoded=if raw.trim_start().starts_with('{'){cav4_local_prototype::personalization::form::mrz(&serde_json::from_str(raw)?)?}else{phoenix_eid_core::transport::unhex(raw)?};
            String::from_utf8(cav4_local_prototype::personalization::td3_mrz(&encoded)?.to_vec())?
        }else{String::new()};
        if let Some(raw)=groups.get("DG1").filter(|v|!v.trim_start().starts_with('{')) {
            let mrz=cav4_local_prototype::personalization::td3_mrz(&phoenix_eid_core::transport::unhex(raw)?)?;
            let text=std::str::from_utf8(&mrz)?;
            let names=text[5..44].split_once("<<").unwrap_or((&text[5..44],""));
            let clean=|s:&str|s.replace('<'," ").trim().to_owned();
            groups.insert("DG1".into(),json!({"surname":clean(names.0),"given_names":clean(names.1),"issuing_state":clean(&text[2..5]),"document_number":clean(&text[44..53]),"nationality":clean(&text[54..57]),"date_of_birth":&text[57..63],"sex":&text[64..65],"date_of_expiry":&text[65..71],"optional_data":clean(&text[72..86])}).to_string());
        }
        let portrait=match groups.get("DG2") {
            Some(raw)=>if let Some(image)=portrait_source(raw){Some(image.to_vec())}else{crate::passport_lds::parse_dg2(&phoenix_eid_core::transport::unhex(raw)?)?.into_iter().next().map(|face|face.image)},
            None=>None,
        };
        Ok(json!({"groups":groups,"mrz":mrz,"portrait":portrait.map(|bytes|openssl::base64::encode_block(&bytes))}))
    }
}
fn portrait_source(value:&str)->Option<&'static [u8]> {
    match value {
        "synthetic:erika"|"synthetic:picture-1"=>Some(include_bytes!("../../.private/simulator/GUI/assets/erika-mustermann-passfoto.jpg")),
        "synthetic:picture-2"=>Some(include_bytes!("../../.private/simulator/GUI/assets/synthetic-passport-person-2.jpg")),
        "synthetic:picture-3"=>Some(include_bytes!("../../.private/simulator/GUI/assets/synthetic-passport-person-3.jpg")),
        "synthetic:shadow-male"=>Some(include_bytes!("../../app/src/main/assets/portraits/portrait-male.jpg")),
        "synthetic:shadow-female"=>Some(include_bytes!("../../app/src/main/assets/portraits/portrait-female.jpg")),
        _=>None,
    }
}
fn passport_groups(groups:&BTreeMap<String,String>)->Result<BTreeMap<String,String>> {
    let mut result=BTreeMap::new();
    for (name,value) in groups {
        let number:u8=name.strip_prefix("DG").context("Invalid Data Group")?.parse()?;
        ensure!((1..=16).contains(&number),"Invalid passport Data Group");
        let encoded=if number==1 && value.trim_start().starts_with('{') {
            cav4_local_prototype::personalization::form::mrz(&serde_json::from_str(value)?)?
        }else if number==2 {
            match portrait_source(value) {
                Some(image)=>cav4_local_prototype::personalization::form::face(image,"iso19794-5")?,
                None=>phoenix_eid_core::transport::unhex(value)?,
            }
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
fn persosim_source(root:&Path,id:&str)->Result<std::path::PathBuf>{
    let (file,source)=fixture(id).context("Unknown PersoSim fixture")?;
    let path=root.join("test-fixtures/persosim-2b045789-v2/profiles").join(file);
    std::fs::create_dir_all(path.parent().unwrap())?;
    if !path.exists(){phoenix_eid_core::persistence::write_atomic(&path,source)?;}
    ensure!(std::fs::read(&path)?==source,"PersoSim test data changed");Ok(path)
}
fn profile_id(kind:&str,bytes:&[u8])->String{
    let digest=openssl::sha::sha256(bytes);
    let suffix=digest[..8].iter().map(|v|format!("{v:02x}")).collect::<String>();
    format!("{}{suffix}",if kind=="persosim"{"android_persosim_v2_"}else{"android_"})
}
#[derive(Deserialize,Serialize)]
#[serde(deny_unknown_fields)]
struct Binding {native_id:String, settings:Settings, generation:Option<String>}
fn binding_path(root:&Path,id:&str)->std::path::PathBuf {
    root.join("draft-bindings").join(format!("{}.bin",profile_id("eid",id.as_bytes())))
}
fn binding(root:&Path,id:&str)->Result<Option<Binding>> {
    let path=binding_path(root,id);
    if !path.try_exists()? {return Ok(None);}
    let config=config(root)?;
    let raw=Zeroizing::new(phoenix_eid_core::persistence::open(&std::fs::read(path)?,&config.passphrase)?);
    let value:Binding=serde_json::from_slice(phoenix_eid_core::ber::one(phoenix_eid_core::ber::one(&raw,0x30)?,4)?)?;
    pki_admin::identifier(&value.native_id)?;Ok(Some(value))
}
fn save_binding(root:&Path,id:&str,value:&Binding)->Result<()> {
    let path=binding_path(root,id);std::fs::create_dir_all(path.parent().unwrap())?;
    let config=config(root)?;
    let json=Zeroizing::new(serde_json::to_vec(value)?);
    let field=Zeroizing::new(phoenix_eid_core::ber::encode(4,&json)?);
    let raw=Zeroizing::new(phoenix_eid_core::ber::encode(0x30,&field)?);
    phoenix_eid_core::persistence::write_atomic(&path,&phoenix_eid_core::persistence::seal(&raw,&config.passphrase)?)
}
fn load_editor(root:&Path,id:&str)->Result<PersonalizedProfile> {
    let config=config(root)?;
    PersonalizedProfile::load(&config.profiles.join(id),&config.passphrase,&config.trusted_cscas,chrono::Utc::now().timestamp(),&[])
}
/// Stop the transport first; the core owns the committed PIN/counter state.
pub fn editor_state(root:&Path,id:&str)->Result<Value> {
    pki_admin::identifier(id)?;
    let Some(saved)=binding(root,id)? else{return Ok(json!({"mounted":false}));};
    let mut state=load_editor(root,&saved.native_id)?.editor_state()?;
    state["draft_settings"]=serde_json::to_value(&saved.settings)?;
    Ok(state)
}
pub fn ri_update(root:&Path,id:&str,frozen:bool,regenerate:bool,keys:Option<Value>)->Result<Value> {
    pki_admin::identifier(id)?;
    ensure!(!regenerate||keys.is_none(),"Choose RI import or regeneration");
    let saved=binding(root,id)?.context("Profil zuerst für die Simulation laden")?;
    let keys=if regenerate {Some(phoenix_eid_core::ri_personalization::RiKeys::generate()?)}else if let Some(value)=keys {
        ensure!(value.as_object().is_some_and(|v|v.len()==4),"RI import requires both private/public pairs");
        let read=|name:&str|->Result<Vec<u8>>{let raw=value[name].as_str().context("Missing RI key")?;ensure!(raw.len()<=8192,"RI input limit");phoenix_eid_core::transport::unhex(raw)};
        let keys=phoenix_eid_core::ri_personalization::RiKeys{ec_private:Zeroizing::new(read("ec_private")?),dh_private:Zeroizing::new(read("dh_private")?)};
        keys.validate_public(&read("ec_public")?,&read("dh_public")?)?;Some(keys)
    }else{None};
    load_editor(root,&saved.native_id)?.update_editor_state(None,Some(frozen),keys.as_ref())?;
    editor_state(root,id)
}
pub fn mount(native:&mut Native,root:&Path,value:Value,application:&mut Option<phoenix_eid_core::application::Application>)->Result<Value>{
    let bytes=serde_json::to_vec(&value)?;ensure!(bytes.len()<=1024*1024,"Draft limit");
    let mut legacy=value.clone();
    if let Some(fields)=legacy.as_object_mut(){fields.remove("baseline_groups");fields.remove("ri");if fields.get("pki_infrastructure").is_some_and(|v|v=="profile"){fields.remove("pki_infrastructure");}}
    let legacy_bytes=serde_json::to_vec(&legacy)?;
    if let Some(fields)=legacy.as_object_mut(){fields.remove("pki_infrastructure");}
    let legacy_unselected=serde_json::to_vec(&legacy)?;
    let draft:Draft=serde_json::from_value(value)?;
    ensure!(draft.format=="phoenix-android-draft-v1"&&["eid","epassport"].contains(&draft.kind.as_str()),"Unsupported draft");
    pki_admin::identifier(&draft.id)?;
    if let Some(generation)=&draft.personalization_generation {ensure!(generation.len()==36&&generation.bytes().all(|b|b.is_ascii_hexdigit()||b==b'-'),"Invalid personalization generation");}
    match draft.pki_infrastructure.as_deref() {
        None=>{},
        Some("profile")=>{},
        Some("bsi")=>ensure!(draft.kind=="eid","BSI-Testinfrastruktur ist nur für eID verfügbar"),
        Some("phoenix")=>anyhow::bail!("Zentrale Phoenix-PKI-Personalisierung ist noch nicht an den Android-Adapter angebunden"),
        _=>anyhow::bail!("Unsupported PKI infrastructure"),
    }
    if draft.kind=="epassport" {
        if let Some(original)=phoenix_simulator::passport_catalog::entry(&draft.id)? {
            ensure!(serde_json::to_value(&draft.groups)?==original["groups"],"Katalog-Datengruppen müssen unverändert bleiben");
            for (name,value) in [("can",&draft.settings.can),("pin",&draft.settings.pin),("puk",&draft.settings.puk)] {
                ensure!(value==original["profile"][name].as_str().unwrap_or(""),"Katalog-Zugangsdaten müssen unverändert bleiben");
            }
            ensure!(!draft.settings.transport_pin&&draft.settings.eid_active,"Ungültige ePassport-Einstellungen");
            *application=None;native.close();
            return native.mount_passport(&draft.id);
        }
    }
    ensure!(draft.settings.pin.len()==if draft.settings.transport_pin{5}else{6},"PIN-Länge passt nicht zum Transport-PIN-Modus");
    ensure!(draft.kind=="eid" || (!draft.settings.transport_pin && draft.settings.eid_active),"eID-Einstellungen sind nur für eID-Profile verfügbar");
    let mut source_id=draft.id.clone();
    if draft.pki_infrastructure.as_deref()==Some("bsi")&&fixture(&source_id).is_none(){
        if let Some(source)=&draft.persosim_source{ensure!(fixture(source).is_some(),"Unknown PersoSim source");source_id=source.clone();}
    }
    if draft.pki_infrastructure.as_deref()==Some("bsi")&&fixture(&source_id).is_none(){
        let source=eid_profile(&draft.groups)?;
        let catalog:Vec<Draft>=serde_json::from_str(include_str!("../../app/src/main/assets/persosim-profiles.json"))?;
        source_id=catalog.into_iter().find(|p|eid_profile(&p.groups).is_ok_and(|candidate|candidate.values==source.values)).context("PersoSim-Testsignaturen passen nicht zu diesen Datengruppen; ursprüngliche Personalisierungsdaten verwenden")?.id;
    }
    let signed=fixture(&source_id);
    if signed.is_some() {
        let catalog:Vec<Draft>=serde_json::from_str(include_str!("../../app/src/main/assets/persosim-profiles.json"))?;
        let original=catalog.into_iter().find(|p|p.id==source_id).context("Missing signed draft")?;
        ensure!(draft.kind=="eid"&&eid_profile(&draft.groups)?.values==eid_profile(&original.groups)?.values,"Signierte PersoSim-Datengruppen müssen unverändert bleiben; für eigene Daten ein separates Profil verwenden");
    }
    let previous=if draft.kind=="eid"{binding(root,&draft.id)?}else{None};
    let tag=if signed.is_some(){"persosim"}else{&draft.kind};
    let legacy_id=profile_id(tag,&legacy_bytes);
    let original_id=profile_id(tag,&legacy_unselected);
    let id=previous.as_ref().map(|b|b.native_id.clone()).unwrap_or_else(||{
        if draft.kind=="eid"&&root.join("profiles/eid").join(&legacy_id).is_dir(){legacy_id}
        else if fixture(&draft.id).is_some()&&root.join("profiles/eid").join(&original_id).is_dir(){original_id}
        else{profile_id(tag,&bytes)}
    });pki_admin::identifier(&id)?;
    *application=None;native.close();
    // Validate and encode before generating any local PKI or writing a profile.
    let eid=if draft.kind=="eid"{Some(eid_profile(&draft.groups)?)}else{None};
    let passport=if draft.kind=="epassport"{Some(passport_groups(&draft.groups)?)}else{None};
    pki(native,root).context("Prepare Android test PKI")?;
    if let Some(profile)=eid {
        if !root.join("profiles/eid").join(&id).exists(){
            let config=config(root)?;
            let Authority::Document(document)=pki_admin::load(&config.data,"android-document",&config.passphrase)? else{anyhow::bail!("Document test authority required")};
            let signer=document.document_signers().first().context("Document signer required")?;
            let terminal=pki_admin::load(&config.data,"android-terminal",&config.passphrase)?;
            let Authority::Terminal(terminal)=terminal else{anyhow::bail!("Terminal test authority required")};
            let anchor=if signed.is_some(){Some(governikus_anchor()?)}else{None};
            let credentials=CredentialState::new(draft.settings.pin.as_bytes(),draft.settings.can.as_bytes(),draft.settings.puk.as_bytes(),b"<<<<<<<<<<<<<<<<<<<<<<<<",draft.settings.pin_retries,draft.settings.puk_uses,draft.settings.eid_active,true)?;
            let baseline=if signed.is_none(){draft.baseline_groups.as_ref().map(eid_profile).transpose()?.unwrap_or_else(||profile.clone())}else{profile.clone()};
            profile_creation::create(&config.profiles.join(&id),&config.passphrase,&baseline,&credentials,
                &CreationPki{csca:document.certificate(),signer,cvca:anchor.as_ref().map(|a|a.key.as_slice()).unwrap_or(&terminal.root().certificate)},
                &CreationOptions{protocols:Default::default(),authenticated_pin_management:false,mobile_eid_type_info:None,time:chrono::Utc::now().timestamp(),card_date:None,cvca_lineage:vec![],cvca_is_lineage:vec![],cvca_public_key:None,cvca_anchor:anchor.clone(),cvca_authorization:None})?;
        }
        if signed.is_none() {
                let (old,_,_,_,_)=PersonalizedProfile::disclosure_source(&config(root)?.profiles.join(&id),&config(root)?.passphrase,chrono::Utc::now().timestamp()).context("Read existing eID personalization")?;
                if profile_changed(&old,&profile)? {
                    let config=config(root)?;
                    let Authority::Document(document)=pki_admin::load(&config.data,"android-document",&config.passphrase)? else{anyhow::bail!("Document authority required")};
                    phoenix_eid_core::profile_editor::ProfileEditor{profile}.save_existing(&config.profiles.join(&id),&config.passphrase,document.certificate(),document.document_signers().first().context("Signer required")?,chrono::Utc::now().timestamp())?;
                }
        }
        if let Some(previous)=&previous {
            if previous.settings!=draft.settings||previous.generation!=draft.personalization_generation {
                let credentials=CredentialState::new(draft.settings.pin.as_bytes(),draft.settings.can.as_bytes(),draft.settings.puk.as_bytes(),b"<<<<<<<<<<<<<<<<<<<<<<<<",draft.settings.pin_retries,draft.settings.puk_uses,draft.settings.eid_active,true)?;
                load_editor(root,&id)?.update_editor_state(Some(&credentials),None,None)?;
            }
        }
        if signed.is_none(){
        if previous.is_none() {
            if let Some(ri)=&draft.ri {
                let read=|name:&str|->Result<Vec<u8>>{let value=ri[name].as_str().context("Missing exported RI value")?;ensure!(value.len()<=16384,"RI export limit");phoenix_eid_core::transport::unhex(value)};
                let keys=phoenix_eid_core::ri_personalization::RiKeys{ec_private:Zeroizing::new(read("ec_private")?),dh_private:Zeroizing::new(read("dh_private")?)};
                keys.validate_public(&read("ec_public")?,&read("dh_public")?)?;
                load_editor(root,&id)?.import_ri(&keys,&read("policy")?)?;
            }
        }
        save_binding(root,&draft.id,&Binding{native_id:id.clone(),settings:draft.settings.clone(),generation:draft.personalization_generation.clone()})?;
            return native.mount(&id).context("Mount existing eID");
        }
        let label=format!("persosim:bsi-test:{}",signed.unwrap().0.trim_end_matches(".perso"));
        let config=config(root)?;
        if phoenix_eid_core::profile_info::ProfilePkiInfo::load(&config.profiles.join(&id))?.document_pki_id!=label {
            PersonalizedProfile::import_persosim(&config.profiles.join(&id),&persosim_source(root,&source_id)?,&config.passphrase,&config.trusted_cscas,chrono::Utc::now().timestamp(),&[],Some(&label),&[]).context("PersoSim profile import")?;
        }
        if previous.is_none() {
            if let Some(ri)=&draft.ri {
                let read=|name:&str|->Result<Vec<u8>>{let value=ri[name].as_str().context("Missing exported RI value")?;ensure!(value.len()<=16384,"RI export limit");phoenix_eid_core::transport::unhex(value)};
                let keys=phoenix_eid_core::ri_personalization::RiKeys{ec_private:Zeroizing::new(read("ec_private")?),dh_private:Zeroizing::new(read("dh_private")?)};
                keys.validate_public(&read("ec_public")?,&read("dh_public")?)?;
                load_editor(root,&id)?.import_ri(&keys,&read("policy")?)?;
            }
        }
        save_binding(root,&draft.id,&Binding{native_id:id.clone(),settings:draft.settings.clone(),generation:draft.personalization_generation.clone()})?;
        // The existing PersoSim-aware loader validates the whole authentication unit before use.
        let profile=PersonalizedProfile::load(&config.profiles.join(&id),&config.passphrase,&config.trusted_cscas,chrono::Utc::now().timestamp(),&[])?;
        *application=Some(std::sync::Arc::new(profile.into_card()?).application()?);
        Ok(json!({"connected":true,"profile":id,"backend":"phoenix-eid-core-rust"}))
    }else {
        if !root.join("profiles/epassport").join(&id).exists(){
            native.manage_passport_profile(serde_json::from_value(json!({"op":"personalize","id":id,"name":draft.name,"can":draft.settings.can,"data_groups":passport.unwrap(),"passive_authentication":{"mode":"classic","classic_authority":"android-document","pqc_authority":null,"pqc_signer":null}}))?)?;
        }
        native.mount_passport(&id).context("Mount ePassport")
    }
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn equivalent_dg_representations_do_not_repersonalize_a_loaded_card() {
        let before=EidProfile::default();let mut after=before.clone();
        for number in &before.active {after.values.insert(*number,phoenix_eid_core::profile::decode(*number,&before.encode(*number).unwrap()).unwrap());}
        assert!(!profile_changed(&before,&after).unwrap());
        after.values.insert(5,Group::Text("ANDERS".into()));assert!(profile_changed(&before,&after).unwrap());
    }
    #[test] fn municipality_audit_checks_every_supplied_eid_without_changing_signed_data() {
        let catalog:Vec<Draft>=serde_json::from_str(include_str!("../../app/src/main/assets/persosim-profiles.json")).unwrap();
        assert_eq!(catalog.len(),16);
        for draft in catalog {
            let before=serde_json::to_value(&draft.groups).unwrap();
            let profile=eid_profile(&draft.groups).unwrap();
            let check=community_check(&profile);
            println!("{} {}",draft.id,check);
            assert_eq!(before,serde_json::to_value(&draft.groups).unwrap());
        }
        let mut profile=EidProfile::default();
        let found=community_check(&profile);assert_eq!(found["expected"],"02761100000000");
        profile.values.insert(18,Group::Text(found["expected"].as_str().unwrap().into()));
        assert_eq!(community_check(&profile)["status"],"matching");
    }
    #[test] fn draft_bindings_use_the_authenticated_ber_envelope() {
        let json=br#"{"native_id":"android_example","settings":{"pin":"12345"}}"#;
        let raw=phoenix_eid_core::ber::encode(0x30,&phoenix_eid_core::ber::encode(4,json).unwrap()).unwrap();
        let encrypted=phoenix_eid_core::persistence::seal(&raw,b"test-local-binding").unwrap();
        let opened=phoenix_eid_core::persistence::open(&encrypted,b"test-local-binding").unwrap();
        assert_eq!(phoenix_eid_core::ber::one(phoenix_eid_core::ber::one(&opened,0x30).unwrap(),4).unwrap(),json);
        assert!(phoenix_eid_core::persistence::open(&encrypted,b"wrong-test-key").is_err());
    }
    #[test] fn eid_uses_governikus_public_pin_and_invalidates_old_native_cache(){
        let anchor=governikus_anchor().unwrap();assert_eq!(anchor.reference,b"DETESTeID00009");assert!(anchor.oid.is_none());assert!(!anchor.key.is_empty());
        let bytes=b"same draft";assert_ne!(profile_id("persosim",bytes),profile_id("epassport",bytes));assert!(profile_id("persosim",bytes).starts_with("android_persosim_v2_"));
    }
    #[test] fn fixture_preserves_signed_files_data_and_ca_keys(){
        let root=Path::new(env!("CARGO_MANIFEST_DIR")).join("../.private");
        assert_eq!(persosim_sources::SOURCES.len(),16);
        assert!(fixture("eid-persosimgt").is_none());
        let catalog:Vec<Draft>=serde_json::from_str(include_str!("../../app/src/main/assets/persosim-profiles.json")).unwrap();
        for (id,file,_) in persosim_sources::SOURCES {
        let original=phoenix_eid_core::persosim_profile::parse(&root.join("persosim/de.persosim.simulator/personalization/profiles").join(file)).unwrap();
        let adapted=phoenix_eid_core::persosim_profile::parse(&root.join("persosim-profiles").join(file)).unwrap();
        let draft=catalog.iter().find(|draft|draft.id==*id).unwrap();
        assert_eq!(draft.groups.len(),original.eid_files.len());
        for (fid,raw) in &original.eid_files {
            let number=(fid-0x100) as u8;
            assert_eq!(draft.groups[&format!("DG{number}")],serde_json::to_string(&phoenix_eid_core::profile::decode(number,raw).unwrap()).unwrap());
        }
        assert_eq!(original.card_access,adapted.card_access);assert_eq!(original.card_security,adapted.card_security);assert_eq!(original.chip_security,adapted.chip_security);assert_eq!(original.eid_files,adapted.eid_files);
        assert_eq!(original.ca_keys.len(),adapted.ca_keys.len());
        for (a,b) in original.ca_keys.iter().zip(&adapted.ca_keys){assert!(a.private_scalar==b.private_scalar);assert_eq!(a.public_point,b.public_point);assert_eq!(a.key_id,b.key_id);assert_eq!(a.parameter_id,b.parameter_id);assert_eq!(a.privileged,b.privileged);}
        assert!(adapted.cvca_certificate.is_none());assert!(adapted.approximate_date.is_none());
        }
    }
    #[test] fn editor_description_uses_effective_core_values_without_mounting_a_card(){
        let draft=json!({"format":"phoenix-android-draft-v1","id":"test","kind":"eid","name":"Test","groups":{"DG4":"MAX","DG5":"MUSTERMANN","DG8":"19900101"},"settings":{"can":"123456","pin":"123456","puk":"1234567890"}});
        let result=describe(draft).unwrap();
        assert_eq!(result["groups"]["DG1"],"ID");assert_eq!(result["groups"]["DG4"],"MAX");assert_eq!(result["groups"]["DG8"],"19900101");assert!(result["groups"].get("DG16").is_none());
    }
    #[test] fn catalogue_description_reads_original_mrz_and_rejects_modified_signed_groups(){
        let all=phoenix_simulator::passport_catalog::drafts().unwrap();
        let mut draft=all["profiles"].as_array().unwrap().iter().find(|p|p["id"]=="phoenix-deu-mara-linden").unwrap().clone();
        for field in ["catalog_sha256","simulatable","simulation_error"]{draft.as_object_mut().unwrap().remove(field);}
        let original=draft["groups"].clone();
        let result=describe(draft.clone()).unwrap();
        let mrz:Value=serde_json::from_str(result["groups"]["DG1"].as_str().unwrap()).unwrap();
        assert_eq!(mrz["surname"],"LINDEN");assert_eq!(mrz["given_names"],"MARA");
        let original_mrz=cav4_local_prototype::personalization::td3_mrz(&phoenix_eid_core::transport::unhex(draft["groups"]["DG1"].as_str().unwrap()).unwrap()).unwrap();
        assert_eq!(result["mrz"].as_str().unwrap().as_bytes(),original_mrz);
        assert_eq!(original_mrz.len(),88);
        assert_eq!(draft["groups"],original);
        draft["groups"]["DG1"]=json!("tampered");
        let mut native=Native::new(Config::default());let mut application=None;
        assert!(mount(&mut native,Path::new("unused"),draft,&mut application).unwrap_err().to_string().contains("unverändert"));
    }
    #[test] fn malformed_encoded_dg_and_mrz_fail_before_personalization(){assert!(passport_groups(&BTreeMap::from([("DG2".into(),"zz".into())])).is_err());assert!(passport_groups(&BTreeMap::from([("DG1".into(),"{}".into())])).is_err());}
    #[test] fn eid_values_use_the_original_core_validation(){assert!(eid_profile(&BTreeMap::from([("DG8".into(),"not-a-date".into())])).is_err());let profile=eid_profile(&BTreeMap::from([("DG4".into(),"MAX".into()),("DG5".into(),"MUSTERMANN".into())])).unwrap();assert_eq!(profile.values[&4],Group::Text("MAX".into()));}
}
