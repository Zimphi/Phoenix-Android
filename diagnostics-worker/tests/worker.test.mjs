import {test, beforeEach, afterEach} from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash,generateKeyPairSync,sign,verify,randomBytes,randomUUID} from 'node:crypto';
import {Miniflare,convertV4MiniflareOptions} from 'miniflare';
import {cleanup,storeUpload} from '../src/worker.js';

const origin='https://diagnostics.test', uuid=()=>randomUUID();
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const device=uuid(),other=uuid(),owner=uuid(),otherOwner=uuid();
const keys=new Map([device,other].map(id=>[id,generateKeyPairSync('ec',{namedCurve:'prime256v1'})]));
let mf,db,seen,authCalls;
const encrypted=Buffer.from([0xc1,1,3,0xd2,...randomBytes(120)]);
function proof(method,path,body,who=device,uploadNonce='',environment='production'){
  const salt=randomBytes(32).toString('base64url');
  const nonce=hash(JSON.stringify(['phoenix-diagnostics-v1',origin,method,path,hash(body),salt,uploadNonce]));
  const timestamp=Math.floor(Date.now()/1000).toString();
  const message=JSON.stringify(['phoenix-device-request-v1',environment,'POST',(environment==='preview'?'https://preview.epassport-simulator.de':'https://epassport-simulator.de')+'/api/auth/device/status',who,nonce,Number(timestamp),hash('{}')]);
  return {'authorization':'PhoenixDevice '+who,'x-phoenix-nonce':nonce,'x-phoenix-timestamp':timestamp,
    'x-phoenix-signature':sign('sha256',Buffer.from(message),keys.get(who).privateKey).toString('base64'),
    'x-phoenix-diagnostics-environment':environment,'x-phoenix-diagnostics-salt':salt,'x-report-nonce':uploadNonce};
}
async function call(method,path,body='',who=device,nonce='',extra={}){
  const bytes=typeof body==='string'?Buffer.from(body):body;
  return mf.dispatchFetch(origin+path,{method,headers:{...proof(method,path,bytes,who,nonce),
    ...(method==='POST'?{'content-type':'application/json'}:method==='PUT'?{'content-type':'application/pgp-encrypted'}:{}),...extra},
    ...(bytes.length?{body:bytes}:{})});
}
async function reserve(who=device,requestId=uuid()){
  const response=await call('POST','/v1/nfc-reports',JSON.stringify({request_id:requestId}),who);
  assert.equal(response.status,201);return response.json();
}
beforeEach(async()=>{
  seen=new Set();authCalls=0;
  mf=new Miniflare(convertV4MiniflareOptions({modules:true,scriptPath:new URL('../src/worker.js',import.meta.url).pathname,
    compatibilityDate:'2026-10-24',bindings:{PUBLIC_ORIGIN:origin,AUTH_ENVIRONMENT:'production',OPERATOR_TOKEN_SHA256:hash('A'.repeat(43))},
    d1Databases:['DB'],
    ratelimits:{AUTH_LIMITER:{namespace_id:'73692341',simple:{limit:10000,period:60}}},
    outboundService:async request=>{
      authCalls++;
      assert(['https://epassport-simulator.de/api/auth/device/status','https://preview.epassport-simulator.de/api/auth/device/status'].includes(request.url));
      const environment=request.url.includes('://preview.')?'preview':'production';
      assert.equal(request.method,'POST');assert.equal(await request.text(),'{}');
      const h=request.headers,id=h.get('authorization')?.slice('PhoenixDevice '.length);
      const nonce=h.get('x-phoenix-nonce'),timestamp=Number(h.get('x-phoenix-timestamp'));
      const message=JSON.stringify(['phoenix-device-request-v1',environment,'POST',request.url,id,nonce,timestamp,hash('{}')]);
      const valid=keys.has(id)&&!seen.has(nonce)&&Math.abs(Date.now()/1000-timestamp)<61&&
        verify('sha256',Buffer.from(message),keys.get(id).publicKey,Buffer.from(h.get('x-phoenix-signature')||'','base64'));
      if(!valid)return Response.json({error:'denied'},{status:401});
      seen.add(nonce);return Response.json({authenticated:true,device_id:id,user_id:id===device?owner:otherOwner});
    }}));
  db=await mf.getD1Database('DB');
  const migration=await readFile(new URL('../migrations/0001_reports.sql',import.meta.url),'utf8');
  for(const statement of migration.split(';').map(x=>x.trim()).filter(Boolean))await db.prepare(statement).run();
});
afterEach(async()=>{await mf?.dispose();});

