import Ajv from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import {readFileSync} from 'node:fs';
const ajv=new Ajv({strict:true,allErrors:true});addFormats(ajv);
const validate=ajv.compile(JSON.parse(readFileSync(new URL('../contracts/events/railtickets.v1.schema.json',import.meta.url))));
const text=readFileSync(0,'utf8').trim();let count=0;
for(const line of text?text.split('\n'):[]){if(!validate(JSON.parse(line)))throw new Error(JSON.stringify(validate.errors));count++;}
if(!count)throw new Error('No rail events supplied; no validation evidence.');
console.log(`${count} rail lifecycle event envelopes validated; no passenger fields permitted.`);
