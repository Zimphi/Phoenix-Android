// No private keys, license credentials or diagnostic plaintext belong in this service.
const BASE='/v1/nfc-reports',MAX_FILE=1900000,MAX_STORAGE=64*1024*1024,DAY=86400;
const COLUMNS='id,owner,request_id,nonce,created,expires,ticket_expires,state,sha256,bytes';
const PRIMARY='FD3DF3CC2F3B4D2116D413A5C59CD4AAABD99160';
const SUBKEY='0A0C0B92467C2A50EB9578DA64FE711F9A5E204A';
const UUID=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const TOKEN=/^[A-Za-z0-9_-]{43}$/;
const seconds=()=>Math.floor(Date.now()/1000);
const json=(value,status=200)=>new Response(JSON.stringify(value),{status,headers:{
  'content-type':'application/json','cache-control':'private, no-store',
  'x-content-type-options':'nosniff','strict-transport-security':'max-age=31536000'}});
class HttpError extends Error {constructor(status,code){super(code);this.status=status;}}
function requireValue(test,status=400,code='invalid_request'){if(!test)throw new HttpError(status,code);}
async function hash(bytes){return Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256',bytes)),b=>b.toString(16).padStart(2,'0')).join('');}
const encoded=value=>new TextEncoder().encode(value);
function randomToken(){return btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32)))).replaceAll('+','-').replaceAll('/','_').replaceAll('=','');}

async function bounded(message,limit){
  const length=message.headers.get('content-length');
  requireValue(length===null||/^[0-9]+$/.test(length));
  requireValue(length===null||Number(length)<=limit,413,'too_large');
  if(!message.body)return new Uint8Array();
  const reader=message.body.getReader(),parts=[];let size=0;
  const deadline=Date.now()+10000;
  try{
    while(true){
      let timer;
      const result=await Promise.race([reader.read(),new Promise((_,reject)=>{timer=setTimeout(()=>reject(new HttpError(408,'body_timeout')),Math.max(1,deadline-Date.now()));})]).finally(()=>clearTimeout(timer));
      if(result.done)break;
      size+=result.value.byteLength;requireValue(size<=limit,413,'too_large');parts.push(result.value);
    }
    const bytes=new Uint8Array(size);let offset=0;for(const part of parts){bytes.set(part,offset);offset+=part.byteLength;}return bytes;
  }catch(error){await reader.cancel().catch(()=>{});throw error;}
  finally{reader.releaseLock();}
}

async function authenticate(request,env,path,bytes){
  const h=request.headers,id=(h.get('authorization')||'').replace(/^PhoenixDevice /,'');
  const salt=h.get('x-phoenix-diagnostics-salt')||'',nonce=h.get('x-phoenix-nonce')||'';
  const timestamp=h.get('x-phoenix-timestamp')||'',signature=h.get('x-phoenix-signature')||'';
  const uploadNonce=h.get('x-report-nonce')||'';
  requireValue(h.get('authorization')==='PhoenixDevice '+id&&UUID.test(id)&&TOKEN.test(salt)&&/^[0-9a-f]{64}$/.test(nonce)
    &&/^[0-9]{10}$/.test(timestamp)&&/^[A-Za-z0-9+/]{80,126}={0,2}$/.test(signature)
    &&(uploadNonce===''||TOKEN.test(uploadNonce)),401,'unauthenticated');
  requireValue(Number(timestamp)>=seconds()-60&&Number(timestamp)<=seconds()+3,401,'stale_proof');
  const expected=await hash(encoded(JSON.stringify(['phoenix-diagnostics-v1',env.PUBLIC_ORIGIN,request.method,path,await hash(bytes),salt,uploadNonce])));
  requireValue(expected===nonce,401,'invalid_binding');
  const upstream={production:'https://epassport-simulator.de',preview:'https://preview.epassport-simulator.de'}[env.AUTH_ENVIRONMENT];
  requireValue(upstream,503,'configuration_error');
  const headers={'content-type':'application/json'};
  for(const name of ['authorization','x-phoenix-nonce','x-phoenix-timestamp','x-phoenix-signature'])headers[name]=h.get(name);
  const response=await fetch(upstream+'/api/auth/device/status',{method:'POST',body:'{}',headers,redirect:'manual',signal:AbortSignal.timeout(8000)});
  requireValue(response.status===200,response.status>=500||response.status>=300&&response.status<400?503:401,'authentication_failed');
  requireValue(response.headers.get('content-type')?.startsWith('application/json'),503,'authentication_failed');
  const result=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(await bounded(response,16384)));
  requireValue(result.authenticated===true&&result.device_id===id&&UUID.test(result.user_id),401,'authentication_failed');
  requireValue((await env.AUTH_LIMITER.limit({key:'owner:'+result.user_id})).success,429,'rate_limited');
  return result.user_id;
}

