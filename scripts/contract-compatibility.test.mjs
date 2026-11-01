import {test} from 'node:test';
import assert from 'node:assert/strict';
import {incompatible} from './contract-compatibility.mjs';
const prior={paths:{'/v1/items':{get:{responses:{'200':{description:'ok'}}}}},components:{schemas:{Item:{type:'object',required:['id'],properties:{id:{type:'string'},status:{type:'string',enum:['A','B']}}}}}};
test('removed operations and narrowed enums fail the compatibility gate',()=>{
  const removed=structuredClone(prior);delete removed.paths['/v1/items'].get;assert.ok(incompatible(prior,removed).length);
  const narrowed=structuredClone(prior);narrowed.components.schemas.Item.properties.status.enum=['A'];assert.ok(incompatible(prior,narrowed).length);
});
test('new required properties and newly introduced constraints fail',()=>{
  const required=structuredClone(prior);required.components.schemas.Item.required.push('status');assert.ok(incompatible(prior,required).length);
  const constrained=structuredClone(prior);constrained.components.schemas.Item.properties.id.maxLength=3;assert.ok(incompatible(prior,constrained).length);
});
test('optional additions and documentation edits remain compatible',()=>{
  const next=structuredClone(prior);next.components.schemas.Item.properties.label={type:'string'};next.paths['/v1/items'].get.responses['200'].description='Updated documentation';
  assert.deepEqual(incompatible(prior,next),[]);
});
test('new methods on slash-containing paths are additive, shared constraints and changed methods are not',()=>{
  const next=structuredClone(prior);next.paths['/v1/items'].post={requestBody:{required:true},responses:{'201':{description:'created'}}};
  assert.deepEqual(incompatible(prior,next),[]);
  const shared=structuredClone(next);shared.paths['/v1/items'].parameters=[{name:'tenant',in:'query',required:true,schema:{type:'string'}}];
  assert.ok(incompatible(prior,shared).length);
  const changed=structuredClone(next);changed.paths['/v1/items'].get.security=[{newRequiredAuth:[]}];assert.ok(incompatible(prior,changed).length);
  const removed=structuredClone(next);delete removed.paths['/v1/items'].get;assert.ok(incompatible(prior,removed).length);
});
test('new security definitions are additive but relaxing existing security is rejected',()=>{
  const secured={...structuredClone(prior),security:[{bearer:[]}]};
  const next=structuredClone(secured);next.components.securitySchemes={bearer:{type:'http',scheme:'bearer'}};
  assert.deepEqual(incompatible(secured,next),[]);
  next.security=[];assert.ok(incompatible(secured,next).length);
});
test('optional parameters are additive; removed, narrowed or newly required parameters are rejected',()=>{
  const base={parameters:[{name:'filter',in:'query',required:false,schema:{type:'string',enum:['A','B']}}]};
  const optional=structuredClone(base);optional.parameters.push({name:'bookingId',in:'query',required:false,schema:{type:'string'}});
  assert.deepEqual(incompatible(base,optional,'/operation'),[]);
  const required=structuredClone(optional);required.parameters[1].required=true;assert.ok(incompatible(base,required,'/operation').length);
  const narrowed=structuredClone(base);narrowed.parameters[0].schema.enum=['A'];assert.ok(incompatible(base,narrowed,'/operation').length);
  assert.ok(incompatible(base,{parameters:[]},'/operation').length);
});
