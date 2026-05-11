import type { ChangeEvent } from 'react';
import { useEffect, useMemo, useRef, useState } from 'react';
import type { MessageInstance } from 'antd/es/message/interface';
import {
  abortMultipartUpload,
  completeMultipartUpload,
  createFolder,
  createMultipartUpload,
  deleteStorageNodes,
  downloadStorageFile,
  fetchStorageFileAccessUrl,
  fetchStorageFolders,
  fetchStorageNodes,
  fetchTrashNodes,
  isApiError,
  moveStorageNodes,
  permanentlyDeleteStorageNodes,
  renameStorageNode,
  restoreStorageNodes,
  uploadMultipartPart,
  uploadStorageFile,
} from '../../../lib/api';
import type {
  BatchMoveNodePayload,
  CreateFolderPayload,
  RenameNodePayload,
  SortDirection,
  StorageNode,
  StorageNodeFilter,
  StorageNodeSortField,
  StorageViewMode,
} from '../../../types';
import { ROOT_PARENT_KEY, createDefaultListState } from '../driveShared';
import type { DriveListState, DrivePreviewKind, DrivePreviewState, DriveUploadTask, FolderCrumb } from '../types';

type UseDriveExplorerOptions = {
  authToken: string | null;
  activeView: StorageViewMode;
  message: MessageInstance;
  onStorageChanged: () => Promise<unknown>;
};

const MAX_TEXT_PREVIEW_BYTES = 2 * 1024 * 1024;
const MULTIPART_UPLOAD_THRESHOLD_BYTES = 20 * 1024 * 1024;
const MULTIPART_CHUNK_SIZE_BYTES = 8 * 1024 * 1024;
const MAX_UPLOAD_RETRIES = 2;
const MAX_CHUNK_UPLOAD_RETRIES = 2;
const PREVIEWABLE_TEXT_EXTENSIONS = new Set([
  'txt',
  'md',
  'csv',
  'tsv',
  'log',
  'json',
  'xml',
  'yaml',
  'yml',
]);
const initialPreviewState: DrivePreviewState = {
  target: null,
  kind: null,
  loading: false,
  objectUrl: null,
  textContent: '',
  note: null,
  error: null,
};

function resolvePreviewKind(node: StorageNode): DrivePreviewKind {
  const mimeType = node.mimeType?.toLowerCase() ?? '';
  const extension = node.extension?.toLowerCase() ?? '';

  if (mimeType.startsWith('image/')) {
    return 'image';
  }

  if (mimeType === 'application/pdf' || extension === 'pdf') {
    return 'pdf';
  }

  if (mimeType.startsWith('video/')) {
    return 'video';
  }

  if (mimeType.startsWith('audio/')) {
    return 'audio';
  }

  if (mimeType.startsWith('text/') || PREVIEWABLE_TEXT_EXTENSIONS.has(extension)) {
    return 'text';
  }

  return 'unsupported';
}

function createUploadTasks(files: FileList | File[], parentId: number | null) {
  const seed = Date.now();

  return Array.from(files).map((file, index) => ({
    id: `${seed}-${index}-${file.name}`,
    file,
    parentId,
    uploadToken: null,
    progress: 0,
    loadedBytes: 0,
    totalBytes: file.size,
    status: 'queued' as const,
    attempt: 0,
    error: null,
  }));
}

function isRetryableUploadError(error: unknown) {
  if (isApiError(error)) {
    return error.status >= 500;
  }

  return error instanceof Error && error.name !== 'AbortError';
}

function shouldUseMultipartUpload(file: File) {
  return file.size > MULTIPART_UPLOAD_THRESHOLD_BYTES;
}

