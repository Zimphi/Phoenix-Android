//! Android lifecycle and JNI only; card protocols are provided by the original host/cores.
use anyhow::{ensure, Context, Result};
use jni::{objects::{JByteArray, JClass, JObject, JString}, sys::{jbyteArray, jstring}, JNIEnv};
use phoenix_simulator::runtime::{Config, Native};
use serde::Deserialize;
use serde_json::{json, Value};
use std::{fs, io::Write, path::Path, sync::{Mutex, OnceLock}};
use zeroize::Zeroizing;

mod activation;
mod remote;
mod remote_identity;
static READER: Mutex<Option<remote::Reader>> = Mutex::new(None);
mod provision;
static ROOT: OnceLock<std::path::PathBuf> = OnceLock::new();
static RUNTIME: OnceLock<Mutex<(Native,Option<phoenix_eid_core::application::Application>)>> = OnceLock::new();
#[derive(Deserialize)]
#[serde(tag="op", rename_all="snake_case", deny_unknown_fields)]
enum Request {
    Inventory,
    IfdStart {draft:Value, pair:bool},
    IfdPair,
    IfdForget,
    IfdStatus,
    IfdStop,
    TrialPrepare,
    TrialReceive {delivery:Value},
    Mount {kind:String, id:String},
    MountDraft {draft:Value},
    Apdu {command:String},
    Reset,
    Close,
    EidEditor {request:Value},
    PassportEditor {request:Value},
}
fn dispatch(request: &str) -> Result<Value> {
    ensure!(request.len() <= 32*1024*1024,"Request size limit");
    let request:Request=serde_json::from_str(request)?;
    if matches!(request,Request::IfdStatus|Request::IfdStop|Request::IfdForget) {
        let mut reader=READER.lock().map_err(|_|anyhow::anyhow!("IFD lock"))?;
        return match request {
            Request::IfdStatus=>reader.as_ref().map(|r|r.status()).unwrap_or_else(||Ok(json!({"running":false,"paired":ROOT.get().map(|root|remote::stored_peers(root)).transpose()?.unwrap_or(0)}))),
            _=>{if let Some(mut reader)=reader.take(){reader.stop()?;}if matches!(request,Request::IfdForget){remote::forget_peers(ROOT.get().context("App root missing")?)?;}Ok(json!({"running":false}))}
        };
    }
    if !matches!(request,Request::IfdStart{..}|Request::IfdPair|Request::Inventory|Request::TrialPrepare|Request::TrialReceive{..}) {
        ensure!(READER.lock().map_err(|_|anyhow::anyhow!("IFD lock"))?.is_none(),"Zuerst den WLAN-Kartenleser beenden");
    }
    let mut native=RUNTIME.get().context("Native Laufzeit nicht initialisiert")?.lock().map_err(|_|anyhow::anyhow!("Core-Zustand nicht verfügbar"))?;
    let (native,application)=&mut *native;
    match request {
        Request::IfdStatus|Request::IfdStop|Request::IfdForget=>unreachable!(),
        Request::IfdPair=>{
            let root=ROOT.get().context("App root missing")?;
            let mut reader=READER.lock().map_err(|_|anyhow::anyhow!("IFD lock"))?;
            if let Some(mut previous)=reader.take(){previous.stop()?;}
            *application=None;native.close();
            let next=remote::Reader::start(root,None,true)?;
            let status=next.status()?;*reader=Some(next);Ok(status)
        },
        Request::IfdStart{draft,pair}=>{
            ensure!(draft["kind"]=="eid","Remote IFD unterstützt eID-Profile");
            let root=ROOT.get().context("App root missing")?;
            let mut reader=READER.lock().map_err(|_|anyhow::anyhow!("IFD lock"))?;
            if let Some(mut previous)=reader.take(){previous.stop()?;}
            let mounted=provision::mount(native,root,draft,application)?;
            let id=mounted["profile"].as_str().context("Mounted profile missing")?;
            *application=None;native.close();
            let next=remote::Reader::start(root,Some(remote::load_card(root,id)?),pair)?;
            let status=next.status()?;*reader=Some(next);Ok(status)
        },
        Request::TrialPrepare => activation::prepare(ROOT.get().context("App root missing")?),
        Request::TrialReceive {delivery} => activation::receive(ROOT.get().context("App root missing")?,delivery),
        Request::Inventory => Ok(json!({"eid":native.inventory()?,"epassport":native.passport_inventory()?})),
        Request::MountDraft {draft} => provision::mount(native,ROOT.get().context("App root missing")?,draft,application),
        Request::Mount {kind,id} => { *application=None;native.close();match kind.as_str(){"eid"=>native.mount(&id),"epassport"=>native.mount_passport(&id),_=>anyhow::bail!("Core nicht verfügbar")}},
        Request::Apdu {command} => if let Some(card)=application.as_mut(){
            ensure!(command.len()<=phoenix_eid_core::transport::MAX_APDU*2+4096,"APDU zu groß");
            let result=card.transmit(&phoenix_eid_core::transport::unhex(&command)?);
            match result {Ok(response)=>Ok(json!({"response":phoenix_simulator::runtime::hex(&response)})),Err(error)=>{*application=None;Err(error)}}
        }else{native.apdu(&command)},
        Request::Reset => if let Some(card)=application.as_mut(){
            if let Err(error)=card.reset(){*application=None;return Err(error)}
            Ok(json!({"connected":true}))
        }else{native.reset()},
        Request::Close => {*application=None;Ok(native.close())},
        Request::EidEditor {request} => native.manage_profile(serde_json::from_value(request)?),
        Request::PassportEditor {request} => native.manage_passport_profile(serde_json::from_value(request)?),
    }
}
fn output(env:&mut JNIEnv, operation:impl FnOnce()->Result<Value>)->jstring {
    let result=std::panic::catch_unwind(std::panic::AssertUnwindSafe(operation));
    let value=match result {Ok(Ok(value))=>value,Ok(Err(error))=>json!({"error":format!("{error:#}")}),Err(_)=>json!({"error":"Native Kartenoperation abgebrochen"})};
    match env.new_string(value.to_string()){Ok(text)=>text.into_raw(),Err(_)=>std::ptr::null_mut()}
}
#[no_mangle]
pub extern "system" fn Java_de_phoenix_simulator_NativeCore_exchange(mut env:JNIEnv,_class:JClass,request:JString)->jstring {
    let request=env.get_string(&request).map(|s|s.to_string_lossy().into_owned());
    output(&mut env,||dispatch(&request?))
}
#[cfg(target_os="android")]
#[no_mangle]
pub extern "system" fn Java_de_phoenix_simulator_NativeCore_challenge(mut env:JNIEnv,_class:JClass)->jbyteArray {
    match std::panic::catch_unwind(phoenix_license_native::android::challenge) {
        Ok(Ok(bytes))=>match env.byte_array_from_slice(&bytes){Ok(array)=>array.into_raw(),Err(_)=>std::ptr::null_mut()},
        _=>{let _=env.throw_new("java/lang/IllegalStateException","Gerätebindung konnte nicht vorbereitet werden");std::ptr::null_mut()}
    }
}
#[cfg(target_os="android")]
#[no_mangle]
pub extern "system" fn Java_de_phoenix_simulator_NativeCore_initializeNative(mut env:JNIEnv,_class:JClass,context:JObject,public:JByteArray,proof:JByteArray,root:JString)->jstring {
    // Android TLS needs the JVM helper before the first native NTS query.
    let setup=(||->Result<String>{
        rustls_platform_verifier::android::init_with_env(&mut env,context)?;
        ensure!(env.get_array_length(&public)? <=512 && env.get_array_length(&proof)?<=128,"Key proof size");
        let root=env.get_string(&root)?.to_string_lossy().into_owned();
        phoenix_license_native::android::initialize(&env.convert_byte_array(&public)?,&env.convert_byte_array(&proof)?,Path::new(&root))?;
        Ok(root)
    })();
    output(&mut env,||{
        let root=setup?;let root=Path::new(&root);let path=root.join("profile-passphrase");
        let secret=if path.exists(){Zeroizing::new(fs::read(&path)?)}else{
            let mut bytes=Zeroizing::new(vec![0;32]);openssl::rand::rand_bytes(&mut bytes)?;
            use std::os::unix::fs::OpenOptionsExt;
            let mut file=fs::OpenOptions::new().write(true).create_new(true).mode(0o600).open(&path)?;file.write_all(&bytes)?;file.sync_all()?;bytes
        };
        ensure!(secret.len()==32,"Ungültiger lokaler Profilschlüssel");
        ROOT.set(root.to_path_buf()).map_err(|_|anyhow::anyhow!("App context already initialized"))?;
        let config=Config {data:root.join("runtime"),profiles:root.join("profiles/eid"),passport_profiles:root.join("profiles/epassport"),passphrase:secret,..Config::default()};
        if RUNTIME.set(Mutex::new((Native::new(config),None))).is_err(){anyhow::bail!("Native Laufzeit bereits initialisiert")}
        Ok(json!({"initialized":true}))
    })
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn rejects_unknown_core_and_unknown_request_fields(){assert!(serde_json::from_str::<Request>(r#"{"op":"mount","kind":"eid","id":"x","owner":true}"#).is_err());assert!(serde_json::from_str::<Request>(r#"{"op":"owner"}"#).is_err());}
    #[test] fn cannot_process_apdus_without_initialized_native_runtime(){assert!(dispatch(r#"{"op":"apdu","command":"00a40400"}"#).is_err());}
}
