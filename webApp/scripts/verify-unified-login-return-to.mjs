import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const expectedBasePath = '/cloudPan/';

function loadModule() {
  const appPathsSource = fs
    .readFileSync(path.join(rootDir, 'src', 'lib', 'appPaths.ts'), 'utf8')
    .replaceAll('import.meta.env.BASE_URL', JSON.stringify(expectedBasePath));
  const loginSource = fs
    .readFileSync(path.join(rootDir, 'src', 'lib', 'unifiedLogin.ts'), 'utf8')
    .replace(/^import\s+\{\s*appPath\s*\}\s+from\s+'\.\/appPaths';\r?\n/m, '');
  const source = `${appPathsSource}\n${loginSource}`;
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  });
  const module = { exports: {} };

  vm.runInNewContext(
    compiled.outputText,
    {
      exports: module.exports,
      module,
      URL,
    },
    {
      filename: path.join(rootDir, 'src', 'lib', 'unifiedLogin.ts'),
    },
  );

  return module.exports;
}

const { buildUnifiedLoginUrl, cloudReturnTo } = loadModule();

const returnToCases = [
  [['', '', ''], '/cloudPan/'],
  [['/', '', ''], '/cloudPan/'],
  [['/cloudPan', '?from=login', ''], '/cloudPan/?from=login'],
  [['/share/abc123', '?mode=save', '#files'], '/cloudPan/share/abc123?mode=save#files'],
  [['/app-download', '?share=abc123', ''], '/cloudPan/app-download?share=abc123'],
  [['/cloudPan/share/abc123', '?p=1', 'bad-hash'], '/cloudPan/share/abc123?p=1'],
  [['/login', '', ''], '/cloudPan/'],
  [['/cloudPan/login', '', ''], '/cloudPan/'],
  [['/api/storage/overview', '', ''], '/cloudPan/'],
  [['/console/cloud/', '', ''], '/cloudPan/'],
  [['//evil.example/cloudPan/', '', ''], '/cloudPan/'],
];

for (const [args, expected] of returnToCases) {
  assert.equal(cloudReturnTo(...args), expected, `cloudReturnTo(${args.map(String).join(', ')})`);
}

assert.equal(
  buildUnifiedLoginUrl(cloudReturnTo('/share/abc123'), 'login-required'),
  '/login?returnTo=%2FcloudPan%2Fshare%2Fabc123&reason=login-required',
);
assert.equal(
  buildUnifiedLoginUrl('/api/storage/overview', 'session-expired'),
  '/login?returnTo=%2FcloudPan%2F&reason=session-expired',
);
assert.equal(buildUnifiedLoginUrl('/cloudPan?from=login'), '/login?returnTo=%2FcloudPan%2F%3Ffrom%3Dlogin');
assert.equal(buildUnifiedLoginUrl('/console/cloud/'), '/login?returnTo=%2FcloudPan%2F');

console.log('[OK] cloud web unified login returnTo verified');
