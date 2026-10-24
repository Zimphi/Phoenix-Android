//! Background transport state for the existing trial relay. No renewal or signing keys.
use anyhow::{ensure, Context, Result};
use phoenix_license_native::legacy_activation::LegacyActivation;
use serde_json::{json, Value};
use std::{fs, io::Write, path::Path};

fn load(root:&Path)->Result<LegacyActivation>{
    let path=root.join("trial-state.json");let metadata=fs::symlink_metadata(&path)?;
    ensure!(metadata.is_file()&&!metadata.file_type().is_symlink()&&metadata.len()<=65536,"Invalid activation state");
    Ok(serde_json::from_slice(&fs::read(path)?)?)
}
fn publish_initial(source:&Path,target:&Path)->Result<()> {
    #[cfg(target_os="android")]
    {
        // Android app storage forbids hard links; preserve atomic no-replace semantics.
        let source=std::ffi::CString::new(source.as_os_str().as_encoded_bytes())?;
        let target=std::ffi::CString::new(target.as_os_str().as_encoded_bytes())?;
        let result=unsafe{libc::syscall(libc::SYS_renameat2,libc::AT_FDCWD,source.as_ptr(),libc::AT_FDCWD,target.as_ptr(),libc::RENAME_NOREPLACE)};
        if result!=0 {return Err(std::io::Error::last_os_error().into())}
    }
    #[cfg(not(target_os="android"))]
    {fs::hard_link(source,target)?;fs::remove_file(source)?;}
    Ok(())
}
fn save(root:&Path,state:&LegacyActivation,initial:bool)->Result<()> {
    let bytes=serde_json::to_vec(state)?;ensure!(bytes.len()<=65536,"Activation state limit");
    use std::os::unix::fs::OpenOptionsExt;
    let path=root.join("trial-state.next");
    let mut file=fs::OpenOptions::new().write(true).create(true).truncate(true).custom_flags(libc::O_NOFOLLOW).mode(0o600).open(&path)?;
    file.write_all(&bytes)?;file.sync_all()?;
    if initial {publish_initial(&path,&root.join("trial-state.json"))?;}
    else {fs::rename(&path,root.join("trial-state.json"))?;}
    Ok(())
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn first_request_publication_is_atomic_and_cannot_replace_existing_state(){
        let root=std::env::temp_dir().join(format!("phoenix-activation-{}",uuid().unwrap()));fs::create_dir(&root).unwrap();
        let source=root.join("next");let target=root.join("state");
        fs::write(&source,b"first").unwrap();publish_initial(&source,&target).unwrap();assert!(!source.exists());
        fs::write(&source,b"replacement").unwrap();assert!(publish_initial(&source,&target).is_err());assert_eq!(fs::read(&target).unwrap(),b"first");
        fs::remove_dir_all(root).unwrap();
    }
}
fn uuid()->Result<String>{
    let mut bytes=[0;16];openssl::rand::rand_bytes(&mut bytes)?;bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
    let h=bytes.iter().map(|b|format!("{b:02x}")).collect::<String>();
    Ok(format!("{}-{}-{}-{}-{}",&h[..8],&h[8..12],&h[12..16],&h[16..20],&h[20..]))
}
fn prepare_products(root:&Path,state_root:&Path,products:&[&str],installed:&[&str])->Result<Value>{
    fs::create_dir_all(state_root)?;
    let state=if state_root.join("trial-state.json").exists(){load(state_root)?}else{
        for product in installed {
            ensure!(!root.join(product).exists(),"Automatic trial is first-start only");
        }
        let device=phoenix_license_native::device_ids().context("Native device identity")?.into_iter().next().context("Device binding missing")?;
        let (lower,_)=phoenix_license_native::trusted_time_bounds().context("Authenticated time initialization")?;
        let created=chrono::DateTime::from_timestamp((lower/1000).try_into()?,0).context("Trusted activation time")?.to_rfc3339_opts(chrono::SecondsFormat::Secs,true);
        let products=products.iter().map(|product|Ok(json!({"product":product,"request_id":uuid()?}))).collect::<Result<Vec<_>>>()?;
        let batch=json!({"type":"phoenix-license-request","version":2,"request_id":uuid()?,"created_at":created,"customer_reference":{"anonymous":true},"device_id":device,"products":products,"license_type":"trial"});
        let state=LegacyActivation::import(batch).context("Prepare trial request")?;save(state_root,&state,true).context("Persist trial request")?;state
    };
    if state.summary()?["license_received"]==true {state.activate()?;Ok(json!({"active":true}))}
    else {Ok(json!({"active":false,"request":state.trial_hash_request()?}))}
}
pub fn prepare(root:&Path)->Result<Value>{prepare_products(root,root,&["phoenix-core","phoenix-eid"],&["Phoenix-ePassport-Core","Phoenix-eID"])}
pub fn receive(root:&Path,delivery:Value)->Result<Value>{
    let mut state=load(root)?;
    state.receive(delivery)?;
    save(root,&state,false)?;
    state.activate()
}

pub fn prepare_terminal(root:&Path)->Result<Value>{
    if [0x101,0x201,0x401].into_iter().all(phoenix_license_native::authorize_operation){return Ok(json!({"active":true}));}
    prepare_products(root,&root.join("terminal-activation"),&["phoenix-is-terminal"],&["Phoenix-IS-Terminal"])
}
pub fn receive_terminal(root:&Path,delivery:Value)->Result<Value>{receive(&root.join("terminal-activation"),delivery)}
