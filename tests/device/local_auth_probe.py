"""Run the existing local clients over physical PC/SC; never implements EAC itself."""
import json
import os
from pathlib import Path
import subprocess
import sys

workspace = Path(__file__).resolve().parents[3]
material = Path(os.environ['PHOENIX_AUTH_MATERIAL']).resolve()
kind = sys.argv[1]
reader = 'REINER SCT cyberJack RFID basis'
password = (material / 'terminal-password').read_text()
sys.path[:0] = [str(workspace / p) for p in ('Phoenix-License-Client', 'Phoenix-IS-Terminal')]
from phoenix_inspection.pcsc import PcscTransport
routing = PcscTransport(reader)
try:
    routing.connect()
    routing.select_aid(bytes.fromhex('E80704007F00070302' if kind == 'eid' else 'A0000002471001'))
    if kind == 'eid':
        client = workspace / 'Phoenix-eID-Client/flutter_app/assets/eid-client-runtime/phoenix-eid-client'
        request = {'reader': reader, 'pin': '123456', 'configuration': str(material / 'eid-terminal/configuration.json'), 'key_password': password}
        completed = subprocess.run([str(client), '--disclosure-bridge'], input=json.dumps(request)+'\n', text=True, capture_output=True, timeout=70)
    else:
        assert kind == 'epassport'
        environment = dict(os.environ, PYTHONPATH=os.pathsep.join(str(workspace / p) for p in ('Phoenix-License-Client', 'Phoenix-IS-Terminal')), EPASSPORT_PASSWORD='123456', EPASSPORT_CVCA_PASSWORD=password, EPASSPORT_PKI_DIR=str(material / 'csca'), EPASSPORT_CVCA_DIR=str(material / 'passport-terminal'))
        completed = subprocess.run([str(workspace / 'Phoenix-ePassport-Core/.venv/bin/python'), str(workspace / 'Phoenix-IS-Terminal/main.py'), '--cli', '--reader', reader, '--auth', 'CAN'], env=environment, text=True, capture_output=True, timeout=70)
finally:
    routing.disconnect()
# The private trace contains synthetic card data, never the PIN/key input or environment.
(material / f'{kind}-client-output.json').write_text(completed.stdout)
(material / f'{kind}-client-stderr.log').write_text(completed.stderr)
assert completed.returncode == 0, f'{kind} client failed; inspect private test output'
value = json.loads(completed.stdout)
if kind == 'eid':
    assert value.get('status') == 'success' and value.get('security_state') == 'PACE-TA-PA-CA', value.get('message', 'Incomplete eID authentication')
    protocols = value['protocols']
    assert protocols['pace']['result'] is True and protocols['passive_authentication']['verified'] is True and protocols['chip_authentication']['verified'] is True
    assert {item['number'] for item in value['data_groups']} == {4, 5, 8}, 'Missing authorized eID groups'
    summary = {'client': 'local Phoenix eID Client', 'security_state': value['security_state'], 'data_groups': [4, 5, 8], 'android_aid_preselection': True}
else:
    assert value['authentic'], 'IS Terminal did not authenticate the complete document'
    states = value['workflow']
    for step in ('pace', 'ca', 'pa', 'ta', 'dgs'):
        assert states[step]['state'] == 'success', f'{step} not completed: {states[step]}'
    assert 3 in {item['number'] for item in value['files'] if item.get('read')}, 'TA-protected DG3 was not read'
    summary = {'client': 'local Phoenix IS Terminal', 'authentic': True, 'protocols': ['PACE', 'CA', 'PA', 'TA'], 'protected_DG3_read': True, 'android_aid_preselection': True}
print(json.dumps({'kind': kind, 'reader': reader, 'status': 'pass', **summary}))
