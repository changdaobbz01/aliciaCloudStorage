import type {
  ApiMessageResponse,
  BatchMoveNodePayload,
  BatchNodePayload,
  ChangePasswordPayload,
  CreateFolderPayload,
  CreateMultipartUploadPayload,
  CreateUserPayload,
  DriveOverview,
  HealthResponse,
  LoginPayload,
  LoginResponse,
  MoveNodePayload,
  MultipartUploadPart,
  MultipartUploadStatus,
  RenameNodePayload,
  ResetUserPasswordPayload,
  StorageNode,
  StorageNodeFilter,
  StorageNodePage,
  StorageNodeQuery,
  UpdateUserStorageQuotaPayload,
  UpdateProfilePayload,
  UsageHistoryPoint,
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

type RequestOptions = {
  signal?: AbortSignal;
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

/**
 * 判断给定异常是否属于统一封装过的接口异常。
 */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/**
 * 根据响应头自动解析 JSON 或文本响应体。
 */
async function readBody(response: Response) {
  const contentType = response.headers.get('content-type') || '';

  if (contentType.includes('application/json')) {
    return response.json();
  }

  return response.text();
}

/**
 * 将后端返回的错误结构转换为前端可直接展示的提示文本。
 */
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

/**
 * 在登录态过期时向全局派发事件，交给会话上下文统一处理。
 */
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

/**
 * 将非 2xx 响应包装成统一的 ApiError 异常。
 */
function throwApiError(response: Response, payload: unknown): never {
  const error = new ApiError(toErrorMessage(payload, response.status), response.status, payload);

  if (response.status === 401) {
    dispatchAuthExpired(error);
  }

  throw error;
}

/**
 * 发起一个返回 JSON 的请求，并在失败时抛出统一异常。
 */
async function requestJson<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  const payload = await readBody(response);

  if (!response.ok) {
    throwApiError(response, payload);
  }

  return payload as T;
}

/**
 * 发起一个返回二进制文件的请求，并解析响应头里的文件名。
 */
async function requestBlob(
  url: string,
  init?: RequestInit,
): Promise<{ blob: Blob; fileName: string | null }> {
  const response = await fetch(url, init);

  if (!response.ok) {
    const payload = await readBody(response);
    throwApiError(response, payload);
  }

  return {
    blob: await response.blob(),
    fileName: parseFileName(response.headers.get('content-disposition')),
  };
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

function requestBinaryUploadJson<T>(
  url: string,
  body: Blob,
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
    xhr.setRequestHeader('Content-Type', 'application/octet-stream');

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

    xhr.send(body);
  });
}

/**
 * 为需要鉴权的请求自动附加 Bearer Token。
 */
function withToken(token: string, init?: RequestInit): RequestInit {
  return {
    ...init,
    headers: {
      ...(init?.headers || {}),
      Authorization: `Bearer ${token}`,
    },
  };
}

/**
 * 从下载响应头中提取后端返回的文件名。
 */
function parseFileName(contentDisposition: string | null) {
  if (!contentDisposition) {
    return null;
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch {
      return utf8Match[1];
    }
  }

  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i);
  return plainMatch?.[1] ?? null;
}

/**
 * 查询后端健康检查状态。
 */
export function fetchHealth() {
  return requestJson<HealthResponse>('/api/health');
}

/**
 * 使用手机号和密码向后端发起登录请求。
 */
export function login(payload: LoginPayload) {
  return requestJson<LoginResponse>('/api/auth/login', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  });
}

/**
 * 获取当前登录用户的资料信息。
 */
export function fetchCurrentUser(token: string) {
  return requestJson<User>('/api/auth/me', withToken(token));
}

/**
 * 更新当前登录用户的资料信息。
 */
