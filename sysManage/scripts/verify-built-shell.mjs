import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const indexHtmlPath = fileURLToPath(new URL('../dist/index.html', import.meta.url));
const indexHtml = readFileSync(indexHtmlPath, 'utf8');

assert.match(indexHtml, /<title>Alicia 云盘后台<\/title>/, 'cloud console shell must keep the Alicia cloud console title');
assert.match(
  indexHtml,
  /src="\/console\/cloud\/assets\/index-[^"]+\.js"/,
  'cloud console shell must load its JavaScript from /console/cloud/assets/',
);
assert.match(
  indexHtml,
  /href="\/console\/cloud\/assets\/index-[^"]+\.css"/,
  'cloud console shell must load its stylesheet from /console/cloud/assets/',
);
assert.match(indexHtml, /href="\/console\/cloud\/favicon-32\.png/, 'cloud console shell must use mounted icons');
assert.doesNotMatch(indexHtml, /(src|href)="\/assets\//, 'cloud console shell must not use root asset URLs');
assert.doesNotMatch(indexHtml, /\/cloudPan\/assets\//, 'cloud console shell must not use cloud web assets');

console.log('[OK] cloud console built shell verified');