test('owner uploads, reads and deletes ciphertext; other account never gains access',async()=>{
  const offer=await reserve(),path='/v1/nfc-reports/'+offer.id;
  assert.equal((await call('PUT',path,encrypted,other,offer.nonce)).status,404);
  assert.equal((await call('PUT',path,encrypted,device,offer.nonce)).status,200);
  const retry=await call('PUT',path,encrypted,device,offer.nonce);assert.equal(retry.status,200);
  assert.equal((await call('PUT',path,Buffer.concat([encrypted,Buffer.from([1])]),device,offer.nonce)).status,409);
  assert.equal((await call('GET',path,'',other)).status,404);
  assert.equal((await call('DELETE',path,'',other)).status,404);
  const got=await call('GET',path);assert.equal(got.status,200);
  assert.equal(got.headers.get('cache-control'),'private, no-store');
  assert.deepEqual(Buffer.from(await got.arrayBuffer()),encrypted);
  const listing=await (await call('GET','/v1/nfc-reports')).json();assert.equal(listing.reports.length,1);
  assert.equal(JSON.stringify(listing).includes('device_id'),false);
  assert.equal((await call('DELETE',path)).status,200);
  assert.equal((await call('GET',path)).status,404);
  assert.equal((await db.prepare('SELECT payload FROM reports WHERE id=?').bind(offer.id).first()).payload,null);
});

test('request binding rejects body/header/path changes before upstream work and replay is denied',async()=>{
  const path='/v1/nfc-reports',body=JSON.stringify({request_id:uuid()});
  const headers={...proof('POST',path,body),'content-type':'application/json'};
  assert.equal((await mf.dispatchFetch(origin+path,{method:'POST',headers,body:body+' '})).status,401);
  assert.equal(authCalls,0);
  assert.equal((await mf.dispatchFetch(origin+path,{method:'POST',headers,body})).status,201);
  assert.equal((await mf.dispatchFetch(origin+path,{method:'POST',headers,body})).status,401);
  assert.equal((await call('POST',path+'?redirect=evil',body)).status,400);
  assert.equal((await call('POST','/v1/%6efc-reports',body)).status,400);
  assert.equal((await call('POST',path,body,device,'',{'authorization':'Bearer secret'})).status,401);
});

test('reservation retry is idempotent and quota remains atomic under concurrent requests',async()=>{
  const id=uuid();const first=await reserve(device,id);const retry=await reserve(device,id);
  assert.equal(first.id,retry.id);assert.equal(first.nonce,retry.nonce);
  const results=await Promise.all(Array.from({length:24},()=>call('POST','/v1/nfc-reports',JSON.stringify({request_id:uuid()}))));
  assert.equal(results.filter(x=>x.status===201).length,19);
  assert.equal(results.filter(x=>x.status===429).length,5);
});

test('size, schema, nonce and expired tickets fail closed',async()=>{
  assert.equal((await call('POST','/v1/nfc-reports','{"request_id":"../bad","email":"no"}')).status,400);
  assert.equal((await call('POST','/v1/nfc-reports','x'.repeat(1025))).status,413);
  const offer=await reserve(),path='/v1/nfc-reports/'+offer.id;
  assert.equal((await call('PUT',path,encrypted,device,randomBytes(32).toString('base64url'))).status,403);
  assert.equal((await call('PUT',path,Buffer.alloc(2*1024*1024+1),device,offer.nonce)).status,413);
  await db.prepare('UPDATE reports SET ticket_expires=0 WHERE id=?').bind(offer.id).run();
  assert.equal((await call('PUT',path,encrypted,device,offer.nonce)).status,410);
});

test('deletion fences a delayed atomic D1 upload',async()=>{
  const offer=await reserve(),path='/v1/nfc-reports/'+offer.id;
  const row=await db.prepare('SELECT * FROM reports WHERE id=?').bind(offer.id).first();
  let release;const gate=new Promise(resolve=>{release=resolve;});
  const delayed={prepare:sql=>({bind:(...args)=>({run:async()=>{await gate;return db.prepare(sql).bind(...args).run();}})})};
  const storing=storeUpload({DB:delayed},row,encrypted,hash(encrypted));
  assert.equal((await call('DELETE',path)).status,200);release();assert.equal(await storing,false);
  assert.equal((await db.prepare('SELECT payload FROM reports WHERE id=?').bind(row.id).first()).payload,null);
});

test('failed D1 write leaves no partial upload; expiry denies reads before cleanup',async()=>{
  const offer=await reserve();const row=await db.prepare('SELECT * FROM reports WHERE id=?').bind(offer.id).first();
  await assert.rejects(storeUpload({DB:{prepare:()=>{throw Error('synthetic failure');}}},row,encrypted,hash(encrypted)));
  assert.equal((await db.prepare('SELECT state FROM reports WHERE id=?').bind(offer.id).first()).state,'reserved');
  assert.equal((await call('PUT','/v1/nfc-reports/'+offer.id,encrypted,device,offer.nonce)).status,200);
  await db.prepare('UPDATE reports SET expires=0 WHERE id=?').bind(offer.id).run();
  assert.equal((await call('GET','/v1/nfc-reports/'+offer.id)).status,404);
  await cleanup({DB:db});assert.equal((await db.prepare('SELECT payload FROM reports WHERE id=?').bind(offer.id).first()).payload,null);
});

