"""PIN modes over the physical reader, with public synthetic credentials only."""
import json
from pathlib import Path
import sys
workspace=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(workspace/'Phoenix-NFC-Device-Tests'))
from phoenix_runtime import activate
activate(workspace)
from phoenix_inspection.pcsc import PcscTransport,reader_inventory
from phoenix_pace.terminal import PaceTerminal
from phoenix_pace.secure_messaging import SecureMessaging
mode=sys.argv[1]
assert mode in ('transport','personal','inactive')
readers=[r['id'] for r in reader_inventory()['readers'] if 'REINER SCT' in r['id']]
assert len(readers)==1
connection=PcscTransport(readers[0]);contexts=[]
aid=bytes.fromhex('E80704007F00070302')
def connect():
    connection.connect();connection.select_aid(aid);connection.select_mf()
    return connection.read_file(bytes.fromhex('011C'))
def pace(access,password,reference):
    result=PaceTerminal(connection).performPACE(access,password,reference)
    if result.get('ctx') is not None: contexts.append(result['ctx'])
    assert result['status']==0,'PACE failed'
    return SecureMessaging(connection,result['ctx'],result['k_enc'],result['k_mac'],result['protocol_oid'])
try:
    access=connect()
    if mode=='inactive':
        body=bytes.fromhex('800a04007f0007020204020283010384010d')
        data,sw1,sw2=connection.transmit(bytes.fromhex('0022c1a4')+bytes([len(body)])+body)
        assert not data and (sw1,sw2)==(0x62,0x83),f'Inactive eID PIN accepted: {sw1:02x}{sw2:02x}'
        sm=pace(access,b'123456',2)
        sm.select_mf_sm()
        assert sm.read_binary_file_sm(bytes.fromhex('011C'))==access
        checks=['inactive_pin_rejected_6283','can_management_channel_available']
    elif mode=='personal':
        sm=pace(access,b'654321',3)
        sm.select_mf_sm()
        assert sm.read_binary_file_sm(bytes.fromhex('011C'))==access
        checks=['fresh_personal_pin_pace_after_session_restart']
    else:
        sm=pace(access,b'12345',3)
        _,a,b=sm.transmit_sm(0,0x24,0,3,b'12345'+b'11111')
        assert (a,b)==(0x6a,0x80),'Five-digit personal PIN accepted'
        _,a,b=sm.transmit_sm(0,0x24,0,3,b'12345'+b'654321')
        assert (a,b)==(0x90,0),'Protected transport PIN change failed'
        checks=['five_digit_pin_pace','reject_five_digit_personal_pin','protected_change_to_six_digits']
    print(json.dumps({'reader':readers[0],'mode':mode,'checks':checks,'status':'pass'}))
finally:
    for context in contexts: context.close()
    connection.disconnect()
