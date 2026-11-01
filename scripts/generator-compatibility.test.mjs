import test from 'node:test';
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {execFileSync} from 'node:child_process';
import {readFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import openapiTS,{astToString} from 'openapi-typescript';
const root=createRequire(import.meta.url),generator=createRequire(root.resolve('openapi-typescript'));
const manifest=generator.resolve('@redocly/openapi-core/package.json');
const utils=generator(join(dirname(manifest),'lib/utils.js'));
test('patched matching attaches fixture headers only to matching hosts and paths',async()=>{
  const cases=[['https://supplier.example/v1/offers',true],['https://supplier.example/v1/search',true],['https://supplier.example/private',false],['https://supplier.example.evil.test/v1/offers',false],['https://other.example/v1/offers',false]];
  for(const [url,match] of cases){let observed;
    await utils.readFileFromUrl(url,{headers:[{matches:'https://supplier.example/v1/{offers,search}',name:'X-Fixture',value:'not-a-secret'}],customFetch:async(_url,options)=>{observed=options.headers;return new Response('{}',{headers:{'Content-Type':'application/json'}});}});
    assert.equal(observed['X-Fixture'],match?'not-a-secret':undefined);
  }
});
test('OpenAPI 3.1 nullable unions, required fields and escaped references still generate types',async()=>{
  const schema={openapi:'3.1.0',info:{title:'Generator regression',version:'1'},paths:{'/fixtures':{get:{operationId:'getFixtures',responses:{200:{description:'Fixture',content:{'application/json':{schema:{$ref:'#/components/schemas/Fixture~1Name'}}}}}}}},components:{schemas:{'Fixture/Name':{type:'object',required:['id','state'],properties:{id:{type:'string'},state:{type:['string','null'],enum:['READY',null]},optional:{type:'integer'}}}}}};
  const output=astToString(await openapiTS(schema));
  assert.match(output,/id: string;/);assert.match(output,/state: "READY" \| null;/);assert.match(output,/optional\?: number;/);assert.match(output,/components\["schemas"\]\["Fixture\/Name"\]/);
});
test('compatibility patch is reproducible and idempotent',()=>{
  const file=join(dirname(manifest),'lib/utils.js'),before=readFileSync(file,'utf8');
  execFileSync(process.execPath,['scripts/patch-redocly-minimatch.cjs']);
  assert.equal(readFileSync(file,'utf8'),before);
});
test('unexpected upstream source is rejected without modifying the installation',()=>{
  const file=join(dirname(manifest),'lib/utils.js'),before=readFileSync(file,'utf8');
  const {adaptSource}=root('./patch-redocly-minimatch.cjs');
  assert.throws(()=>adaptSource(before+'\n// unexpected change'),/Unexpected upstream/);
  assert.equal(readFileSync(file,'utf8'),before);
});
