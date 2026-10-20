"""Physical REINER-SCT proof for the Android app; uses the sibling licensed terminal."""
import json
from pathlib import Path
import sys
import time

workspace = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(workspace / 'Phoenix-NFC-Device-Tests'))
from phoenix_runtime import activate
activate(workspace)
from phoenix_inspection.pcsc import PcscTransport, reader_inventory
from phoenix_pace.terminal import PaceTerminal
from phoenix_pace.secure_messaging import SecureMessaging

kind, mode = sys.argv[1:]
assert kind in ('epassport', 'eid') and mode in ('active', 'stopped')
readers = [r['id'] for r in reader_inventory()['readers'] if 'REINER SCT' in r['id']]
assert len(readers) == 1, 'Exactly one physical REINER SCT reader required'
connection = PcscTransport(readers[0])
result = None
checks = []
try:
    connection.connect()
    aid = bytes.fromhex('A0000002471001' if kind == 'epassport' else 'E80704007F00070302')
    if mode == 'stopped':
        # A field transition may invalidate the reader handle; require an actual 6985 response.
        for attempt in range(3):
            try:
                data, sw1, sw2 = connection.transmit(bytes.fromhex('00A40400') + bytes([len(aid)]) + aid)
                break
            except OSError as error:
                if '0x80100016' not in str(error) or attempt == 2:
                    raise
                connection.disconnect()
                time.sleep(0.3)
                connection.connect()
        assert not data and (sw1, sw2) == (0x69, 0x85), f'Stopped simulation accepted an APDU: {sw1:02X}{sw2:02X}'
        checks.append('stopped_simulation_rejects_apdu')
    else:
        connection.select_aid(aid)
        checks.append('physical_application_selection')
        connection.select_mf()
        access = connection.read_file(bytes.fromhex('011C'))
        assert access, 'Empty CardAccess'
        checks.append('card_access_read')
        result = PaceTerminal(connection).performPACE(access, b'123456', 2)
        assert result['status'] == 0, 'PACE authentication failed'
        checks.append('pace_mutual_authentication')
        sm = SecureMessaging(connection, result['ctx'], result['k_enc'], result['k_mac'], result['protocol_oid'])
        if kind == 'epassport':
            sm.select_aid_sm(aid)
            data = sm.read_binary_file_sm(bytes.fromhex('0101'))
            assert b'MUSTERMANN' in data and b'ERIKA' in data, 'Wrong synthetic passport DG1'
            checks.append('secure_messaging_synthetic_dg1_matches')
        else:
            sm.select_mf_sm()
            assert sm.read_binary_file_sm(bytes.fromhex('011C')) == access, 'Protected CardAccess mismatch'
            checks.append('secure_messaging_card_access_matches')
    print(json.dumps({'kind': kind, 'reader': readers[0], 'checks': checks, 'status': 'pass'}))
finally:
    if result is not None and result.get('ctx') is not None:
        result['ctx'].close()
    connection.disconnect()
