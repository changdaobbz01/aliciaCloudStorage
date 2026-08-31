import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const expectedBasePath = '/cloudPan/';

async function compileTypeScriptModule(sourceText, exportNames) {
  const typescript = process.env.ALICIA_VERIFY_RETURN_TO_DISABLE_TYPESCRIPT === '1'
    ? null
    : await import('typescript').catch(() => null);
  const ts = typescript?.default ?? typescript;

  if (ts?.transpileModule) {
    return ts.transpileModule(sourceText, {
      compilerOptions: {
        module: ts.ModuleKind.CommonJS,
        target: ts.ScriptTarget.ES2022,
      },
    }).outputText;
  }

  return `${transpileTypeScriptSubset(sourceText)}\nmodule.exports = { ${exportNames.join(', ')} };`;
}

function transpileTypeScriptSubset(sourceText) {
  return sourceText
    .replace(/^export\s+type\s+[A-Za-z_$][\w$]*\s*=\s*[^;]+;\s*/gm, '')
    .replace(/\bexport\s+(?=(?:const|function)\b)/g, '')
    .replace(/([\(,]\s*[A-Za-z_$][\w$]*)\??\s*:\s*[^=,)]+(?=[=,)])/g, '$1')
    .replace(/\)\s*:\s*[A-Za-z_$][\w$<>\[\] |.'"]*\s*\{/g, ') {');
}

async function loadModule() {
  const appPathsSource = fs
    .readFileSync(path.join(rootDir, 'src', 'lib', 'appPaths.ts'), 'utf8')
    .replaceAll('import.meta.env.BASE_URL', JSON.stringify(expectedBasePath));
  const loginSource = fs
    .readFileSync(path.join(rootDir, 'src', 'lib', 'unifiedLogin.ts'), 'utf8')
    .replace(/^import\s+\{\s*appPath\s*\}\s+from\s+'\.\/appPaths';\r?\n/m, '');
  const source = `${appPathsSource}\n${loginSource}`;
  const compiled = await compileTypeScriptModule(source, ['buildUnifiedLoginUrl', 'cloudReturnTo']);
  const module = { exports: {} };

  vm.runInNewContext(
    compiled,
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

const { buildUnifiedLoginUrl, cloudReturnTo } = await loadModule();

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
  [['/console/', '', ''], '/cloudPan/'],
  [['/console/identity/', '', ''], '/cloudPan/'],
  [['/console/cloud/', '', ''], '/cloudPan/'],
  [['/rag/', '', ''], '/cloudPan/'],
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
assert.equal(buildUnifiedLoginUrl('/console/identity/users'), '/login?returnTo=%2FcloudPan%2F');
assert.equal(buildUnifiedLoginUrl('/console/cloud/'), '/login?returnTo=%2FcloudPan%2F');
assert.equal(buildUnifiedLoginUrl('/rag/'), '/login?returnTo=%2FcloudPan%2F');

console.log('[OK] cloud web unified login returnTo verified');
