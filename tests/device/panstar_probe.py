"""Opt-in external Panstar authentication; synthetic PIN enters a private PTY once."""
import errno,fcntl,json,os,pathlib,pty,re,select,subprocess,sys,termios,time,urllib.parse,urllib.request
request=json.load(sys.stdin)
master,slave=pty.openpty()
client=pathlib.Path(__file__).resolve().parents[3]/'Phoenix-eID-Client'
workflow="""
import getpass,sys
from phoenix_eid_client.panstar import start_panstar_test
from phoenix_eid_client.tc_token import fetch_tc_token
from phoenix_eid_crypto.paos_channel import PaosChannel
channel=PaosChannel.start(fetch_tc_token(start_panstar_test(rights_profile=sys.argv[2])).paos_configuration())
try:
 result=channel.authenticate(getpass.getpass('eID-PIN (6 Ziffern): '),reader=sys.argv[1])
 print('Authentisierung erfolgreich abgeschlossen.')
 print('Übertragene Kartenkommandos:',result.transmitted_apdus)
 print('Rücksprungadresse:',result.refresh_address)
finally: channel.close()
"""
command=[sys.executable,'-c',workflow,request['reader'],request.get('rights','identity')]
if binary:=os.environ.get('PHOENIX_EID_CLIENT_BINARY'):
 command=[binary,'panstar','--yes','--no-browser','--reader',request['reader'],'--rights-profile',request.get('rights','identity')]
environment=dict(os.environ,PYTHONPATH=str(client)+os.pathsep+str(client/'crypto_service'))
process=subprocess.Popen(command,stdin=slave,stdout=slave,stderr=slave,env=environment,start_new_session=True,preexec_fn=lambda:fcntl.ioctl(0,termios.TIOCSCTTY,0))
os.close(slave)
output=b'';sent=False;failure=None;deadline=time.monotonic()+90
try:
 while process.poll() is None and time.monotonic()<deadline:
  if not select.select([master],[],[],.2)[0]:continue
  try:part=os.read(master,65536)
  except OSError as error:
   if error.errno==errno.EIO:break
   raise
  if not part:break
  output+=part
  if len(output)>1024*1024:failure='Client output limit';break
  prompts=len(re.findall(rb'(?m)^(?:eID-PIN \(6 Ziffern\)|Pin): ',output))
  if prompts>1:failure='Credential retry requested; stopped before another PIN attempt';break
  if prompts==1 and not sent:os.write(master,(request['pin']+'\n').encode());sent=True
 if process.poll() is None and (failure or time.monotonic()>=deadline):
  failure=failure or 'Authentication timed out'
  process.terminate()
 try: process.wait(timeout=5)
 except subprocess.TimeoutExpired:
  failure=failure or "Client did not close after timeout"
  process.kill()
 while select.select([master],[],[],0)[0]:
  try:
   part=os.read(master,65536)
   if not part:break
   output+=part
  except OSError:break
finally:
 if process.poll() is None:
  try: process.kill()
  except ProcessLookupError: pass
 os.close(master)
text=output.decode(errors='replace')
match=re.search(r'Authentisierung erfolgreich abgeschlossen\.\s+Übertragene Kartenkommandos: (\d+)',text) or re.search(r'Authentisierung abgeschlossen\. Kartenkommandos: (\d+)',text)
result={'authenticated':process.returncode==0 and match is not None,'returncode':process.returncode,'commands':int(match[1]) if match else None}
if result['authenticated']:
 refresh=re.search(r'Rücksprungadresse: (https://\S+)',text)
 if refresh:
  url=refresh[1];parsed=urllib.parse.urlsplit(url)
  if parsed.hostname=='test.governikus-eid.de':
   try:
    with urllib.request.urlopen(url,timeout=20) as response:
     body=response.read(512*1024).decode(errors='replace')
     result['service_http']=response.status
     # Return only an aggregate, never session URLs, cookies or returned identity values.
     result['service_result_page']=bool(re.search(r'(Ergebnis|result|Identit|givenNames|Familienname)',body,re.I))
   except Exception as error:result['service_fetch_error']=type(error).__name__
else:
 lines=[line.strip() for line in text.splitlines() if line.strip()]
 reason=failure or next((line for line in reversed(lines) if 'error' in line.lower() or 'Error' in line or 'failed' in line.lower()),'Client did not confirm authentication')
 reason=re.sub(r'https?://\S+','[session URL removed]',reason)
 result['reason']=reason.replace(request['pin'],'[PIN removed]')[:500]
 result['diagnostic']=re.sub(r'https?://\S+','[session URL removed]',text).replace(request['pin'],'[PIN removed]')[-1600:]
print(json.dumps(result))