function offer(row){return {id:row.id,nonce:row.nonce,upload_expires_at:row.ticket_expires,
  expires_at:row.expires,recipient:PRIMARY,encryption_subkey:SUBKEY,max_bytes:MAX_FILE};}
function metadata(row){return {id:row.id,created_at:row.created,expires_at:row.expires,
  sha256:row.sha256,bytes:row.bytes,recipient:PRIMARY,encryption_subkey:SUBKEY};}
async function reservation(env,owner,body){
  let value;try{value=JSON.parse(new TextDecoder('utf-8',{fatal:true}).decode(body));}catch{throw new HttpError(400,'invalid_request');}
  requireValue(value&&Object.keys(value).length===1&&UUID.test(value.request_id));
  const now=seconds();
  await env.DB.prepare(`INSERT INTO reports (id,owner,request_id,nonce,created,expires,ticket_expires,state)
    SELECT ?,?,?,?,?,?,?,'reserved' WHERE (SELECT COUNT(*) FROM reports WHERE owner=? AND created>?)<20
    AND (SELECT COUNT(*) FROM reports WHERE created>?)<200
    AND (SELECT COALESCE(SUM(CASE WHEN state='reserved' THEN ? ELSE bytes END),0) FROM reports WHERE state<>'deleting' AND expires>?)+?<=?
    ON CONFLICT(owner,request_id) DO NOTHING`).bind(crypto.randomUUID(),owner,value.request_id,randomToken(),now,now+14*DAY,now+300,owner,now-DAY,now-DAY,MAX_FILE,now,MAX_FILE,MAX_STORAGE).run();
  const row=await env.DB.prepare(`SELECT ${COLUMNS} FROM reports WHERE owner=? AND request_id=?`).bind(owner,value.request_id).first();
  requireValue(row,429,'capacity_or_daily_quota');
  requireValue(row.state!=='deleting'&&row.ticket_expires>now,410,'reservation_expired');
  return json(offer(row),201);
}

// One D1 statement commits ciphertext and metadata, conditional on the still-live reservation.
export async function storeUpload(env,row,bytes,digest){
  const result=await env.DB.prepare("UPDATE reports SET state='complete',payload=?,sha256=?,bytes=? WHERE id=? AND state='reserved' AND ticket_expires>? AND expires>?")
    .bind(bytes,digest,bytes.byteLength,row.id,seconds(),seconds()).run();
  return result.meta.changes===1;
}

async function reportRequest(request,env,row,bytes){
  const now=seconds();
  if(request.method==='DELETE'){
    await env.DB.prepare("UPDATE reports SET state='deleting',payload=NULL,sha256=NULL,bytes=NULL,nonce='' WHERE id=?").bind(row.id).run();
    return json({deleted:true,id:row.id});
  }
  requireValue(row.state!=='deleting'&&row.expires>now,404,'not_found');
  if(request.method==='GET'){
    requireValue(row.state==='complete',404,'not_found');
    const stored=await env.DB.prepare("SELECT payload FROM reports WHERE id=? AND state='complete' AND expires>?").bind(row.id,now).first();
    requireValue(stored?.payload,404,'not_found');
    const ciphertext=new Uint8Array(stored.payload);requireValue(ciphertext.byteLength===row.bytes,503,'content_unavailable');
    return new Response(ciphertext,{headers:{'content-type':'application/pgp-encrypted',
      'content-disposition':`attachment; filename="phoenix-nfc-${row.id}.pgp"`,'cache-control':'private, no-store',
      'x-content-type-options':'nosniff','content-length':String(ciphertext.length),'x-content-sha256':row.sha256}});
  }
  requireValue(request.headers.get('x-report-nonce')===row.nonce,403,'invalid_upload_nonce');
  const digest=await hash(bytes);
  if(row.state==='complete'){
    requireValue(row.sha256===digest&&row.bytes===bytes.byteLength,409,'different_content');return json(metadata(row));
  }
  requireValue(row.ticket_expires>now,410,'reservation_expired');
  requireValue(bytes.length>=64&&((bytes[0]&0xc0)===0xc0?(bytes[0]&63):((bytes[0]>>2)&15))===1,415,'openpgp_required');
  const stored=await storeUpload(env,row,bytes,digest);
  requireValue(stored,409,'upload_cancelled');return json(metadata({...row,sha256:digest,bytes:bytes.length}));
}

