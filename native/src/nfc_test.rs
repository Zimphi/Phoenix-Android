//! Isolated synthetic test cards and the unchanged licensed IS-Terminal over JNI IsoDep.
use anyhow::{ensure, Context, Result};
use jni::{objects::{JByteArray,JClass,JObject,JString,JValue},sys::{jbyteArray,jstring},JNIEnv};
use phoenix_simulator::{runtime::Native,pki_admin};
use phoenix_eid_core::pki::store::Authority;
use crate::{terminal::PaceSession,inspection::{TerminalCredentials,InspectionRequest,EidInspectionRequest,inspect},passive_authentication::PaProfile};
use serde::Deserialize;
use serde_json::{json,Value};
use std::{fs,path::{Path,PathBuf},sync::Mutex};
use zeroize::{Zeroizing,Zeroize};

struct Card {root:PathBuf,native:Native,application:Option<phoenix_eid_core::application::Application>}
impl Drop for Card {fn drop(&mut self){self.application=None;self.native.close();let _=fs::remove_dir_all(&self.root);}}
static CARD:Mutex<Option<Card>>=Mutex::new(None);
fn hex(data:&[u8])->String{phoenix_simulator::runtime::hex(data)}
fn unhex(text:&str)->Result<Vec<u8>>{phoenix_eid_core::transport::unhex(text)}
// One synthetic RAW pixel exercises protected DG3 access, not biometric scanner quality.
fn diagnostic_dg3()->Result<Vec<u8>>{unhex(include_str!("../fixtures/nfc-test-dg3.hex").trim())}
fn random_passphrase(bytes:usize)->Result<Zeroizing<Vec<u8>>>{
    let mut random=Zeroizing::new(vec![0;bytes]);openssl::rand::rand_bytes(&mut random)?;
    Ok(Zeroizing::new(hex(&random).into_bytes()))
}

