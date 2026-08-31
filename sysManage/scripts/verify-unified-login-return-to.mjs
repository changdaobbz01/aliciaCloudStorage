import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import { fileURLToPath } from 'node:url';

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const expectedBasePath = '/console/cloud/';

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
  const compiled = await compileTypeScriptModule(source, ['buildUnifiedLoginUrl', 'cloudConsoleReturnTo']);
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

const { buildUnifiedLoginUrl, cloudConsoleReturnTo } = await loadModule();

const returnToCases = [
  [['', '', ''], '/console/cloud/'],
  [['/', '', ''], '/console/cloud/'],
  [['/console/cloud', '?view=operations', ''], '/console/cloud/?view=operations'],
  [['/operations', '?tab=shares', '#filters'], '/console/cloud/operations?tab=shares#filters'],
  [['/app-package', '?release=current', '#upload'], '/console/cloud/app-package?release=current#upload'],
  [['/console/cloud/app-package', '', ''], '/console/cloud/app-package'],
  [['/console/cloud/', '?view=operations', 'bad-hash'], '/console/cloud/?view=operations'],
  [['/login', '', ''], '/console/cloud/'],
  [['/console/cloud/login', '', ''], '/console/cloud/'],
  [['/cloudPan/', '', ''], '/console/cloud/'],
  [['/console/', '', ''], '/console/cloud/'],
  [['/console/identity/', '', ''], '/console/cloud/'],
  [['/rag/', '', ''], '/console/cloud/'],
  [['/api/admin/cloud-users', '', ''], '/console/cloud/'],
  [['//evil.example/console/cloud/', '', ''], '/console/cloud/'],
];

for (const [args, expected] of returnToCases) {
  assert.equal(cloudConsoleReturnTo(...args), expected, `cloudConsoleReturnTo(${args.map(String).join(', ')})`);
}

assert.equal(
  buildUnifiedLoginUrl(cloudConsoleReturnTo('/operations'), 'login-required'),
  '/login?returnTo=%2Fconsole%2Fcloud%2Foperations&reason=login-required',
);
assert.equal(
  buildUnifiedLoginUrl(cloudConsoleReturnTo('/app-package'), 'login-required'),
  '/login?returnTo=%2Fconsole%2Fcloud%2Fapp-package&reason=login-required',
);
assert.equal(
  buildUnifiedLoginUrl('/api/admin/cloud-users', 'session-expired'),
  '/login?returnTo=%2Fconsole%2Fcloud%2F&reason=session-expired',
);
assert.equal(
  buildUnifiedLoginUrl('/console/cloud?view=operations'),
  '/login?returnTo=%2Fconsole%2Fcloud%2F%3Fview%3Doperations',
);
assert.equal(buildUnifiedLoginUrl('/cloudPan/'), '/login?returnTo=%2Fconsole%2Fcloud%2F');
assert.equal(buildUnifiedLoginUrl('/console/'), '/login?returnTo=%2Fconsole%2Fcloud%2F');
assert.equal(buildUnifiedLoginUrl('/rag/'), '/login?returnTo=%2Fconsole%2Fcloud%2F');

console.log('[OK] cloud console unified login returnTo verified');
