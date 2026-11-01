import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolveRequirementMappings } from './requirement-mappings.mjs';

test('standalone mappings retain implementation evidence without an unrelated group', () => {
  const mapping = {status: 'In progress', tests: 'Bus booking browser evidence'};
  assert.deepEqual(resolveRequirementMappings([{id: 'FE-BUS-01'}], {
    overrides: {'FE-BUS-01': mapping}
  })['FE-BUS-01'], {related: [], ...mapping});
});

test('overrides win over the latest group while retaining cross-source links', () => {
  const rows = [{id: 'FE-BUS-01'}, {id: 'BE-BUS-001'}];
  const mappings = resolveRequirementMappings(rows, {
    groups: [
      {ids: rows.map(row => row.id), mapping: {status: 'Not started', api: 'old'}},
      {ids: rows.map(row => row.id), mapping: {status: 'In progress', api: 'current'}}
    ],
    overrides: {'FE-BUS-01': {tests: 'executed evidence'}}
  });
  assert.deepEqual(mappings['FE-BUS-01'], {
    related: ['BE-BUS-001'], status: 'In progress', api: 'current', tests: 'executed evidence'
  });
});

test('unknown source IDs fail for groups and standalone overrides', () => {
  for (const config of [
    {groups: [{ids: ['FE-UNKNOWN-01'], mapping: {}}]},
    {overrides: {'FE-UNKNOWN-01': {status: 'Verified'}}}
  ]) assert.throws(() => resolveRequirementMappings([], config), /absent source ID/);
});

test('every configured repository mapping is represented in the resolved register', () => {
  const rows = JSON.parse(readFileSync(new URL('../docs/requirements/register.json', import.meta.url)));
  const config = JSON.parse(readFileSync(new URL('../docs/requirements/mappings.json', import.meta.url)));
  const mappings = resolveRequirementMappings(rows, config);
  for (const id of new Set([...config.groups.flatMap(group => group.ids), ...Object.keys(config.overrides)])) {
    assert.ok(mappings[id], `Dropped requirement mapping: ${id}`);
    if (config.overrides[id]) for (const [key, value] of Object.entries(config.overrides[id])) {
      assert.deepEqual(mappings[id][key], value, `Lost override ${id}.${key}`);
    }
  }
});
