import {readFileSync} from 'node:fs';
import assert from 'node:assert/strict';
import Ajv from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
const ajv=new Ajv({strict:false,allErrors:true});addFormats(ajv);
const validate=ajv.compile(JSON.parse(readFileSync('contracts/events/coupon.v1.schema.json','utf8')));
const lines=readFileSync(0,'utf8').trim().split('\n').filter(Boolean);
assert.ok(lines.length>0,'Expected persisted coupon events from completed browser checkout.');
const ids=new Set(),awards=new Set();
for(const line of lines){const event=JSON.parse(line);assert.ok(validate(event),JSON.stringify(validate.errors));assert.ok(!ids.has(event.eventId),'Duplicate event ID');assert.ok(!awards.has(event.aggregateId),'Award redeemed twice');assert.equal(event.causationId,event.payload.reservationId);ids.add(event.eventId);awards.add(event.aggregateId);}
console.log(`${lines.length} persisted coupon redemption events validate with unique awards.`);
