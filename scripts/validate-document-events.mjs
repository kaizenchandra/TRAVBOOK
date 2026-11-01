import {readFileSync} from 'node:fs';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
const ajv=new Ajv2020({allErrors:true});addFormats(ajv);
const validate=ajv.compile(JSON.parse(readFileSync(new URL('../contracts/events/document-v1.schema.json',import.meta.url),'utf8')));
const events=readFileSync(0,'utf8').split('\n').filter(Boolean).map(line=>JSON.parse(line));
if(events.length===0)throw new Error('No persisted document events supplied.');
for(const event of events)if(!validate(event))throw new Error(JSON.stringify(validate.errors));
console.log(`${events.length} persisted document events match schema v1.`);
