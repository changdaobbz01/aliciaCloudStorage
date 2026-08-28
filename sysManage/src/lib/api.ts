import type {
  AdminCloudOperationsOverview,
  AdminCloudShareLinksPage,
  AdminCloudShareLinksQuery,
  AdminCloudStorageUsersPage,
  AdminCloudStorageUsersQuery,
  AdminCloudTrashNodesPage,
  AdminCloudTrashNodesQuery,
  ApiMessageResponse,
  AppPackageInfo,
  HealthResponse,
  IdentityLoginResponse,
  UpdateProfilePayload,
  UpdateUserStorageQuotaPayload,
  User,
} from '../types';

export const AUTH_EXPIRED_EVENT = 'alicia-cloud-storage:auth-expired';

type UploadProgress = {
  loaded: number;
  total: number;
  percent: number;
};

type UploadFileOptions = {
  onProgress?: (progress: UploadProgress) => void;
  signal?: AbortSignal;
};

type ApiRequestOptions = {
  dispatchAuthExpired?: boolean;
};

type PageQuery = {
  page?: number;
  size?: number;
  sortBy?: string;
  sortDirection?: string;
};

export class ApiError extends Error {
  status: number;
  payload: unknown;

  constructor(message: string, status: number, payload: unknown) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.payload = payload;
    Object.setPrototypeOf(this, ApiError.prototype);
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

async function readBody(response: Response) {
  const contentType = response.headers.get('content-type') || '';

  if (contentType.includes('application/json')) {
    return response.json();
  }

  return response.text();
}

function isHtmlErrorBody(value: string) {
  const normalized = value.trim().toLowerCase();
  return normalized.startsWith('<!doctype html') || normalized.startsWith('<html') || normalized.includes('<body');
}

function statusToReadableError(status: number) {
  switch (status) {
    case 400:
      return '请求内容不正确，请检查填写的信息后再试。';
    case 401:
      return '登录状态已过期，请重新登录。';
    case 403:
      return '当前账号没有权限执行这个操作。';
    case 404:
      return '请求的资源不存在，可能已经被移动或删除。';
    case 413:
      return '文件太大，当前最多支持上传 1GB 的文件。请换一个更小的文件后重试。';
    case 415:
      return '上传内容格式不受支持。';
    case 429:
      return '操作太频繁了，请稍后再试。';
    case 502:
    case 503:
    case 504:
      return '服务暂时不可用，请稍后再试。';
    default:
      if (status >= 500) {
        return '服务器处理失败，请稍后再试。';
      }

      return null;
  }
}

function toErrorMessage(payload: unknown, status?: number, fallback = '请求失败。') {
  const readableStatusError = status ? statusToReadableError(status) : null;

  if (typeof payload === 'string' && payload.trim()) {
    if (isHtmlErrorBody(payload)) {
      return readableStatusError ?? fallback;
    }

    return payload;
  }

  if (payload && typeof payload === 'object') {
    const record = payload as Record<string, unknown>;
    const maybeMessage = record.error ?? record.message;

    if (typeof maybeMessage === 'string' && maybeMessage.trim()) {
      return maybeMessage;
    }
  }

  return readableStatusError ?? fallback;
}

function requireToken(value: string | null | undefined, fallback: string) {
  const token = value?.trim();

  if (!token) {
    throw new Error(fallback);
  }

  return token;
}

function dispatchAuthExpired(error: ApiError) {
  if (typeof window === 'undefined') {
    return;
  }

  window.dispatchEvent(
    new CustomEvent(AUTH_EXPIRED_EVENT, {
      detail: {
        status: error.status,
        message: error.message,
      },
    }),
  );
}

function throwApiError(response: Response, payload: unknown, options?: ApiRequestOptions): never {
  const error = new ApiError(toErrorMessage(payload, response.status), response.status, payload);

  if (response.status === 401 && options?.dispatchAuthExpired !== false) {
    dispatchAuthExpired(error);
  }

  throw error;
}

async function fetchWithReadableNetworkError(url: string, init?: RequestInit) {
  try {
    return await fetch(url, init);
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error;
    }

    throw new Error('网络连接异常，请稍后重试。');
  }
}

