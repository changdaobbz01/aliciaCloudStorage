import assert from 'node:assert/strict';
import { readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';

const assetsDir = fileURLToPath(new URL('../dist/assets/', import.meta.url));
const maxChunkBytes = 500 * 1024;

const jsChunks = readdirSync(assetsDir)
  .filter((fileName) => fileName.endsWith('.js'))
  .map((fileName) => {
    const filePath = join(assetsDir, fileName);
    return {
      fileName,
      size: statSync(filePath).size,
    };
  })
  .sort((left, right) => right.size - left.size);

assert.ok(jsChunks.length > 0, 'build output must contain JavaScript chunks');

const oversizedChunks = jsChunks.filter((chunk) => chunk.size > maxChunkBytes);

assert.deepEqual(
  oversizedChunks,
  [],
  `JavaScript chunks must stay below ${formatBytes(maxChunkBytes)} before gzip`,
);

const largestChunk = jsChunks[0];
console.log(`[OK] cloud web bundle size verified, largest JS chunk ${largestChunk.fileName} ${formatBytes(largestChunk.size)}`);

function formatBytes(bytes) {
  return `${(bytes / 1024).toFixed(1)} KiB`;
}
