//! Prepare an additional synthetic EAC profile and local terminal credentials; never modifies originals.
use anyhow::{ensure, Context, Result};
use phoenix_eid_core::{
    authorization::{Authorization, TerminalType},
    chat::ChatConfiguration,
    pki::{cvc::CvcaConfig, store::Authority},
};
use phoenix_simulator::{
    pki_admin,
    runtime::{Config, Native},
};
use serde_json::json;
use std::{collections::BTreeMap, fs, path::Path};
use zeroize::Zeroizing;
fn credentials(
    root: &Path,
    id: &str,
    password: &[u8],
    output: &Path,
    export_password: &[u8],
) -> Result<()> {
    let Authority::Terminal(mut pki) = pki_admin::load(&root.join("runtime"), id, password)? else {
        anyhow::bail!("CVCA required")
    };
    let today = chrono::Utc::now().date_naive();
    if pki.document_verifiers().is_empty() {
        pki.issue_document_verifier(None, true, None, 30, today)?;
    }
    if pki.terminals().is_empty() {
        pki.issue_terminal(0, None, None, 14, today, BTreeMap::new())?;
    }
    fs::create_dir(output)?;
    fs::write(output.join("cvca.cvcert"), &pki.root().certificate)?;
    fs::write(
        output.join("dv.cvcert"),
        &pki.document_verifiers()[0].certificate,
    )?;
    let terminal = &pki.terminals()[0];
    fs::write(output.join("terminal.cvcert"), &terminal.certificate)?;
    fs::write(
        output.join("terminal-key.pem"),
        terminal.private_key.private_key_to_pem_pkcs8_passphrase(
            openssl::symm::Cipher::aes_256_cbc(),
            export_password,
        )?,
    )?;
    Ok(())
}
fn main() -> Result<()> {
    let directory = std::env::args()
        .nth(1)
        .context("private test directory required")?;
    let root = Path::new(&directory);
    ensure!(
        root.join("phone.tar").is_file(),
        "Copied Android synthetic test data required"
    );
    let password = Zeroizing::new(fs::read(root.join("profile-passphrase"))?);
    let export_password = Zeroizing::new(fs::read(root.join("terminal-password"))?);
    let Authority::Document(document) =
        pki_admin::load(&root.join("runtime"), "android-document", &password)?
    else {
        anyhow::bail!("CSCA required")
    };
    fs::write(root.join("csca.cer"), document.certificate().to_der()?)?;
    credentials(
        root,
        "android-terminal",
        &password,
        &root.join("eid-terminal"),
        &export_password,
    )?;
    let mut native = Native::new(Config {
        data: root.join("runtime"),
        profiles: root.join("profiles/eid"),
        passport_profiles: root.join("profiles/epassport"),
        passphrase: password,
        trusted_cscas: vec![document.certificate().clone()],
        ..Default::default()
    });
    let chat = ChatConfiguration::from_authorization(&Authorization::new(
        3,
        ["read_dg3".into(), "read_dg4".into()].into(),
        TerminalType::Inspection,
    )?);
    let config = CvcaConfig {
        chat,
        ..Default::default()
    };
    native.manage_pki(serde_json::from_value(
        json!({"op":"create_cvca","id":"android-full-auth-is","config":config}),
    )?)?;
    credentials(
        root,
        "android-full-auth-is",
        &fs::read(root.join("profile-passphrase"))?,
        &root.join("passport-terminal"),
        &export_password,
    )?;
    let base = fs::read_dir(root.join("profiles/epassport"))?
        .next()
        .context("Passport profile required")??
        .path();
    let dg1 = phoenix_simulator::runtime::hex(&fs::read(base.join("EF.DG1"))?);
    let dg3 = phoenix_simulator::runtime::hex(&fs::read(root.join("synthetic-dg3.bin"))?);
    // shortcut: match Profile.json field order; canonicalize identities when the draft format changes.
    let draft = json!({"format":"phoenix-android-draft-v1","id":"passport-full-auth-test","kind":"epassport","name":"Erika EAC-Test","groups":{"DG1":dg1,"DG2":"synthetic:erika","DG3":dg3},"settings":{"can":"123456","pin":"123456","puk":"1234567890"}});
    let hash = openssl::sha::sha256(&serde_json::to_vec(&draft)?);
    let id = format!(
        "android_{}",
        phoenix_simulator::runtime::hex(&hash[..8]).to_lowercase()
    );
    let groups = json!({"1":dg1,"2":phoenix_simulator::runtime::hex(&fs::read(base.join("EF.DG2"))?),"3":dg3});
    native.manage_passport_profile(serde_json::from_value(json!({"op":"personalize","id":id,"name":"Erika EAC-Test","can":"123456","data_groups":groups,"passive_authentication":{"mode":"classic","classic_authority":"android-document"},"eac":{"ca":{"oid":"0.4.0.127.0.7.2.2.3.2.4","curve":"brainpoolP256r1","key_id":1,"version":1},"terminal_authority":"android-full-auth-is","ta_oid":"0.4.0.127.0.7.2.2.2.2.3"}}))?)?;
    fs::write(
        root.join("full-auth-draft.json"),
        serde_json::to_vec_pretty(&draft)?,
    )?;
    fs::write(root.join("full-auth-native-id"), id.as_bytes())?;
    println!("Prepared separate Android EAC test profile and matching local client credentials");
    Ok(())
}