async function requestJson<T>(url: string, init?: RequestInit, options?: ApiRequestOptions): Promise<T> {
  const response = await fetchWithReadableNetworkError(url, init);
  const payload = await readBody(response);

  if (!response.ok) {
    throwApiError(response, payload, options);
  }

  return payload as T;
}

function readXhrBody(xhr: XMLHttpRequest) {
  const contentType = xhr.getResponseHeader('content-type') || '';
  const rawBody = xhr.responseText || '';

  if (contentType.includes('application/json') && rawBody) {
    try {
      return JSON.parse(rawBody);
    } catch {
      return rawBody;
    }
  }

  return rawBody;
}

function requestUploadJson<T>(
  url: string,
  formData: FormData,
  token: string,
  options?: UploadFileOptions,
): Promise<T> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    const abortHandler = () => xhr.abort();
    let settled = false;

    const cleanup = () => {
      if (options?.signal) {
        options.signal.removeEventListener('abort', abortHandler);
      }
    };

    const settleReject = (error: unknown) => {
      if (settled) {
        return;
      }

      settled = true;
      cleanup();
      reject(error);
    };

    const settleResolve = (payload: T) => {
      if (settled) {
        return;
      }

      settled = true;
      cleanup();
      resolve(payload);
    };

    xhr.open('POST', url);
    xhr.setRequestHeader('Authorization', `Bearer ${token}`);

    xhr.upload.onprogress = (event) => {
      if (!options?.onProgress || !event.lengthComputable) {
        return;
      }

      options.onProgress({
        loaded: event.loaded,
        total: event.total,
        percent: Math.round((event.loaded / event.total) * 100),
      });
    };

    xhr.onload = () => {
      const payload = readXhrBody(xhr);

      if (xhr.status >= 200 && xhr.status < 300) {
        settleResolve(payload as T);
        return;
      }

      const error = new ApiError(toErrorMessage(payload, xhr.status), xhr.status, payload);

      if (xhr.status === 401) {
        dispatchAuthExpired(error);
      }

      settleReject(error);
    };

    xhr.onerror = () => {
      settleReject(new Error('网络连接异常，请稍后重试。'));
    };

    xhr.onabort = () => {
      const abortError = new Error('上传已取消。');
      abortError.name = 'AbortError';
      settleReject(abortError);
    };

    if (options?.signal) {
      if (options.signal.aborted) {
        xhr.abort();
        return;
      }

      options.signal.addEventListener('abort', abortHandler, { once: true });
    }

    xhr.send(formData);
  });
}

function withToken(token: string, init?: RequestInit): RequestInit {
  return {
    ...init,
    headers: {
      ...(init?.headers || {}),
      Authorization: `Bearer ${token}`,
    },
  };
}

function appendAdminOperationPageParams(search: URLSearchParams, query: PageQuery) {
  if (query.page) {
    search.set('page', String(query.page));
  }

  if (query.size) {
    search.set('size', String(query.size));
  }

  if (query.sortBy) {
    search.set('sortBy', query.sortBy);
  }

  if (query.sortDirection) {
    search.set('sortDirection', query.sortDirection);
  }
}

function toQuerySuffix(search: URLSearchParams) {
  const query = search.toString();
  return query ? `?${query}` : '';
}

export function fetchHealth() {
  return requestJson<HealthResponse>('/api/health');
}

export function fetchCurrentUser(token: string) {
  return requestJson<User>('/api/cloud-profile/me', withToken(token));
}

export function updateProfile(payload: UpdateProfilePayload, token: string) {
  return requestJson<unknown>(
    '/api/identity/auth/profile',
    withToken(token, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  ).then(() => fetchCurrentUser(token));
}

export function uploadCurrentUserAvatar(file: File, token: string) {
  const formData = new FormData();
  formData.append('file', file);

  return requestUploadJson<User>('/api/cloud-profile/avatar', formData, token);
}

export function refreshAuthSession(token: string, refreshToken: string) {
  const accessToken = requireToken(token, '登录状态缺少 access token，请重新登录。');
  const nextRefreshToken = requireToken(refreshToken, '登录状态缺少 refresh token，请重新登录。');

  return requestJson<IdentityLoginResponse>(
    '/api/identity/auth/token/refresh',
    withToken(accessToken, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refreshToken: nextRefreshToken }),
    }),
  );
}

