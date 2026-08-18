const normalizedBasePath = normalizeBasePath(import.meta.env.BASE_URL);

function normalizeBasePath(basePath: string) {
  const withLeadingSlash = basePath.startsWith('/') ? basePath : `/${basePath}`;
  const withoutTrailingSlash = withLeadingSlash.replace(/\/+$/, '');

  return withoutTrailingSlash === '' ? '' : withoutTrailingSlash;
}

export const ROUTER_BASENAME = normalizedBasePath || undefined;

export function appPath(path = '/') {
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;

  return `${normalizedBasePath}${normalizedPath}`;
}

export function appUrl(path = '/') {
  if (typeof window === 'undefined') {
    return appPath(path);
  }

  return new URL(appPath(path), window.location.origin).toString();
}

export function publicAssetPath(path: string) {
  return appPath(path);
}