function wait(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function toHex(buffer: ArrayBuffer) {
  return Array.from(new Uint8Array(buffer))
    .map((byte) => byte.toString(16).padStart(2, '0'))
    .join('');
}

function fallbackHash(input: string) {
  let hash = 0x811c9dc5;

  for (let index = 0; index < input.length; index += 1) {
    hash ^= input.charCodeAt(index);
    hash = Math.imul(hash, 0x01000193);
  }

  return hash.toString(16).padStart(8, '0');
}

async function createUploadFingerprint(file: File) {
  const signature = `${file.name}\n${file.size}\n${file.lastModified}\n${file.type}`;

  if (globalThis.crypto?.subtle) {
    const digest = await globalThis.crypto.subtle.digest('SHA-256', new TextEncoder().encode(signature));
    return toHex(digest);
  }

  return fallbackHash(signature);
}

async function retryUploadRequest<T>(producer: () => Promise<T>, retries: number) {
  for (let attempt = 1; attempt <= retries + 1; attempt += 1) {
    try {
      return await producer();
    } catch (error) {
      if (attempt > retries || !isRetryableUploadError(error)) {
        throw error;
      }

      await wait(Math.min(800 * attempt, 2000));
    }
  }

  throw new Error('上传失败，请稍后重试。');
}

export function useDriveExplorer({ authToken, activeView, message, onStorageChanged }: UseDriveExplorerOptions) {
  const [items, setItems] = useState<StorageNode[]>([]);
  const [listState, setListState] = useState<DriveListState>(() => createDefaultListState('drive'));
  const [folderOptions, setFolderOptions] = useState<StorageNode[]>([]);
  const [breadcrumbs, setBreadcrumbs] = useState<FolderCrumb[]>([{ id: null, label: '根目录' }]);
  const [loading, setLoading] = useState(true);
  const [folderOptionsLoading, setFolderOptionsLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadTasks, setUploadTasks] = useState<DriveUploadTask[]>([]);
  const [downloadingFileId, setDownloadingFileId] = useState<number | null>(null);
  const [previewState, setPreviewState] = useState<DrivePreviewState>(initialPreviewState);
  const [error, setError] = useState<string | null>(null);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [nodeTypeFilter, setNodeTypeFilter] = useState<StorageNodeFilter>('ALL');
  const [selectedItems, setSelectedItems] = useState<StorageNode[]>([]);

  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const previewObjectUrlRef = useRef<string | null>(null);
  const previewRequestIdRef = useRef(0);
  const uploadControllersRef = useRef<Map<string, AbortController>>(new Map());

  const isDriveView = activeView === 'drive';
  const isTrashView = activeView === 'trash';
  const isListView = isDriveView || isTrashView;
  const currentFolderId = breadcrumbs[breadcrumbs.length - 1]?.id ?? null;
  const selectedRowKeys = useMemo(() => selectedItems.map((item) => item.id), [selectedItems]);
  const overallUploadProgress = useMemo(() => {
    if (uploadTasks.length === 0) {
      return 0;
    }

    const totalBytes = uploadTasks.reduce((sum, task) => sum + task.totalBytes, 0);
    if (totalBytes === 0) {
      return 0;
    }

    const uploadedBytes = uploadTasks.reduce((sum, task) => {
      if (task.status === 'success') {
        return sum + task.totalBytes;
      }

      return sum + Math.min(task.loadedBytes, task.totalBytes);
    }, 0);

    return Math.round((uploadedBytes / totalBytes) * 100);
  }, [uploadTasks]);
  const previewTarget = previewState.target;
  const previewingFileId = previewState.loading ? previewTarget?.id ?? null : null;

  function clearSelection() {
    setSelectedItems([]);
  }

  function revokePreviewObjectUrl() {
    if (!previewObjectUrlRef.current) {
      return;
    }

    if (previewObjectUrlRef.current.startsWith('blob:')) {
      URL.revokeObjectURL(previewObjectUrlRef.current);
    }
    previewObjectUrlRef.current = null;
  }

  function closePreviewModal() {
    previewRequestIdRef.current += 1;
    revokePreviewObjectUrl();
    setPreviewState(initialPreviewState);
  }

  function openFolder(item: StorageNode) {
    if (!isDriveView) {
      return;
    }

    setBreadcrumbs((current) => [...current, { id: item.id, label: item.name }]);
  }

  function jumpToCrumb(index: number) {
    setBreadcrumbs((current) => current.slice(0, index + 1));
  }

  function resetListState(view: StorageViewMode) {
    clearSelection();
    setListState(createDefaultListState(view));
  }

  function handleSelectionChange(nextItems: StorageNode[]) {
    setSelectedItems(nextItems);
  }

  function handleTableChange(options: {
    page: number;
    pageSize: number;
    sortBy: StorageNodeSortField;
    sortDirection: SortDirection;
  }) {
    clearSelection();
    setListState((current) => {
      const sortChanged = current.sortBy !== options.sortBy || current.sortDirection !== options.sortDirection;
      const sizeChanged = current.size !== options.pageSize;

      return {
        ...current,
        page: sortChanged || sizeChanged ? 1 : options.page,
        size: options.pageSize,
        sortBy: options.sortBy,
        sortDirection: options.sortDirection,
      };
    });
  }

  function handleSearch(value: string) {
    setKeyword(value.trim());
  }

  function handleKeywordInputChange(event: ChangeEvent<HTMLInputElement>) {
    const nextValue = event.target.value;
    setKeywordInput(nextValue);

    if (nextValue.trim() === '' && keyword !== '') {
      setKeyword('');
    }
  }

  function updateUploadTask(taskId: string, updater: (task: DriveUploadTask) => DriveUploadTask) {
    setUploadTasks((current) => current.map((task) => (task.id === taskId ? updater(task) : task)));
  }

  function createUploadAbortController(taskId: string) {
    const controller = new AbortController();
    uploadControllersRef.current.set(taskId, controller);
    return controller;
  }

  function clearUploadAbortController(taskId: string, controller: AbortController) {
    if (uploadControllersRef.current.get(taskId) === controller) {
      uploadControllersRef.current.delete(taskId);
    }
  }

  function cancelUploadTask(taskId: string) {
    const controller = uploadControllersRef.current.get(taskId);

    if (!controller) {
      return;
    }

    controller.abort();
    const task = uploadTasks.find((candidate) => candidate.id === taskId);
    if (authToken && task?.uploadToken && task.status !== 'completing') {
      void abortMultipartUpload(task.uploadToken, authToken).catch(() => undefined);
    }

    updateUploadTask(taskId, (current) => ({
      ...current,
      status: 'canceled',
      error: '上传已取消。',
    }));
  }

  function cancelActiveUploads() {
    if (uploadControllersRef.current.size === 0) {
      return;
    }

    Array.from(uploadControllersRef.current.keys()).forEach(cancelUploadTask);
  }

  function clearUploadHistory() {
    if (uploading) {
      return;
    }

    setUploadTasks([]);
  }

  function getUploadTaskStatusText(task: DriveUploadTask) {
    if (task.status === 'success') {
      return '上传完成';
    }

    if (task.status === 'error') {
      return '上传失败';
    }

    if (task.status === 'canceled') {
      return '已取消';
    }

    if (task.status === 'completing') {
      return '正在合并文件';
    }

    if (task.status === 'retrying') {
      return `继续上传（第 ${task.attempt} 次）`;
    }

    if (task.status === 'uploading') {
      return `正在上传（第 ${task.attempt} 次）`;
    }

    return '等待上传';
  }

  async function loadDrive() {
    if (!authToken || !isListView) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const nodeRequest = isTrashView
        ? fetchTrashNodes(authToken, keyword, nodeTypeFilter, listState)
        : fetchStorageNodes(authToken, currentFolderId, keyword, nodeTypeFilter, listState);
      const nodeData = await nodeRequest;
      setItems(nodeData.items);
      setListState((current) => ({
        ...current,
        page: nodeData.page,
        size: nodeData.size,
        totalItems: nodeData.totalItems,
        totalPages: nodeData.totalPages,
        sortBy: nodeData.sortBy,
        sortDirection: nodeData.sortDirection,
      }));
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : '加载文件列表失败。');
    } finally {
      setLoading(false);
    }
  }

  async function loadFolderOptions() {
    if (!authToken) {
      setFolderOptions([]);
      return;
    }

    setFolderOptionsLoading(true);

    try {
      setFolderOptions(await fetchStorageFolders(authToken));
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载文件夹列表失败。');
    } finally {
      setFolderOptionsLoading(false);
    }
  }

  async function uploadMultipartTask(task: DriveUploadTask, token: string, signal?: AbortSignal) {
    const fingerprint = await createUploadFingerprint(task.file);
    const totalChunks = Math.ceil(task.file.size / MULTIPART_CHUNK_SIZE_BYTES);
    const session = await createMultipartUpload(
      {
        parentId: task.parentId,
        fileName: task.file.name,
        fileSize: task.file.size,
        contentType: task.file.type || null,
        chunkSize: MULTIPART_CHUNK_SIZE_BYTES,
        totalChunks,
        fingerprint,
      },
      token,
      { signal },
    );
    const uploadedParts = new Map(session.uploadedParts.map((part) => [part.partNumber, part]));
    let completedBytes = session.uploadedParts.reduce((sum, part) => sum + part.size, 0);

    updateUploadTask(task.id, (current) => ({
      ...current,
      uploadToken: session.uploadToken,
      loadedBytes: completedBytes,
      totalBytes: session.fileSize,
      progress: Math.round((completedBytes / session.fileSize) * 100),
    }));

    for (let partNumber = 1; partNumber <= session.totalChunks; partNumber += 1) {
      const uploadedPart = uploadedParts.get(partNumber);

      if (uploadedPart) {
        continue;
      }

      const start = (partNumber - 1) * session.chunkSize;
      const end = Math.min(start + session.chunkSize, task.file.size);
      const chunk = task.file.slice(start, end);

      await retryUploadRequest(
        () =>
          uploadMultipartPart(session.uploadToken, partNumber, chunk, token, {
            signal,
            onProgress: ({ loaded }) => {
              const loadedBytes = completedBytes + loaded;
              updateUploadTask(task.id, (current) => ({
                ...current,
                loadedBytes,
                totalBytes: session.fileSize,
                progress: Math.round((loadedBytes / session.fileSize) * 100),
                error: null,
              }));
            },
          }),
        MAX_CHUNK_UPLOAD_RETRIES,
      );

      completedBytes += chunk.size;
      updateUploadTask(task.id, (current) => ({
        ...current,
        loadedBytes: completedBytes,
        totalBytes: session.fileSize,
        progress: Math.round((completedBytes / session.fileSize) * 100),
        error: null,
      }));
    }

    updateUploadTask(task.id, (current) => ({
      ...current,
      status: 'completing',
      loadedBytes: session.fileSize,
      totalBytes: session.fileSize,
      progress: 100,
      error: null,
    }));

    return retryUploadRequest(() => completeMultipartUpload(session.uploadToken, token, { signal }), MAX_UPLOAD_RETRIES);
  }

  async function uploadTaskFile(task: DriveUploadTask, token: string, signal?: AbortSignal) {
    if (shouldUseMultipartUpload(task.file)) {
      return uploadMultipartTask(task, token, signal);
    }

    return uploadStorageFile(task.file, task.parentId, token, {
      signal,
      onProgress: ({ loaded, total, percent }) => {
        updateUploadTask(task.id, (current) => ({
          ...current,
          status: 'uploading',
          loadedBytes: loaded,
          totalBytes: total || current.totalBytes || current.file.size,
          progress: percent,
          error: null,
        }));
      },
    });
  }

  async function runUploadTasks(tasksToRun: DriveUploadTask[]) {
    if (!authToken || tasksToRun.length === 0) {
      return;
    }

    setUploading(true);
    let successCount = 0;
    let canceledCount = 0;

    try {
      for (const task of tasksToRun) {
        for (let attempt = 1; attempt <= MAX_UPLOAD_RETRIES + 1; attempt += 1) {
          const controller = createUploadAbortController(task.id);

          updateUploadTask(task.id, (current) => ({
            ...current,
            status: attempt === 1 ? 'uploading' : 'retrying',
            attempt,
            progress: 0,
            loadedBytes: 0,
            totalBytes: current.totalBytes || current.file.size,
            error: null,
          }));

          try {
            await uploadTaskFile(task, authToken, controller.signal);

            updateUploadTask(task.id, (current) => ({
              ...current,
              status: 'success',
              progress: 100,
              loadedBytes: current.totalBytes || current.file.size,
              totalBytes: current.totalBytes || current.file.size,
              error: null,
            }));
            successCount += 1;
            break;
          } catch (uploadError) {
            if (uploadError instanceof Error && uploadError.name === 'AbortError') {
              updateUploadTask(task.id, (current) => ({
                ...current,
                status: 'canceled',
                error: '上传已取消。',
              }));
              canceledCount += 1;
              break;
            }

            const shouldRetry = attempt <= MAX_UPLOAD_RETRIES && isRetryableUploadError(uploadError);

            if (shouldRetry) {
              await wait(Math.min(800 * attempt, 2000));
              continue;
            }

            updateUploadTask(task.id, (current) => ({
              ...current,
              status: 'error',
              error: uploadError instanceof Error ? uploadError.message : '上传失败，请稍后重试。',
            }));
            break;
          } finally {
            clearUploadAbortController(task.id, controller);
          }
        }
      }
    } finally {
      setUploading(false);
    }

    if (successCount > 0) {
      clearSelection();
      await Promise.all([loadDrive(), onStorageChanged()]);
    }

    const failedCount = tasksToRun.length - successCount - canceledCount;
    if (failedCount === 0) {
      if (canceledCount > 0) {
        message.info(
          canceledCount === tasksToRun.length ? '上传已取消。' : `已完成 ${successCount} 个文件上传，取消 ${canceledCount} 个。`,
        );
        return;
      }

      message.success(
        tasksToRun.length === 1
          ? `文件“${tasksToRun[0].file.name}”上传成功。`
          : `已完成 ${tasksToRun.length} 个文件上传。`,
      );
      return;
    }

    if (successCount > 0) {
      message.warning(`已完成 ${successCount} 个文件上传，另有 ${failedCount} 个失败。`);
      return;
    }

    message.error(
      tasksToRun.length === 1 ? `文件“${tasksToRun[0].file.name}”上传失败。` : `共 ${failedCount} 个文件上传失败。`,
    );
  }

  async function retryFailedUploads() {
    if (uploading) {
      return;
    }

    const failedTasks = uploadTasks.filter((task) => task.status === 'error');
    if (failedTasks.length === 0) {
      return;
    }

    await runUploadTasks(failedTasks);
  }

  async function retryUploadTask(taskId: string) {
    if (uploading) {
      return;
    }

    const retryTarget = uploadTasks.find((task) => task.id === taskId && task.status === 'error');
    if (!retryTarget) {
      return;
    }

    await runUploadTasks([retryTarget]);
  }

  function handleUploadButtonClick() {
    if (uploading) {
      message.info('当前仍有文件正在上传，请稍候。');
      return;
    }

    const input = fileInputRef.current;
    if (!input) {
      message.error('上传入口初始化失败，请刷新页面后重试。');
      return;
    }

    try {
      if (typeof input.showPicker === 'function') {
        input.showPicker();
        return;
      }
    } catch {
      // Fall back to the classic click flow when showPicker is unavailable.
    }

    input.click();
  }

  async function handleSelectedFiles(event: ChangeEvent<HTMLInputElement>) {
    if (!authToken) {
      return;
    }

    const selectedFiles = event.target.files ? Array.from(event.target.files) : [];
    event.target.value = '';

    if (selectedFiles.length === 0) {
      return;
    }

    const nextUploadTasks = createUploadTasks(selectedFiles, currentFolderId);
    setUploadTasks(nextUploadTasks);
    await runUploadTasks(nextUploadTasks);
  }

  async function handleDownloadFile(item: StorageNode) {
    if (!authToken) {
      return;
    }

    setDownloadingFileId(item.id);

    try {
      const access = await fetchStorageFileAccessUrl(item.id, authToken, 'attachment');
      const anchor = document.createElement('a');
      anchor.href = access.url;
      anchor.rel = 'noreferrer';
      anchor.target = '_blank';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
    } catch (downloadError) {
      message.error(downloadError instanceof Error ? downloadError.message : '下载文件失败。');
    } finally {
      setDownloadingFileId(null);
    }
  }

  async function handlePreviewFile(item: StorageNode) {
    if (!authToken || item.type !== 'FILE') {
      return;
    }

    const kind = resolvePreviewKind(item);
    const requestId = previewRequestIdRef.current + 1;
    previewRequestIdRef.current = requestId;
    revokePreviewObjectUrl();

    if (kind === 'unsupported') {
      setPreviewState({
        target: item,
        kind,
        loading: false,
        objectUrl: null,
        textContent: '',
        note: '当前文件暂不支持在线预览，请直接下载查看。',
        error: null,
      });
      return;
    }

    if (kind === 'text' && item.size > MAX_TEXT_PREVIEW_BYTES) {
      setPreviewState({
        target: item,
        kind: 'unsupported',
        loading: false,
        objectUrl: null,
        textContent: '',
        note: '文本文件超过 2 MB，暂不在线预览，请直接下载查看。',
        error: null,
      });
      return;
    }

    setPreviewState({
      target: item,
      kind,
      loading: true,
      objectUrl: null,
      textContent: '',
      note: null,
      error: null,
    });

    try {
      if (kind === 'text') {
        const { blob } = await downloadStorageFile(item.id, authToken, item.updatedAt);

        if (previewRequestIdRef.current !== requestId) {
          return;
        }

        const textContent = await blob.text();

        if (previewRequestIdRef.current !== requestId) {
          return;
        }

        setPreviewState({
          target: item,
          kind,
          loading: false,
          objectUrl: null,
          textContent,
          note: null,
          error: null,
        });
        return;
      }

      const access = await fetchStorageFileAccessUrl(item.id, authToken, 'inline');

      if (previewRequestIdRef.current !== requestId) {
        return;
      }

      previewObjectUrlRef.current = access.url;
      setPreviewState({
        target: item,
        kind,
        loading: false,
        objectUrl: access.url,
        textContent: '',
        note: null,
        error: null,
      });
    } catch (previewError) {
      if (previewRequestIdRef.current !== requestId) {
        return;
      }

      setPreviewState({
        target: item,
        kind,
        loading: false,
        objectUrl: null,
        textContent: '',
        note: null,
        error: previewError instanceof Error ? previewError.message : '预览文件失败。',
      });
    }
  }

  async function deleteNodes(targets: StorageNode[]) {
    if (!authToken || targets.length === 0) {
      return;
    }

    try {
      await deleteStorageNodes({ nodeIds: targets.map((target) => target.id) }, authToken);
      clearSelection();
      await Promise.all([loadDrive(), onStorageChanged()]);
      message.success(targets.length === 1 ? '已移入回收站。' : `已将 ${targets.length} 项移入回收站。`);
    } catch (deleteError) {
      message.error(deleteError instanceof Error ? deleteError.message : '删除失败。');
    }
  }

  async function restoreNodes(targets: StorageNode[]) {
    if (!authToken || targets.length === 0) {
      return;
    }

    try {
      await restoreStorageNodes({ nodeIds: targets.map((target) => target.id) }, authToken);
      clearSelection();
      await Promise.all([loadDrive(), onStorageChanged()]);
      message.success(targets.length === 1 ? '已恢复。' : `已恢复 ${targets.length} 项。`);
    } catch (restoreError) {
      message.error(restoreError instanceof Error ? restoreError.message : '恢复失败。');
    }
  }

  async function permanentlyDeleteNodes(targets: StorageNode[]) {
    if (!authToken || targets.length === 0) {
      return;
    }

    try {
      await permanentlyDeleteStorageNodes({ nodeIds: targets.map((target) => target.id) }, authToken);
      clearSelection();
      await Promise.all([loadDrive(), onStorageChanged()]);
      message.success(targets.length === 1 ? '已彻底删除。' : `已彻底删除 ${targets.length} 项。`);
    } catch (deleteError) {
      message.error(deleteError instanceof Error ? deleteError.message : '彻底删除失败。');
    }
  }

  async function createFolderNode(values: CreateFolderPayload) {
    if (!authToken) {
      return false;
    }

    try {
      await createFolder(
        {
          parentId: currentFolderId,
          folderName: values.folderName,
        },
        authToken,
      );

      clearSelection();
      await Promise.all([loadDrive(), onStorageChanged()]);
      message.success('文件夹创建成功。');
      return true;
    } catch (createError) {
      message.error(createError instanceof Error ? createError.message : '文件夹创建失败。');
      return false;
    }
  }

  async function renameNode(target: StorageNode | null, values: RenameNodePayload) {
    if (!authToken || !target) {
      return false;
    }

    try {
      await renameStorageNode(target.id, { name: values.name }, authToken);
      clearSelection();
      await loadDrive();
      message.success('重命名成功。');
      return true;
    } catch (renameError) {
      message.error(renameError instanceof Error ? renameError.message : '重命名失败。');
      return false;
    }
  }

  async function moveNodes(targets: StorageNode[], parentKey: string) {
    if (!authToken || targets.length === 0) {
      return false;
    }

    const parentId = parentKey === ROOT_PARENT_KEY ? null : Number(parentKey);
    const payload: BatchMoveNodePayload = {
      nodeIds: targets.map((target) => target.id),
      parentId,
    };

    try {
      await moveStorageNodes(payload, authToken);
      clearSelection();
      await loadDrive();
      message.success(targets.length === 1 ? '移动成功。' : `已移动 ${targets.length} 项。`);
      return true;
    } catch (moveError) {
      message.error(moveError instanceof Error ? moveError.message : '移动失败。');
      return false;
    }
  }

  useEffect(() => {
    if (!isListView) {
      return;
    }

    void loadDrive();
  }, [
    authToken,
    isListView,
    isTrashView,
    currentFolderId,
    keyword,
    nodeTypeFilter,
    listState.page,
    listState.size,
    listState.sortBy,
    listState.sortDirection,
  ]);

  useEffect(() => {
    setSelectedItems((current) => current.filter((item) => items.some((candidate) => candidate.id === item.id)));
  }, [items]);

  useEffect(() => {
    setSelectedItems([]);
  }, [activeView, currentFolderId, keyword, nodeTypeFilter]);

  useEffect(() => {
    setListState((current) => {
      if (current.page === 1) {
        return current;
      }

      return {
        ...current,
        page: 1,
      };
    });
  }, [currentFolderId, keyword, nodeTypeFilter]);

  useEffect(
    () => () => {
      previewRequestIdRef.current += 1;

      if (previewObjectUrlRef.current) {
        if (previewObjectUrlRef.current.startsWith('blob:')) {
          URL.revokeObjectURL(previewObjectUrlRef.current);
        }
        previewObjectUrlRef.current = null;
      }
    },
    [],
  );

  useEffect(() => {
    previewRequestIdRef.current += 1;

    if (previewObjectUrlRef.current) {
      if (previewObjectUrlRef.current.startsWith('blob:')) {
        URL.revokeObjectURL(previewObjectUrlRef.current);
      }
      previewObjectUrlRef.current = null;
    }

    setPreviewState(initialPreviewState);
  }, [activeView, currentFolderId]);

  return {
    items,
    listState,
    folderOptions,
    breadcrumbs,
    currentFolderId,
    nodeTypeFilter,
    keywordInput,
    selectedItems,
    selectedRowKeys,
    loading,
    uploading,
    uploadTasks,
    overallUploadProgress,
    downloadingFileId,
    previewState,
    previewTarget,
    previewingFileId,
    error,
    folderOptionsLoading,
    fileInputRef,
    clearSelection,
    closePreviewModal,
    resetListState,
    setNodeTypeFilter,
    loadDrive,
    loadFolderOptions,
    openFolder,
    jumpToCrumb,
    handleSelectionChange,
    handleTableChange,
    handleSearch,
    handleKeywordInputChange,
    handleUploadButtonClick,
    handleSelectedFiles,
    handleDownloadFile,
    handlePreviewFile,
    deleteNodes,
    restoreNodes,
    permanentlyDeleteNodes,
    createFolderNode,
    renameNode,
    moveNodes,
    cancelActiveUploads,
    retryFailedUploads,
    clearUploadHistory,
    retryUploadTask,
    cancelUploadTask,
    getUploadTaskStatusText,
  };
}
