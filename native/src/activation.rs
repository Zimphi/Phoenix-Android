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
fn save(root:&Path,state:&LegacyActivation,initial:bool)->Result<()> {
    let bytes=serde_json::to_vec(state)?;ensure!(bytes.len()<=65536,"Activation state limit");
    use std::os::unix::fs::OpenOptionsExt;
    let path=root.join("trial-state.next");
    let mut file=fs::OpenOptions::new().write(true).create(true).truncate(true).custom_flags(libc::O_NOFOLLOW).mode(0o600).open(&path)?;
    file.write_all(&bytes)?;file.sync_all()?;
    if initial {fs::hard_link(&path,root.join("trial-state.json"))?;fs::remove_file(&path)?;}
    else {fs::rename(&path,root.join("trial-state.json"))?;}
    Ok(())
}
fn uuid()->Result<String>{
    let mut bytes=[0;16];openssl::rand::rand_bytes(&mut bytes)?;bytes[6]=(bytes[6]&15)|64;bytes[8]=(bytes[8]&63)|128;
    let h=bytes.iter().map(|b|format!("{b:02x}")).collect::<String>();
    Ok(format!("{}-{}-{}-{}-{}",&h[..8],&h[8..12],&h[12..16],&h[16..20],&h[20..]))
}
pub fn prepare(root:&Path)->Result<Value>{
    let state=if root.join("trial-state.json").exists(){load(root)?}else{
        for product in ["Phoenix-ePassport-Core","Phoenix-eID"] {
            ensure!(!root.join(product).exists(),"Automatic trial is first-start only");
        }
        let device=phoenix_license_native::device_ids()?.into_iter().next().context("Device binding missing")?;
        let (lower,_)=phoenix_license_native::trusted_time_bounds()?;
        let created=chrono::DateTime::from_timestamp((lower/1000).try_into()?,0).context("Trusted activation time")?.to_rfc3339_opts(chrono::SecondsFormat::Secs,true);
        let batch=json!({"type":"phoenix-license-request","version":2,"request_id":uuid()?,"created_at":created,"customer_reference":{"anonymous":true},"device_id":device,"products":[{"product":"phoenix-core","request_id":uuid()?},{"product":"phoenix-eid","request_id":uuid()?}],"license_type":"trial"});
        let state=LegacyActivation::import(batch)?;save(root,&state,true)?;state
    };
    if state.summary()?["license_received"]==true {state.activate()?;Ok(json!({"active":true}))}
    else {Ok(json!({"active":false,"request":state.trial_hash_request()?}))}
}
pub fn receive(root:&Path,delivery:Value)->Result<Value>{
    let mut state=load(root)?;
    state.receive(delivery)?;
    save(root,&state,false)?;
    state.activate()
}