export function updateProfile(payload: UpdateProfilePayload, token: string) {
  return requestJson<User>(
    '/api/auth/profile',
    withToken(token, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 上传当前用户的本地头像图片。
 */
export function uploadCurrentUserAvatar(file: File, token: string) {
  const formData = new FormData();
  formData.append('file', file);

  return requestUploadJson<User>(
    '/api/auth/avatar',
    formData,
    token,
  );
}

/**
 * 上传当前用户的主页背景图。
 */
export function uploadCurrentUserHomeBackground(file: File, token: string) {
  const formData = new FormData();
  formData.append('file', file);

  return requestUploadJson<User>(
    '/api/auth/background',
    formData,
    token,
  );
}

/**
 * 清空当前用户的主页背景图。
 */
export function clearCurrentUserHomeBackground(token: string) {
  return requestJson<User>('/api/auth/background', withToken(token, { method: 'DELETE' }));
}

/**
 * 修改当前登录用户的密码，并要求提供旧密码校验。
 */
export function changePassword(payload: ChangePasswordPayload, token: string) {
  return requestJson<ApiMessageResponse>(
    '/api/auth/password',
    withToken(token, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 获取云盘首页的统计概览数据。
 */
export function fetchDriveOverview(token: string) {
  return requestJson<DriveOverview>('/api/storage/overview', withToken(token));
}

/**
 * 获取近一段时间的云盘空间占用趋势。
 */
export function fetchUsageHistory(token: string, days = 30) {
  const search = new URLSearchParams();
  search.set('days', String(days));

  return requestJson<UsageHistoryPoint[]>(`/api/storage/usage-history?${search.toString()}`, withToken(token));
}

/**
 * 按目录、关键字和类型筛选当前用户的文件列表。
 */
export function fetchStorageNodes(
  token: string,
  parentId?: number | null,
  keyword?: string,
  type?: StorageNodeFilter,
  query?: StorageNodeQuery,
) {
  const search = new URLSearchParams();

  if (parentId !== undefined && parentId !== null) {
    search.set('parentId', String(parentId));
  }

  if (keyword && keyword.trim()) {
    search.set('keyword', keyword.trim());
  }

  if (type && type !== 'ALL') {
    search.set('type', type);
  }

  if (query?.page) {
    search.set('page', String(query.page));
  }

  if (query?.size) {
    search.set('size', String(query.size));
  }

  if (query?.sortBy) {
    search.set('sortBy', query.sortBy);
  }

  if (query?.sortDirection) {
    search.set('sortDirection', query.sortDirection);
  }

  const suffix = search.toString() ? `?${search.toString()}` : '';
  return requestJson<StorageNodePage>(`/api/storage/nodes${suffix}`, withToken(token));
}

/**
 * 获取当前用户所有可移动到的文件夹。
 */
export function fetchStorageFolders(token: string) {
  return requestJson<StorageNode[]>('/api/storage/folders', withToken(token));
}

/**
 * 查询当前用户回收站里的顶层项目。
 */
export function fetchTrashNodes(
  token: string,
  keyword?: string,
  type?: StorageNodeFilter,
  query?: StorageNodeQuery,
) {
  const search = new URLSearchParams();

  if (keyword && keyword.trim()) {
    search.set('keyword', keyword.trim());
  }

  if (type && type !== 'ALL') {
    search.set('type', type);
  }

  if (query?.page) {
    search.set('page', String(query.page));
  }

  if (query?.size) {
    search.set('size', String(query.size));
  }

  if (query?.sortBy) {
    search.set('sortBy', query.sortBy);
  }

  if (query?.sortDirection) {
    search.set('sortDirection', query.sortDirection);
  }

  const suffix = search.toString() ? `?${search.toString()}` : '';
  return requestJson<StorageNodePage>(`/api/storage/trash${suffix}`, withToken(token));
}

/**
 * 在当前目录下新建文件夹。
 */
export function createFolder(payload: CreateFolderPayload, token: string) {
  return requestJson<StorageNode>(
    '/api/storage/folders',
    withToken(token, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 上传文件到后端，再由后端写入腾讯 COS。
 */
export function uploadStorageFile(
  file: File,
  parentId: number | null,
  token: string,
  options?: UploadFileOptions,
) {
  const formData = new FormData();
  formData.append('file', file);

  if (parentId !== null) {
    formData.append('parentId', String(parentId));
  }

  return requestUploadJson<StorageNode>(
    '/api/storage/files',
    formData,
    token,
    options,
  );
}

/**
 * 初始化或复用后端分片上传会话。
 */
export function createMultipartUpload(
  payload: CreateMultipartUploadPayload,
  token: string,
  options?: RequestOptions,
) {
  return requestJson<MultipartUploadStatus>(
    '/api/storage/files/multipart',
    withToken(token, {
      method: 'POST',
      signal: options?.signal,
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 查询分片上传会话状态。
 */
export function fetchMultipartUploadStatus(uploadToken: string, token: string) {
  return requestJson<MultipartUploadStatus>(
    `/api/storage/files/multipart/${encodeURIComponent(uploadToken)}`,
    withToken(token),
  );
}

/**
 * 上传单个文件分片。
 */
export function uploadMultipartPart(
  uploadToken: string,
  partNumber: number,
  chunk: Blob,
  token: string,
  options?: UploadFileOptions,
) {
  return requestBinaryUploadJson<MultipartUploadPart>(
    `/api/storage/files/multipart/${encodeURIComponent(uploadToken)}/parts/${partNumber}`,
    chunk,
    token,
    options,
  );
}

/**
 * 合并分片并生成正式文件节点。
 */
export function completeMultipartUpload(uploadToken: string, token: string, options?: RequestOptions) {
  return requestJson<StorageNode>(
    `/api/storage/files/multipart/${encodeURIComponent(uploadToken)}/complete`,
    withToken(token, {
      method: 'POST',
      signal: options?.signal,
    }),
  );
}

/**
 * 取消未完成的分片上传会话。
 */
export function abortMultipartUpload(uploadToken: string, token: string, options?: RequestOptions) {
  return requestJson<ApiMessageResponse>(
    `/api/storage/files/multipart/${encodeURIComponent(uploadToken)}`,
    withToken(token, {
      method: 'DELETE',
      signal: options?.signal,
    }),
  );
}

/**
 * 下载指定文件节点对应的文件内容。
 */
export function downloadStorageFile(fileId: number, token: string, version?: string) {
  const search = new URLSearchParams();

  if (version) {
    search.set('v', version);
  }

  const suffix = search.toString() ? `?${search.toString()}` : '';
  return requestBlob(`/api/storage/files/${fileId}/download${suffix}`, withToken(token));
}

/**
 * 重命名指定文件或文件夹。
 */
export function renameStorageNode(nodeId: number, payload: RenameNodePayload, token: string) {
  return requestJson<StorageNode>(
    `/api/storage/nodes/${nodeId}/rename`,
    withToken(token, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 将指定文件或文件夹移动到新的父目录。
 */
export function moveStorageNode(nodeId: number, payload: MoveNodePayload, token: string) {
  return requestJson<StorageNode>(
    `/api/storage/nodes/${nodeId}/move`,
    withToken(token, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 批量移动多个文件或文件夹到新的父目录。
 */
export function moveStorageNodes(payload: BatchMoveNodePayload, token: string) {
  return requestJson<StorageNode[]>(
    '/api/storage/nodes/batch/move',
    withToken(token, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 将文件或文件夹移入回收站。
 */
export function deleteStorageNode(nodeId: number, token: string) {
  return requestJson<ApiMessageResponse>(
    `/api/storage/nodes/${nodeId}`,
    withToken(token, {
      method: 'DELETE',
    }),
  );
}

/**
 * 批量将文件或文件夹移入回收站。
 */
export function deleteStorageNodes(payload: BatchNodePayload, token: string) {
  return requestJson<ApiMessageResponse>(
    '/api/storage/nodes/batch/trash',
    withToken(token, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 从回收站恢复文件或文件夹。
 */
export function restoreStorageNode(nodeId: number, token: string) {
  return requestJson<StorageNode>(
    `/api/storage/trash/${nodeId}/restore`,
    withToken(token, {
      method: 'POST',
    }),
  );
}

/**
 * 批量从回收站恢复文件或文件夹。
 */
export function restoreStorageNodes(payload: BatchNodePayload, token: string) {
  return requestJson<StorageNode[]>(
    '/api/storage/trash/batch/restore',
    withToken(token, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 从回收站彻底删除文件或文件夹。
 */
export function permanentlyDeleteStorageNode(nodeId: number, token: string) {
  return requestJson<ApiMessageResponse>(
    `/api/storage/trash/${nodeId}`,
    withToken(token, {
      method: 'DELETE',
    }),
  );
}

/**
 * 批量从回收站彻底删除文件或文件夹。
 */
export function permanentlyDeleteStorageNodes(payload: BatchNodePayload, token: string) {
  return requestJson<ApiMessageResponse>(
    '/api/storage/trash/batch/delete',
    withToken(token, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

/**
 * 查询管理员可见的账号列表。
 */
export function fetchUsers(token: string) {
  return requestJson<User[]>('/api/admin/users', withToken(token));
}

/**
 * 由管理员创建新的普通用户或管理员账号。
 */
export function createUser(payload: CreateUserPayload, token: string) {
  return requestJson<User>(
    '/api/admin/users',
    withToken(token, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

export function updateUserStorageQuota(userId: number, payload: UpdateUserStorageQuotaPayload, token: string) {
  return requestJson<User>(
    `/api/admin/users/${userId}/quota`,
    withToken(token, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}

export function resetUserPassword(userId: number, payload: ResetUserPasswordPayload, token: string) {
  return requestJson<ApiMessageResponse>(
    `/api/admin/users/${userId}/password`,
    withToken(token, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(payload),
    }),
  );
}
