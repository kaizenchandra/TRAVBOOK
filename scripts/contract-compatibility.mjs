import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';
const documentation=new Set(['description','summary','title','example','examples','externalDocs','contact','license','tags']);
const methods=new Set(['get','put','post','delete','options','head','patch','trace']);
const segment=value=>value.replaceAll('~','~0').replaceAll('/','~1');
// Conservative gate: existing machine contracts cannot change without an explicit version transition.
// Allows new operations/schemas, optional properties/query headers and additional response codes.
export function incompatible(previous,next,path='') {
  if(Array.isArray(previous)&&Array.isArray(next)&&path.endsWith('/parameters')) {
    const identity=p=>p.$ref??p.in+':'+p.name;
    const indexed=new Map(next.map(p=>[identity(p),p]));
    if(indexed.size!==next.length)return [path];
    const errors=[];
    for(const parameter of previous) {
      const key=identity(parameter),match=indexed.get(key);
      if(!match)errors.push(path+'/'+key);else errors.push(...incompatible(parameter,match,path+'/'+key));
    }
    for(const parameter of next)if(!previous.some(p=>identity(p)===identity(parameter))&&
      (parameter.$ref||parameter.required===true||parameter.in==='path'))errors.push(path+'/'+identity(parameter));
    return errors;
  }
  if(Array.isArray(previous)||previous===null||typeof previous!=='object') return JSON.stringify(previous)===JSON.stringify(next)?[]:[path];
  if(next===null||typeof next!=='object'||Array.isArray(next))return [path];
  const errors=[];
  for(const [key,value] of Object.entries(previous)) {
    if(documentation.has(key)||key==='servers'||(path==='/info'&&key==='version'))continue;
    if(!(key in next))errors.push(path+'/'+segment(key));else errors.push(...incompatible(value,next[key],path+'/'+segment(key)));
  }
  const additionsAllowed=path==='/paths'||path==='/components'||/^\/components\/(schemas|parameters|securitySchemes|responses|requestBodies|headers)$/.test(path)||path.endsWith('/properties')||path.endsWith('/responses');
  const pathItem=/^\/paths\/[^/]+$/.test(path);
  if(!additionsAllowed)for(const key of Object.keys(next))if(!(key in previous)&&!documentation.has(key)&&!(pathItem&&methods.has(key)))errors.push(path+'/'+segment(key));
  return errors;
}
if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href) {
  const [baseline,current='contracts/openapi.json']=process.argv.slice(2);
  if(!baseline)throw new Error('Pass the previous released/base-branch OpenAPI file.');
  const errors=incompatible(JSON.parse(readFileSync(baseline,'utf8')),JSON.parse(readFileSync(current,'utf8')));
  if(errors.length)throw new Error('Contract changes need versioned migration/review: '+errors.join(', '));
  console.log('Existing API contract retained; additive changes only.');
}