export async function cleanup(env,now=seconds()){
  await env.DB.prepare("UPDATE reports SET state='deleting',payload=NULL,sha256=NULL,bytes=NULL,nonce='' WHERE state<>'deleting' AND (expires<=? OR (state='reserved' AND ticket_expires<=?))").bind(now,now).run();
  await env.DB.prepare("DELETE FROM reports WHERE state='deleting' AND created<?").bind(now-DAY).run();
}

async function operatorRequest(request,env,url){
  requireValue(request.method==='GET',405,'method_not_allowed');
  const authorization=request.headers.get('authorization')||'';
  requireValue(/^Bearer [A-Za-z0-9_-]{43}$/.test(authorization)&&/^[0-9a-f]{64}$/.test(env.OPERATOR_TOKEN_SHA256||''),401,'unauthenticated');
  const actual=await hash(encoded(authorization.slice(7)));
  let difference=0;for(let i=0;i<64;i++)difference|=actual.charCodeAt(i)^env.OPERATOR_TOKEN_SHA256.charCodeAt(i);
  requireValue(difference===0,401,'unauthenticated');
  const base='/v1/operator/nfc-reports';
  if(url.pathname===base){
    const after=url.searchParams.get('after')||'';
    requireValue(url.search===''||url.search==='?after='+after&&UUID.test(after));
    const rows=await env.DB.prepare(`SELECT ${COLUMNS} FROM reports WHERE state='complete' AND expires>? AND id>? ORDER BY id LIMIT 50`)
      .bind(seconds(),after).all();
    return json({reports:rows.results.map(metadata),next:rows.results.length===50?rows.results.at(-1).id:null});
  }
  const id=url.pathname.slice(base.length+1);
  requireValue(url.search===''&&url.pathname===base+'/'+id&&UUID.test(id));
  const row=await env.DB.prepare(`SELECT ${COLUMNS} FROM reports WHERE id=?`).bind(id).first();
  requireValue(row,404,'not_found');return reportRequest(request,env,row,new Uint8Array());
}

async function handle(request,env){
  const url=new URL(request.url),path=url.pathname;
  requireValue(url.origin===env.PUBLIC_ORIGIN&&url.protocol==='https:'&&!path.includes('%'),400,'invalid_target');
  if(path==='/health'&&request.method==='GET')return json({service:'phoenix-nfc-diagnostics',version:1});
  if(path.startsWith('/v1/operator/')){
    requireValue((await env.AUTH_LIMITER.limit({key:'operator-source:'+await hash(encoded(request.headers.get('cf-connecting-ip')||'unknown'))})).success,429,'rate_limited');
    await bounded(request,0);return operatorRequest(request,env,url);
  }
  requireValue(url.search==='');
  const item=path.startsWith(BASE+'/')?path.slice(BASE.length+1):null;
  requireValue(path===BASE||item&&UUID.test(item),400,'invalid_path');
  requireValue(path===BASE?['GET','POST'].includes(request.method):['GET','PUT','DELETE'].includes(request.method),405,'method_not_allowed');
  const ip=request.headers.get('cf-connecting-ip')||'unknown';
  requireValue((await env.AUTH_LIMITER.limit({key:'source:'+await hash(encoded(ip))})).success,429,'rate_limited');
  if(request.method==='POST')requireValue(request.headers.get('content-type')==='application/json',415,'json_required');
  if(request.method==='PUT')requireValue(request.headers.get('content-type')==='application/pgp-encrypted',415,'openpgp_required');
  const bytes=await bounded(request,request.method==='PUT'?MAX_FILE:request.method==='POST'?1024:0);
  const owner=await authenticate(request,env,path,bytes);
  if(path===BASE&&request.method==='POST')return reservation(env,owner,bytes);
  if(path===BASE){
    const rows=await env.DB.prepare(`SELECT ${COLUMNS} FROM reports WHERE owner=? AND state='complete' AND expires>? ORDER BY created DESC,id LIMIT 50`).bind(owner,seconds()).all();
    return json({reports:rows.results.map(metadata)});
  }
  const row=await env.DB.prepare(`SELECT ${COLUMNS} FROM reports WHERE id=? AND owner=?`).bind(item,owner).first();
  requireValue(row,404,'not_found');return reportRequest(request,env,row,bytes);
}

export default {
  async fetch(request,env){try{return await handle(request,env);}catch(error){return json({error:error instanceof HttpError?error.message:'service_unavailable'},error instanceof HttpError?error.status:503);}},
  async scheduled(_event,env){await cleanup(env);}
};
