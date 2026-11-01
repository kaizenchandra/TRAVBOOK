import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import Ajv from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';

const source=process.argv[2];
if(!source)throw new Error('Pass the FlightProviderIntegrationTest target/flight-provider-http.jsonl file.');
const api=JSON.parse(readFileSync('contracts/openapi.json','utf8'));
const ajv=new Ajv({strict:false,allErrors:true});addFormats(ajv);
let examples=0;
for(const [name,schema] of Object.entries(api.components.schemas))if(name.startsWith('Fp')&&schema.example){
  const validate=ajv.compile({$ref:'#/components/schemas/'+name,components:api.components});
  assert.ok(validate(schema.example),`${name} example: ${JSON.stringify(validate.errors)}`);examples++;
}
assert.ok(examples>=14);console.log(`${examples} provider request/response examples validate.`);
const operations=Object.entries(api.paths).filter(([path])=>path.startsWith('/api/v1/demo/flight-provider')).flatMap(([path,item])=>Object.entries(item).map(([method,op])=>({path,method,op,regex:new RegExp('^'+path.replace(/\{[^}]+\}/g,'[^/]+')+'$')})));
const evidence=readFileSync(source,'utf8').trim().split('\n').map(line=>JSON.parse(line));
const covered=new Set();let validated=0;
for(const result of evidence){
  const path=result.path.split('?')[0],entry=operations.find(o=>o.method===result.method&&o.regex.test(path));
  if(!entry){assert.ok(result.status>=400,'Uncontracted operation cannot succeed');continue;}
  let response=entry.op.responses[result.status];assert.ok(response,`Undocumented status ${result.status} at ${path}`);
  if(response.$ref)response=api.components.responses[response.$ref.split('/').at(-1)];
  const schema=Object.values(response.content)[0].schema;
  const validate=ajv.compile({...schema,components:api.components});
  assert.ok(validate(result.body),`${entry.method} ${entry.path}: ${JSON.stringify(validate.errors)}`);
  if(result.status<300)covered.add(entry.op.operationId);validated++;
}
for(const entry of operations)assert.ok(covered.has(entry.op.operationId),`No executed success for ${entry.op.operationId}`);
console.log(`${validated} actual HTTP responses match OpenAPI; all ${covered.size} fictional flight-provider operations exercised successfully.`);
const eventFile=process.argv[3];
if(eventFile){
  const validate=ajv.compile(JSON.parse(readFileSync('contracts/events/flight-provider.v1.schema.json','utf8')));
  const versions=new Map();let count=0;
  for(const line of readFileSync(eventFile,'utf8').trim().split('\n')){
    const event=JSON.parse(line);assert.ok(validate(event),JSON.stringify(validate.errors));
    assert.equal(event.aggregateVersion,(versions.get(event.aggregateId)??0)+1,'Event versions must have no gap or duplicate');versions.set(event.aggregateId,event.aggregateVersion);count++;
  }
  assert.ok(count>0);console.log(`${count} persisted provider events match the versioned schema, with contiguous aggregate versions.`);
}
