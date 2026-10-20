"""Configure existing local clients from private synthetic CVC material."""
import json
import sys
from pathlib import Path
workspace = Path(__file__).resolve().parents[3]
root = Path(sys.argv[1]).resolve()
sys.path[:0] = [str(workspace / 'Phoenix-IS-Terminal'), str(workspace / 'Phoenix-License-Client')]
from phoenix_eac.cvc import CVCCertificate, verify_cvc_chain
for name in ('eid-terminal', 'passport-terminal'):
    directory = root / name
    (directory / 'csca.cer').write_bytes((root / 'csca.cer').read_bytes())
    raw = [(directory / f'{x}.cvcert').read_bytes() for x in ('cvca', 'dv', 'terminal')]
    certs = [CVCCertificate.parse(c) for c in raw]
    verify_cvc_chain(certs)
    if name == 'eid-terminal':
        config = {'name': 'Android TEST local disclosure', 'certificates': ['dv.cvcert', 'terminal.cvcert'], 'private_key': 'terminal-key.pem', 'csca': 'csca.cer', 'data_groups': [4, 5, 8]}
        (directory / 'configuration.json').write_text(json.dumps(config))
    else:

        def entry(index, name):
            c = certs[index]
            return {'id': name, 'reference': c.holder_reference.decode('ascii'), 'certificate': f'{name}.cvcert', **({'issuer': ['cvca', 'dv'][index - 1]} if index else {})}
        terminal = entry(2, 'terminal')
        terminal['private_key'] = 'terminal-key.pem'
        manifest = {'format': 1, 'config': {'signature_scheme': 'ECDSA_SHA_256'}, 'cvca': entry(0, 'cvca'), 'document_verifiers': [entry(1, 'dv')], 'terminals': [terminal]}
        (directory / 'manifest.json').write_text(json.dumps(manifest))
csca = root / 'csca'
csca.mkdir(exist_ok=True)
(csca / 'csca.cer').write_bytes((root / 'csca.cer').read_bytes())
(csca / 'manifest.json').write_text(json.dumps({'format': 1, 'csca': {'certificate': 'csca.cer'}, 'crls': []}))
for path in root.rglob('*'):
    if path.is_file():
        path.chmod(384)
print('Verified CVC chains; local eID and IS terminal configuration prepared')