fn prepare(kind:&str)->Result<Value>{
    ensure!(matches!(kind,"eid"|"epassport"),"Unknown diagnostic application");
    crate::terminal::require_inspection_license()?;
    let base=crate::ROOT.get().context("Initialize native runtime first")?;
    let mut random=[0;16];openssl::rand::rand_bytes(&mut random)?;
    let root=base.join(format!("nfc-test-{}",hex(&random)));
    fs::create_dir(&root)?;
    use std::os::unix::fs::{PermissionsExt,OpenOptionsExt};
    fs::set_permissions(&root,fs::Permissions::from_mode(0o700))?;
    let password=random_passphrase(16)?;
    std::io::Write::write_all(&mut fs::OpenOptions::new().write(true).create_new(true).mode(0o600).open(root.join("profile-passphrase"))?,&password)?;
    let native=Native::new(crate::provision::config(&root)?);
    let mut card=Card{root,native,application:None};
    let groups=if kind=="eid" {json!({"DG4":"PHOENIX","DG5":"NFC TEST","DG8":"19900101","DG18":"02761100000000"})}else{
        let mrz=json!({"surname":"MUSTERMANN","given_names":"ERIKA","issuing_state":"D<<","nationality":"D<<","document_number":"TEST00001","date_of_birth":"640812","date_of_expiry":"301020","sex":"F","optional_data":""});
        json!({"DG1":mrz.to_string(),"DG2":"synthetic:erika","DG3":hex(&diagnostic_dg3()?)})
    };
    let draft=json!({"format":"phoenix-android-draft-v1","id":"nfc-diagnostic","kind":kind,"name":"Synthetic NFC diagnostic","groups":groups,"settings":{"can":"123456","pin":"123456","puk":"1234567890"},"pki_infrastructure":"profile"});
    crate::provision::mount(&mut card.native,&card.root,draft,&mut card.application)?;
    let config=crate::provision::config(&card.root)?;
    let Authority::Document(document)=pki_admin::load(&config.data,"android-document",&config.passphrase)? else{anyhow::bail!("Diagnostic CSCA missing")};
    let authority=if kind=="eid"{"android-terminal"}else{"android-passport-terminal"};
    let Authority::Terminal(mut terminal)=pki_admin::load(&config.data,authority,&config.passphrase)? else{anyhow::bail!("Diagnostic CVCA missing")};
    let today=chrono::Utc::now().date_naive();
    terminal.issue_document_verifier(None,true,None,30,today)?;
    terminal.issue_terminal(0,None,None,14,today,std::collections::BTreeMap::new())?;
    let leaf=&terminal.terminals()[0];
    let chain=[terminal.root().certificate.clone(),terminal.document_verifiers()[0].certificate.clone(),leaf.certificate.clone()];
    // OpenSSL's PEM writer accepts a C string, so random passphrases must be NUL-free.
    let pem_password=random_passphrase(32)?;
    let key=leaf.private_key.private_key_to_pem_pkcs8_passphrase(openssl::symm::Cipher::aes_256_cbc(),&pem_password)?;
    let crl=document.issue_crl(1,chrono::Utc::now().timestamp())?.to_der()?;
    let material=json!({"kind":kind,"anchor":hex(&document.certificate().to_der()?),"crl":hex(&crl),"chain":chain.iter().map(|c|hex(c)).collect::<Vec<_>>(),"key":hex(&key),"password":hex(&pem_password)});
    ensure!(serde_json::to_vec(&material)?.len()<=24576,"Diagnostic credentials limit");
    let mut holder=CARD.lock().map_err(|_|anyhow::anyhow!("Diagnostic card lock"))?;
    *holder=Some(card);
    Ok(material)
}
#[derive(Deserialize)]
#[serde(deny_unknown_fields)]
struct Material {kind:String,anchor:String,crl:String,chain:Vec<String>,key:String,password:String}
impl Drop for Material {fn drop(&mut self){self.key.zeroize();self.password.zeroize();}}
fn inspect_card(material:&str,exchange:&mut impl FnMut(&[u8])->Result<Vec<u8>>)->Result<Value>{
    ensure!(material.len()<=24576,"Diagnostic material limit");
    let m:Material=serde_json::from_str(material)?;
    ensure!(matches!(m.kind.as_str(),"eid"|"epassport")&&m.chain.len()==3,"Invalid diagnostic material");
    crate::terminal::require_inspection_license()?;
    let anchor=unhex(&m.anchor)?;let anchors=vec![anchor];let crls=vec![unhex(&m.crl)?];let chain=m.chain.iter().map(|c|unhex(c)).collect::<Result<Vec<_>>>()?;
    let key=Zeroizing::new(unhex(&m.key)?);let password=Zeroizing::new(unhex(&m.password)?);
    ensure!(key.len()<=8192&&password.len()==64&&password.iter().all(u8::is_ascii_hexdigit),"Diagnostic key limit");
    let mut random=[0;16];openssl::rand::rand_bytes(&mut random)?;
    let path=crate::ROOT.get().context("Native root missing")?.join(format!("nfc-terminal-{}.pem",hex(&random)));
    struct Cleanup(PathBuf);impl Drop for Cleanup {fn drop(&mut self){let _=fs::remove_file(&self.0);}}
    let cleanup=Cleanup(path);
    use std::os::unix::fs::OpenOptionsExt;
    std::io::Write::write_all(&mut fs::OpenOptions::new().write(true).create_new(true).mode(0o600).open(&cleanup.0)?,&key)?;
    let aid=unhex(if m.kind=="eid"{"E80704007F00070302"}else{"A0000002471001"})?;
    let plain=[vec![0,0xa4,4,0x0c,aid.len() as u8],aid.clone()].concat();
    ensure!(exchange(&plain)?==[0x90,0],"NFC application selection failed");
    let mut session=PaceSession::connect_auto(b"123456",2,true,exchange)?;
    let result=(||->Result<Value>{
        // Verify an authenticated control response before the selection regression probe.
        let command=session.protect_command(&[0,0x84,0,0,8])?;
        let response=exchange(&command)?;
        let (_,status)=session.unprotect_response(&response)?;
        ensure!(matches!(status,0x9000|0x6985),"Unexpected protected control response");
        let selected=session.protect_command(&plain)?;
        let response=exchange(&selected)?;
        let (data,status)=session.unprotect_response(&response)?;
        ensure!(status==0x9000&&data.is_empty(),"Protected application selection failed");
        let protected_fid=if m.kind=="eid"{4}else{3};
        let command=session.protect_command(&[0,0xa4,2,0x0c,2,1,protected_fid])?;
        let response=exchange(&command)?;
        let (_,mut denied)=session.unprotect_response(&response)?;
        if denied==0x9000 {
            let command=session.protect_command(&[0,0xb0,0,0,32])?;
            let response=exchange(&command)?;denied=session.unprotect_response(&response)?.1;
        }
        ensure!(matches!(denied,0x6982|0x6985),"Protected data readable before TA");
        let credentials=TerminalCredentials{chain:&chain,key_path:&cleanup.0,password:&password};
        let groups=if m.kind=="eid" {
            // The pinned synthetic eID profile advertises ECDH-AES-256 CA2 at key reference 1.
            let report=session.inspect_eid(&EidInspectionRequest{anchors:&anchors,crls:&crls,ca:("0.4.0.127.0.7.2.2.3.2.4",Some(1)),ta:credentials,data_groups:&[4,5,8]},exchange)?;
            ensure!(report.groups.keys().copied().collect::<Vec<_>>()==[4,5,8]&&session.chip_authenticated(),"Incomplete eID session");
            report.groups.keys().copied().collect::<Vec<_>>()
        }else{
            let report=inspect(&mut session,&InspectionRequest{pa_profile:PaProfile::Single,anchors:&anchors,crls:&crls,ca:Some(("0.4.0.127.0.7.2.2.3.2.4",Some(1))),ca_icc:None,ta_icc:None,ta:Some(credentials),active_authentication:false},exchange)?;
            ensure!(report.complete()&&report.authentic()&&report.chip_authenticated&&!report.terminal_authentication_failed&&report.groups.contains_key(&3),
                "Incomplete ePassport session: missing={:?}, CA={}, TA_failed={}, TA_error={}",report.pa.missing_dgs,report.chip_authenticated,report.terminal_authentication_failed,report.terminal_authentication_error.as_deref().unwrap_or("none"));
            report.groups.keys().copied().collect::<Vec<_>>()
        };
        Ok(json!({"kind":m.kind.clone(),"status":"pass","checks":["PACE","Secure Messaging","PA","CA","TA","authorized_DG_read","unauthorized_read_rejected","authenticated_SELECT"],"data_groups":groups}))
    })();
    session.close();result
}
#[no_mangle]
pub extern "system" fn Java_de_phoenix_simulator_NfcTestNative_prepare(mut env:JNIEnv,_class:JClass,kind:JString)->jstring {
    let kind=env.get_string(&kind).map(|s|s.to_string_lossy().into_owned());
    crate::output(&mut env,||prepare(&kind?))
}
#[no_mangle]
pub extern "system" fn Java_de_phoenix_simulator_NfcTestNative_transmit(mut env:JNIEnv,_class:JClass,input:JByteArray)->jbyteArray {
    let result=std::panic::catch_unwind(std::panic::AssertUnwindSafe(||->Result<Vec<u8>>{
        ensure!(env.get_array_length(&input)?<=65535,"APDU size");let command=env.convert_byte_array(&input)?;
        let mut card=CARD.lock().map_err(|_|anyhow::anyhow!("Diagnostic card lock"))?;let card=card.as_mut().context("Diagnostic card inactive")?;
        if let Some(application)=card.application.as_mut(){application.transmit(&command)}else{
            let response=card.native.apdu(&hex(&command))?;unhex(response["response"].as_str().context("APDU response missing")?)
        }
    }));
    let bytes=match result{Ok(Ok(bytes))=>bytes,_=>vec![0x6f,0]};
    env.byte_array_from_slice(&bytes).map(|a|a.into_raw()).unwrap_or(std::ptr::null_mut())
}
#[no_mangle]
pub extern "system" fn Java_de_phoenix_simulator_NfcTestNative_close(_env:JNIEnv,_class:JClass){if let Ok(mut card)=CARD.lock(){*card=None;}}
#[no_mangle]
pub extern "system" fn Java_de_phoenix_simulator_NfcTestNative_reset(_env:JNIEnv,_class:JClass){if let Ok(mut holder)=CARD.lock(){if let Some(card)=holder.as_mut(){if let Some(application)=card.application.as_mut(){let _=application.reset();}else{let _=card.native.reset();}}}}
#[no_mangle]
pub extern "system" fn Java_de_phoenix_simulator_NfcTestNative_inspect(mut env:JNIEnv,_class:JClass,material:JString,transport:JObject)->jstring {
    let result=std::panic::catch_unwind(std::panic::AssertUnwindSafe(||->Result<Value>{
        let material=env.get_string(&material)?.to_string_lossy().into_owned();
        inspect_card(&material,&mut |command|{
            ensure!(command.len()<=65535,"APDU size");
            env.with_local_frame(8,|env|->Result<Vec<u8>>{
                let array=env.byte_array_from_slice(command)?;let object=JObject::from(array);
                let response=env.call_method(&transport,"exchange","([B)[B",&[JValue::Object(&object)])?.l()?;
                let response=JByteArray::from(response);ensure!(env.get_array_length(&response)?<=65535,"Response size");env.convert_byte_array(response).map_err(Into::into)
            })
        })
    })).unwrap_or_else(|_|Err(anyhow::anyhow!("Diagnostic session aborted")));
    if env.exception_check().unwrap_or(false){let _=env.exception_clear();}
    crate::output(&mut env,||result)
}
#[cfg(test)] mod tests {
    use super::*;
    #[test]fn untrusted_material_rejected_before_transport(){let mut called=false;assert!(inspect_card("{\"kind\":\"x\"}",&mut |_|{called=true;Ok(vec![])}).is_err());assert!(!called);}
    #[test]fn synthetic_dg3_passes_original_terminal_validation(){crate::diagnostics::biometric(&diagnostic_dg3().unwrap(),3).unwrap();}
    #[test]fn generated_pem_passphrase_roundtrips_without_nul(){
        let key=openssl::pkey::PKey::from_ec_key(openssl::ec::EcKey::generate(&openssl::ec::EcGroup::from_curve_name(openssl::nid::Nid::X9_62_PRIME256V1).unwrap()).unwrap()).unwrap();
        for size in [16,32]{for _ in 0..32{
            let password=random_passphrase(size).unwrap();assert_eq!(password.len(),size*2);assert!(password.iter().all(u8::is_ascii_hexdigit));
            let pem=key.private_key_to_pem_pkcs8_passphrase(openssl::symm::Cipher::aes_256_cbc(),&password).unwrap();
            let decoded=openssl::pkey::PKey::private_key_from_pem_passphrase(&pem,&password).unwrap();assert!(key.public_eq(&decoded));
        }}
    }
}
