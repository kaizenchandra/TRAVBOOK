// Temporary, fail-closed compatibility patch for the pinned generator dependency.
// Actual minimatch 11.0.1 is installed by npm overrides; this adapts its named export.
const {createRequire}=require('node:module');
const {readFileSync,writeFileSync}=require('node:fs');
const {dirname,join}=require('node:path');
const {createHash}=require('node:crypto');
const original='const minimatch = require("minimatch");';
const replacement='const { minimatch } = require("minimatch");';
function adaptSource(installed){
  const source=installed.includes(replacement)?installed.replace(replacement,original):installed;
  if(createHash('sha256').update(source).digest('hex')!=='896a9acce92e3e5905aa267e895985d57888a87f500d58c29da9c8f9d488ff1c')
    throw new Error('Unexpected upstream Redocly source; refusing to patch.');
  return source.replace(original,replacement);
}
module.exports={adaptSource};
if(require.main===module){
  const generator=createRequire(require.resolve('openapi-typescript'));
  const manifest=generator.resolve('@redocly/openapi-core/package.json');
  if(JSON.parse(readFileSync(manifest,'utf8')).version!=='1.34.22')throw new Error('Review the Redocly compatibility patch for the new dependency version.');
  const file=join(dirname(manifest),'lib/utils.js'),dependency=createRequire(file);
  if(dependency('minimatch/package.json').version!=='11.0.1'||typeof dependency('minimatch').minimatch!=='function')
    throw new Error('Expected the audited minimatch 11.0.1 named export.');
  const installed=readFileSync(file,'utf8'),patched=adaptSource(installed);
  if(installed!==patched)writeFileSync(file,patched);
  console.log('Verified Redocly 1.34.22 compatibility with minimatch 11.0.1.');
}
