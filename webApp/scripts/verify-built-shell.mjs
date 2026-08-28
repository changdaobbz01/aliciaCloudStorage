import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const indexHtmlPath = fileURLToPath(new URL('../dist/index.html', import.meta.url));
const indexHtml = readFileSync(indexHtmlPath, 'utf8');

assert.match(indexHtml, /<title>Alicia 云盘<\/title>/, 'cloud web shell must keep the Alicia cloud title');
assert.match(
  indexHtml,
  /src="\/cloudPan\/assets\/index-[^"]+\.js"/,
  'cloud web shell must load its JavaScript from /cloudPan/assets/',
);
assert.match(
  indexHtml,
  /href="\/cloudPan\/assets\/index-[^"]+\.css"/,
  'cloud web shell must load its stylesheet from /cloudPan/assets/',
);
assert.match(indexHtml, /href="\/cloudPan\/favicon-32\.png/, 'cloud web shell must use mounted icons');
assert.doesNotMatch(indexHtml, /(src|href)="\/assets\//, 'cloud web shell must not use root asset URLs');
assert.doesNotMatch(indexHtml, /\/console\/cloud\/assets\//, 'cloud web shell must not use cloud console assets');

console.log('[OK] cloud web built shell verified');