export function logoutAuthToken(token: string, refreshToken?: string | null) {
  const accessToken = requireToken(token, '退出登录失败。');
  const nextRefreshToken = refreshToken?.trim();

  return requestJson<ApiMessageResponse>(
    '/api/identity/auth/logout',
    withToken(accessToken, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(nextRefreshToken ? { refreshToken: nextRefreshToken } : {}),
    }),
    { dispatchAuthExpired: false },
  );
}

export function fetchUsers(token: string) {
  return requestJson<User[]>('/api/admin/cloud-users', withToken(token));
}

export function updateUserStorageQuota(userId: number, payload: UpdateUserStorageQuotaPayload, token: string) {
  return requestJson<User>(
    `/api/admin/cloud-users/${userId}/quota`,
    withToken(token, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

export function fetchAdminCloudOperationsOverview(token: string) {
  return requestJson<AdminCloudOperationsOverview>('/api/admin/cloud-operations/overview', withToken(token));
}

export function fetchAdminCloudOperationShares(query: AdminCloudShareLinksQuery, token: string) {
  const search = new URLSearchParams();

  if (query.ownerId !== undefined && query.ownerId !== null) {
    search.set('ownerId', String(query.ownerId));
  }

  if (query.status) {
    search.set('status', query.status);
  }

  if (query.passwordProtected !== undefined && query.passwordProtected !== null) {
    search.set('passwordProtected', String(query.passwordProtected));
  }

  appendAdminOperationPageParams(search, query);

  return requestJson<AdminCloudShareLinksPage>(
    `/api/admin/cloud-operations/shares${toQuerySuffix(search)}`,
    withToken(token),
  );
}

export function fetchAdminCloudOperationTrash(query: AdminCloudTrashNodesQuery, token: string) {
  const search = new URLSearchParams();

  if (query.ownerId !== undefined && query.ownerId !== null) {
    search.set('ownerId', String(query.ownerId));
  }

  if (query.keyword?.trim()) {
    search.set('keyword', query.keyword.trim());
  }

  if (query.type) {
    search.set('type', query.type);
  }

  if (query.rootOnly !== undefined && query.rootOnly !== null) {
    search.set('rootOnly', String(query.rootOnly));
  }

  appendAdminOperationPageParams(search, query);

  return requestJson<AdminCloudTrashNodesPage>(
    `/api/admin/cloud-operations/trash${toQuerySuffix(search)}`,
    withToken(token),
  );
}

export function fetchAdminCloudStorageUsers(query: AdminCloudStorageUsersQuery, token: string) {
  const search = new URLSearchParams();
  appendAdminOperationPageParams(search, query);

  return requestJson<AdminCloudStorageUsersPage>(
    `/api/admin/cloud-operations/users/storage${toQuerySuffix(search)}`,
    withToken(token),
  );
}

export function fetchPublicAppPackage() {
  return requestJson<AppPackageInfo>('/api/app-package');
}

export function fetchAdminAppPackage(token: string) {
  return requestJson<AppPackageInfo>('/api/admin/app-package', withToken(token));
}

export function uploadAdminAppPackage(
  file: File,
  versionName: string,
  releaseNotes: string,
  token: string,
  options?: UploadFileOptions,
) {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('versionName', versionName);
  formData.append('releaseNotes', releaseNotes);

  return requestUploadJson<AppPackageInfo>('/api/admin/app-package', formData, token, options);
}

export function deleteAdminAppPackage(token: string) {
  return requestJson<ApiMessageResponse>(
    '/api/admin/app-package',
    withToken(token, {
      method: 'DELETE',
    }),
  );
}
