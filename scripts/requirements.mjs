import { readFileSync, writeFileSync, mkdirSync } from 'node:fs';
import { resolveRequirementMappings } from './requirement-mappings.mjs';
const rows = [];
for (const [source, prefix] of [['FRONTEND-REQUIREMENT-SPECIFICATION.MD','FE'], ['BACKEND-REQUIREMENT-SPECIFICATION.MD','BE']]) {
  const lines = readFileSync(source, 'utf8').split('\n');
  let section = '';
  lines.forEach((line, index) => {
    if (line.startsWith('#')) section = line.replace(/^#+\s*/, '');
    const match = line.match(/^\| ([A-Z]+-\d+) \| (.*?) \| (.*?) \|$/);
    if (match) {
      const [, originalId, text, priorityOrTarget] = match;
      const nfr = originalId.startsWith('NFR-');
      rows.push({ id: nfr ? originalId : `${prefix}-${originalId}`, originalId, source, line: index + 1,
        section, requirement: nfr ? `${text}: ${priorityOrTarget}` : text,
        priority: nfr ? 'Not assigned in source; mandatory NFR baseline' : priorityOrTarget,
        acceptance: nfr ? 'Proposed target; requires measured release evidence' : 'Source requirement plus applicable source acceptance scenarios and cross-cutting constraints' });
    }
    const ac = line.match(/^### (AC-\d+) — (.*)$/);
    if (ac) rows.push({id:`FE-${ac[1]}`,originalId:ac[1], source,line:index+1,section,
      requirement:ac[2],priority:'Acceptance scenario; inherits linked requirements',acceptance:lines[index+1]});
  });
}
const configured = JSON.parse(readFileSync('docs/requirements/mappings.json','utf8'));
const mappings = resolveRequirementMappings(rows, configured);
const columns = ['Business requirement ID','Original ID','Original frontend IDs','Original backend IDs','Source','Priority','Business outcome','Acceptance','UI screen','Component','Hook','API','Use case','Aggregate','Inbound port','Outbound port','Adapter','Database/migration','Event','Security','Failure / idempotency','Tests / evidence','Status'];
const pending = 'Pending design; requirement retained';
const body = rows.map(row => {
  const map = mappings[row.id] ?? {};
  const related=[row.id,...(map.related ?? [])];
  const frontend=related.filter(id=>id.startsWith('FE-')).map(id=>id.slice(3)).join(', ') || 'Pending cross-source mapping';
  const backend=related.filter(id=>id.startsWith('BE-')||id.startsWith('NFR-')).map(id=>id.replace(/^BE-/, '')).join(', ') || 'Pending cross-source mapping';
  const values = [row.id,row.originalId,frontend,backend,`[${row.source}:${row.line}](../../${row.source}#L${row.line})`,row.priority,row.requirement,map.acceptance ?? row.acceptance,
    ...['route','component','hook','api','usecase','aggregate','inbound','outbound','adapter','database','event','security','failure','tests'].map(k => map[k] ?? pending),map.status ?? 'Not started'];
  return `| ${values.map(v => String(v).replaceAll('|','\\|').replaceAll('\n',' ')).join(' | ')} |`;
});
const outputs = {
  'docs/requirements/register.json':JSON.stringify(rows,null,2)+'\n',
  'docs/requirements/rtm.md':`# Requirements traceability matrix\n\nGenerated from both authoritative specifications by \`npm run requirements:generate\`. ${rows.length} source requirements/scenarios retained. Original wording and priorities remain in the register. Mapping is progressive: pending cells are unresolved implementation work, never a completed design. Full source sections remain binding, including prose requirements without IDs. Evidence is MOCK unless explicitly stated otherwise.\n\n| ${columns.join(' | ')} |\n| ${columns.map(()=>'---').join(' | ')} |\n${body.join('\n')}\n`
};
mkdirSync('docs/requirements',{recursive:true});
for (const [file, contents] of Object.entries(outputs)) {
  if (process.argv.includes('--check')) { if(readFileSync(file,'utf8')!==contents) throw new Error(`Requirement drift: ${file}`); }
  else writeFileSync(file,contents);
}
console.log(`${rows.length} source requirements/scenarios ${process.argv.includes('--check')?'checked':'registered'}.`);
