//! Android lifecycle only; pairing, TLS, discovery and IFD messages come from the shared core.
use anyhow::{ensure, Context, Result};
use phoenix_eid_core::{
    application::CardModel,
    remote_ifd::{
        self, discovery::DiscoveryBroadcaster, tls::AuthenticatedServer, PairingSession, PeerStore,
    },
};
use serde_json::{json, Value};
use std::{
    net::{Ipv4Addr, SocketAddr, TcpListener},
    path::Path,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex,
    },
    thread::JoinHandle,
    time::Duration,
};

#[derive(Default)]
struct State {
    running: bool,
    pairing: bool,
    code: String,
    paired: usize,
    completed: u64,
    error: String,
}
pub struct Reader {
    stop: Arc<AtomicBool>,
    state: Arc<Mutex<State>>,
    server: Arc<Mutex<Option<Arc<AuthenticatedServer>>>>,
    worker: Option<JoinHandle<()>>,
}
fn local_peer(address: SocketAddr) -> bool {
    match address.ip() {
        std::net::IpAddr::V4(ip) => ip.is_private() || ip.is_loopback() || ip.is_link_local(),
        _ => false,
    }
}
impl Reader {
    pub fn start(root: &Path, card: Option<Arc<CardModel>>, pair: bool) -> Result<Self> {
        let directory = root.join("remote-ifd");
        let passphrase = zeroize::Zeroizing::new(std::fs::read(root.join("profile-passphrase"))?);
        let identity = crate::remote_identity::load(&directory, &passphrase)?;
        let peers = PeerStore::open(&directory)?;
        let trusted = peers.certificates()?;
        ensure!(
            pair || !trusted.is_empty(),
            "Zuerst mit der AusweisApp koppeln"
        );
        let pairing = pair
            .then(|| PairingSession::new(None, Duration::from_secs(300), 5))
            .transpose()?;
        let listener = TcpListener::bind((Ipv4Addr::UNSPECIFIED, 24727))?;
        listener.set_nonblocking(true)?;
        let state = Arc::new(Mutex::new(State {
            running: true,
            pairing: pair,
            code: pairing
                .as_ref()
                .map(|p| p.code().to_owned())
                .unwrap_or_default(),
            paired: trusted.len(),
            ..State::default()
        }));
        let stop = Arc::new(AtomicBool::new(false));
        let server = Arc::new(Mutex::new(None));
        let pairing_flag = Arc::new(AtomicBool::new(pair));
        let certificate = identity.certificate().clone();
        let announced_pairing = pairing_flag.clone();
        let mut discovery = DiscoveryBroadcaster::start(
            move || {
                remote_ifd::discovery_message(
                    "Phoenix Android",
                    &certificate,
                    24727,
                    announced_pairing.load(Ordering::Acquire),
                )
            },
            Arc::new(AtomicBool::new(true)),
            Duration::from_secs(1),
        )?;
        let mut reader = Self {
            stop: stop.clone(),
            state: state.clone(),
            server: server.clone(),
            worker: None,
        };
        reader.worker = Some(std::thread::spawn(move || {
            let run = (|| -> Result<()> {
                let mut pairing = pairing;
                let mut current = Arc::new(if let Some(p) = pairing.as_ref() {
                    identity.pairing(p.code().as_bytes())?
                } else {
                    identity.mutual_tls(&trusted)?
                });
                *server.lock().map_err(|_| anyhow::anyhow!("IFD lock"))? = Some(current.clone());
                while !stop.load(Ordering::Acquire) {
                    if let Some(p) = pairing.as_ref() {
                        ensure!(p.active(), "Kopplung abgelaufen; erneut starten");
                    }
                    let (stream, address) = match listener.accept() {
                        Ok(connection) => connection,
                        Err(error) if error.kind() == std::io::ErrorKind::WouldBlock => {
                            std::thread::park_timeout(Duration::from_millis(100));
                            continue;
                        }
                        Err(error) => return Err(error.into()),
                    };
                    if !local_peer(address) {
                        continue;
                    }
                    let result = current.serve_connection(stream, card.clone());
                    if stop.load(Ordering::Acquire) {
                        break;
                    }
                    if let Some(p) = pairing.as_mut() {
                        match result {
                            Ok(Some(certificate)) => {
                                p.complete(&peers, &certificate)?;
                                pairing = None;
                                current = Arc::new(identity.mutual_tls(&peers.certificates()?)?);
                                *server.lock().map_err(|_| anyhow::anyhow!("IFD lock"))? =
                                    Some(current.clone());
                                pairing_flag.store(false, Ordering::Release);
                                let mut state =
                                    state.lock().map_err(|_| anyhow::anyhow!("IFD state"))?;
                                state.pairing = false;
                                state.code.clear();
                                state.paired = peers.certificates()?.len();
                                state.error.clear();
                            }
                            _ => {
                                p.failed_attempt();
                            }
                        }
                    } else {
                        let mut state = state.lock().map_err(|_| anyhow::anyhow!("IFD state"))?;
                        if result.is_ok() {
                            state.completed += 1;
                            state.error.clear();
                        } else {
                            state.error = "Verbindung abgewiesen oder unterbrochen".into();
                        }
                    }
                }
                Ok(())
            })();
            let discovery_result = discovery.stop();
            if let Ok(mut state) = state.lock() {
                state.running = false;
                state.code.clear();
                state.pairing = false;
                if let Err(error) = run.and(discovery_result) {
                    state.error = error.to_string();
                }
            }
        }));
        Ok(reader)
    }
    pub fn status(&self) -> Result<Value> {
        let state = self
            .state
            .lock()
            .map_err(|_| anyhow::anyhow!("IFD state"))?;
        Ok(
            json!({"running":state.running,"pairing":state.pairing,"pairing_code":state.code,"paired":state.paired,"completed_connections":state.completed,"last_error":state.error}),
        )
    }
    pub fn stop(&mut self) -> Result<()> {
        self.stop.store(true, Ordering::Release);
        if let Some(server) = self
            .server
            .lock()
            .map_err(|_| anyhow::anyhow!("IFD lock"))?
            .as_ref()
        {
            server.stop();
        }
        if let Some(worker) = self.worker.take() {
            worker.thread().unpark();
            worker
                .join()
                .map_err(|_| anyhow::anyhow!("IFD worker failed"))?;
        }
        Ok(())
    }
}
impl Drop for Reader {
    fn drop(&mut self) {
        let _ = self.stop();
    }
}

