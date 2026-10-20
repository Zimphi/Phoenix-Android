//! App communication identity configuration; all IFD/TLS behavior remains in the shared core.
use anyhow::Result;
use phoenix_eid_core::remote_ifd::tls::TlsIdentity;
use std::path::Path;

pub fn load(directory: &Path, passphrase: &[u8]) -> Result<TlsIdentity> {
    let path = directory.join("ifd-communication-certificate.pem");
    let identity = if path.try_exists()? {
        TlsIdentity::load(directory, passphrase)?
    } else {
        TlsIdentity::create(directory, passphrase)?
    };
    let original = identity.certificate();
    if original.subject_name().entries().next().is_some() {
        return Ok(identity);
    }
    // Empty issuer names collide with existing simulator trust anchors in OpenSSL/Qt.
    let encoded =
        zeroize::Zeroizing::new(std::fs::read(directory.join("ifd-communication-key.pem"))?);
    let key = openssl::pkey::PKey::private_key_from_pem_passphrase(&encoded, passphrase)?;
    let fingerprint = openssl::sha::sha256(&key.public_key_to_der()?)
        .iter()
        .map(|b| format!("{b:02x}"))
        .collect::<String>();
    let mut name = openssl::x509::X509Name::builder()?;
    name.append_entry_by_text("CN", "Phoenix Android IFD")?;
    name.append_entry_by_text("serialNumber", &fingerprint)?;
    let name = name.build();
    let mut certificate = openssl::x509::X509::builder()?;
    certificate.set_version(original.version())?;
    certificate.set_serial_number(original.serial_number())?;
    certificate.set_not_before(original.not_before())?;
    certificate.set_not_after(original.not_after())?;
    certificate.set_subject_name(&name)?;
    certificate.set_issuer_name(&name)?;
    certificate.set_pubkey(&key)?;
    certificate.append_extension(
        openssl::x509::extension::BasicConstraints::new()
            .critical()
            .ca()
            .pathlen(0)
            .build()?,
    )?;
    certificate.sign(&key, openssl::hash::MessageDigest::sha256())?;
    let certificate = certificate.build();
    let identity = TlsIdentity::new(certificate.clone(), key)?;
    phoenix_eid_core::persistence::write_atomic(&path, &certificate.to_pem()?)?;
    Ok(identity)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn app_identities_remain_trusted_alongside_another_simulator() {
        let mut nonce = [0; 16];
        openssl::rand::rand_bytes(&mut nonce).unwrap();
        let suffix = nonce.iter().map(|b| format!("{b:02x}")).collect::<String>();
        let root = std::env::temp_dir().join(format!("android-ifd-identities-{suffix}"));
        let first = TlsIdentity::create(&root.join("old-simulator"), b"synthetic key").unwrap();
        let second = load(&root.join("android"), b"synthetic key").unwrap();
        assert!(second
            .certificate()
            .subject_name()
            .entries()
            .next()
            .is_some());
        assert_ne!(
            first.certificate().subject_name().to_der().unwrap(),
            second.certificate().subject_name().to_der().unwrap()
        );
        let mut store = openssl::x509::store::X509StoreBuilder::new().unwrap();
        store.add_cert(first.certificate().clone()).unwrap();
        store.add_cert(second.certificate().clone()).unwrap();
        let verified = openssl::x509::X509StoreContext::new()
            .unwrap()
            .init(
                &store.build(),
                second.certificate(),
                &openssl::stack::Stack::new().unwrap(),
                |ctx| ctx.verify_cert(),
            )
            .unwrap();
        assert!(verified);
        assert_eq!(
            second.certificate().to_der().unwrap(),
            load(&root.join("android"), b"synthetic key")
                .unwrap()
                .certificate()
                .to_der()
                .unwrap()
        );
        assert!(load(&root.join("android"), b"wrong key").is_err());
        std::fs::remove_dir_all(root).unwrap();
    }
}