test('operator reads all owners without receiving owner identities and cannot mutate reports',async()=>{
  const first=await reserve(),second=await reserve(other);
  for(const [offer,who] of [[first,device],[second,other]])assert.equal((await call('PUT','/v1/nfc-reports/'+offer.id,encrypted,who,offer.nonce)).status,200);
  const base='/v1/operator/nfc-reports';
  const get=(path,method='GET',token='A'.repeat(43))=>mf.dispatchFetch(origin+path,{method,headers:{authorization:'Bearer '+token}});
  assert.equal((await get(base,'GET','B'.repeat(43))).status,401);
  assert.equal((await call('GET',base)).status,401);
  const list=await (await get(base)).json();assert.equal(list.reports.length,2);
  assert.equal(JSON.stringify(list).includes(owner),false);
  assert.deepEqual(Buffer.from(await (await get(base+'/'+second.id)).arrayBuffer()),encrypted);
  assert.equal((await get(base+'/'+first.id,'DELETE')).status,405);
  assert.equal((await get(base+'?after=invalid')).status,400);
  const cursor=list.reports[0].id;
  assert.equal((await (await get(base+'?after='+cursor)).json()).reports.length,1);
});

test('cleanup progresses beyond a hundred old tombstones',async()=>{
  const now=Math.floor(Date.now()/1000);
  for(let i=0;i<110;i++)await db.prepare("INSERT INTO reports(id,owner,request_id,nonce,created,expires,ticket_expires,state) VALUES(?,?,?,?,?,?,?,'deleting')").bind(uuid(),owner,uuid(),'A'.repeat(43),now-2*86400,now+100,now+100).run();
  const offer=await reserve();await call('PUT','/v1/nfc-reports/'+offer.id,encrypted,device,offer.nonce);
  await db.prepare('UPDATE reports SET expires=? WHERE id=?').bind(now-1,offer.id).run();
  await cleanup({DB:db},now);
  assert.equal((await db.prepare('SELECT payload FROM reports WHERE id=?').bind(offer.id).first()).payload,null);
});


test('global capacity and daily reservation ceilings refuse uploads instead of requiring paid storage',async()=>{
  const now=Math.floor(Date.now()/1000);
  for(let i=0;i<35;i++)await db.prepare("INSERT INTO reports(id,owner,request_id,nonce,created,expires,ticket_expires,state) VALUES(?,?,?,?,?,?,?,'reserved')").bind(uuid(),uuid(),uuid(),'A'.repeat(43),now,now+86400,now+300).run();
  assert.equal((await call('POST','/v1/nfc-reports',JSON.stringify({request_id:uuid()}))).status,429);
  await db.prepare("UPDATE reports SET state='deleting'").run();
  assert.equal((await call('POST','/v1/nfc-reports',JSON.stringify({request_id:uuid()}))).status,201);
  for(let i=0;i<164;i++)await db.prepare("INSERT INTO reports(id,owner,request_id,nonce,created,expires,ticket_expires,state) VALUES(?,?,?,?,?,?,?,'deleting')").bind(uuid(),uuid(),uuid(),'A'.repeat(43),now,now+86400,now+300).run();
  assert.equal((await call('POST','/v1/nfc-reports',JSON.stringify({request_id:uuid()}))).status,429);
});


test('D1 stores a full-sized bounded ciphertext and listings never select its payload',async()=>{
 const offer=await reserve(),path='/v1/nfc-reports/'+offer.id;
 const bytes=Buffer.alloc(1900000,0x5a);bytes[0]=0xc1;
 assert.equal((await call('PUT',path,bytes,device,offer.nonce)).status,200);
 const listing=await call('GET','/v1/nfc-reports');assert.ok((await listing.text()).length<1000);
 const got=await call('GET',path);assert.equal(hash(Buffer.from(await got.arrayBuffer())),hash(bytes));
 assert.equal((await call('DELETE',path)).status,200);
});

 test('one service accepts both environments and prevents cross-environment ownership and signature substitution',async()=>{
  const send=(method,path,body='',nonce='')=>mf.dispatchFetch(origin+path,{method,headers:{...proof(method,path,body,device,nonce,'preview'),...(method==='POST'?{'content-type':'application/json'}:method==='PUT'?{'content-type':'application/pgp-encrypted'}:{})},...(body.length?{body}:{})});
  const reservation=await send('POST','/v1/nfc-reports',JSON.stringify({request_id:uuid()}));assert.equal(reservation.status,201);
  const offer=await reservation.json(),path='/v1/nfc-reports/'+offer.id;
  assert.equal((await send('PUT',path,encrypted,offer.nonce)).status,200);
  assert.deepEqual(Buffer.from(await(await send('GET',path)).arrayBuffer()),encrypted);
  assert.equal((await call('GET',path)).status,404);
  assert.equal((await call('DELETE',path)).status,404);
  assert.equal((await call('GET','/v1/nfc-reports','',''+device,'',{'x-phoenix-diagnostics-environment':'preview'})).status,401);
  assert.equal((await call('GET','/v1/nfc-reports','',''+device,'',{'x-phoenix-diagnostics-environment':'invalid'})).status,401);
  assert.equal((await send('DELETE',path)).status,200);
  const production=await reserve();assert.equal((await send('GET','/v1/nfc-reports/'+production.id)).status,404);
 });