pub fn load_card(root: &Path, id: &str) -> Result<Arc<CardModel>> {
    phoenix_simulator::pki_admin::identifier(id)?;
    let config = crate::provision::config(root)?;
    let profile = phoenix_eid_core::personalization::PersonalizedProfile::load(
        &config.profiles.join(id),
        &config.passphrase,
        &config.trusted_cscas,
        chrono::Utc::now().timestamp(),
        &[],
    )
    .context("Remote IFD eID-Profil")?;
    Ok(Arc::new(profile.into_card()?))
}
#[cfg(test)]
mod tests {
    use super::*;
    use phoenix_eid_core::remote_ifd::tls::TlsIdentity;
    #[test]
    fn status_does_not_collide_with_jni_error_envelope() {
        let reader = Reader {
            stop: Arc::new(AtomicBool::new(false)),
            state: Arc::new(Mutex::new(State::default())),
            server: Arc::new(Mutex::new(None)),
            worker: None,
        };
        assert!(reader.status().unwrap().get("error").is_none());
    }
    #[test]
    fn removing_pairings_preserves_identity_and_rejects_corrupt_trust() {
        let nonce = openssl::rand::rand_bytes;
        let mut bytes = [0; 16];
        nonce(&mut bytes).unwrap();
        let suffix = bytes.iter().map(|b| format!("{b:02x}")).collect::<String>();
        let root = std::env::temp_dir().join(format!("android-ifd-test-{suffix}"));
        let identity_root = root.join("remote-ifd");
        let identity =
            TlsIdentity::create(&identity_root, b"synthetic test encryption key").unwrap();
        let encoded = identity.certificate().to_der().unwrap();
        {
            let peers = PeerStore::open(&identity_root).unwrap();
            peers.register(identity.certificate()).unwrap();
        }
        assert_eq!(stored_peers(&root).unwrap(), 1);
        forget_peers(&root).unwrap();
        assert_eq!(stored_peers(&root).unwrap(), 0);
        assert_eq!(
            TlsIdentity::load(&identity_root, b"synthetic test encryption key")
                .unwrap()
                .certificate()
                .to_der()
                .unwrap(),
            encoded
        );
        std::fs::write(
            identity_root.join("trusted-ud-certificates.pem"),
            b"-----BEGIN CERTIFICATE-----\nY29ycnVwdA==\n-----END CERTIFICATE-----\n",
        )
        .unwrap();
        assert!(stored_peers(&root).is_err());
        std::fs::remove_dir_all(root).unwrap();
    }
    #[test]
    fn remote_reader_accepts_only_local_network_peers() {
        for ip in [
            "127.0.0.1:1234",
            "192.168.1.4:1",
            "10.0.0.2:24727",
            "172.16.0.2:9",
            "169.254.2.3:8",
        ] {
            assert!(local_peer(ip.parse().unwrap()));
        }
        for ip in ["8.8.8.8:1234", "172.32.0.1:1", "[::1]:24727"] {
            assert!(!local_peer(ip.parse().unwrap()));
        }
    }
}

pub fn stored_peers(root: &Path) -> Result<usize> {
    Ok(PeerStore::open(&root.join("remote-ifd"))?
        .certificates()?
        .len())
}
pub fn forget_peers(root: &Path) -> Result<()> {
    let peers = PeerStore::open(&root.join("remote-ifd"))?;
    for certificate in peers.certificates()? {
        let hash = openssl::sha::sha256(&certificate.to_der()?)
            .iter()
            .map(|b| format!("{b:02x}"))
            .collect::<String>();
        peers.unregister(&hash)?;
    }
    Ok(())
}
