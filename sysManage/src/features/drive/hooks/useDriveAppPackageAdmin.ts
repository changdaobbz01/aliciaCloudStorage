import { Form } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { useEffect, useRef, useState, type ChangeEvent } from 'react';
import {
  deleteAdminAppPackage,
  fetchAdminAppPackage,
  fetchPublicAppPackage,
  uploadAdminAppPackage,
} from '../../../lib/api';
import type { AppPackageInfo } from '../../../types';
import { createEmptyAppPackageInfo } from '../driveShared';
import type { AppPackageUploadFormValues } from '../types';

type UseDriveAppPackageAdminOptions = {
  authToken: string | null;
  isAdmin: boolean;
  isAppPackageView: boolean;
  message: MessageInstance;
};

type AppPackageReadOptions = {
  force?: boolean;
};

type RefState<T> = {
  current: T;
};

export function useDriveAppPackageAdmin({
  authToken,
  isAdmin,
  isAppPackageView,
  message,
}: UseDriveAppPackageAdminOptions) {
  const [appPackageInfo, setAppPackageInfo] = useState<AppPackageInfo | null>(null);
  const [publicAppPackageInfo, setPublicAppPackageInfo] = useState<AppPackageInfo | null>(null);
  const [appPackageLoading, setAppPackageLoading] = useState(false);
  const [publicAppPackageLoading, setPublicAppPackageLoading] = useState(false);
  const [appPackageUploading, setAppPackageUploading] = useState(false);
  const [appPackageDeleting, setAppPackageDeleting] = useState(false);
  const [appPackageUploadOpen, setAppPackageUploadOpen] = useState(false);
  const [selectedAppPackageFile, setSelectedAppPackageFile] = useState<File | null>(null);
  const [publicAppPackageError, setPublicAppPackageError] = useState<string | null>(null);

  const [appPackageUploadForm] = Form.useForm<AppPackageUploadFormValues>();
  const appPackageInputRef = useRef<HTMLInputElement | null>(null);
  const appPackageLoadingRef = useRef(false);
  const publicAppPackageLoadingRef = useRef(false);
  const appPackageMutationRef = useRef<'upload' | 'delete' | null>(null);
  const authTokenRef = useRef(authToken);
  const isAdminRef = useRef(isAdmin);
  const appPackageRequestIdRef = useRef(0);
  const appPackageLoadingKeyRef = useRef<string | null>(null);
  const publicAppPackageRequestIdRef = useRef(0);
  const publicAppPackageLoadingKeyRef = useRef<string | null>(null);

  authTokenRef.current = authToken;
  isAdminRef.current = isAdmin;

  function createAppPackageRequestKey(scope: string, token: string | null = authToken, admin = isAdmin) {
    return JSON.stringify([scope, token, admin]);
  }

  function isCurrentAppPackageRequest(
    requestIdRef: RefState<number>,
    loadingKeyRef: RefState<string | null>,
    requestId: number,
    requestKey: string,
    scope: string,
  ) {
    const currentToken = scope === 'public' ? null : authTokenRef.current;
    const currentAdmin = scope === 'public' ? true : isAdminRef.current;
    return (
      requestIdRef.current === requestId
      && loadingKeyRef.current === requestKey
      && createAppPackageRequestKey(scope, currentToken, currentAdmin) === requestKey
    );
  }

  function createAppPackageMutationRequestKey(
    scope: 'upload' | 'delete',
    token: string | null = authToken,
    admin = isAdmin,
  ) {
    return JSON.stringify([scope, token, admin]);
  }

  function isCurrentAppPackageMutationRequest(requestKey: string, scope: 'upload' | 'delete') {
    return (
      appPackageMutationRef.current === scope
      && createAppPackageMutationRequestKey(scope, authTokenRef.current, isAdminRef.current) === requestKey
    );
  }

  function cancelAppPackageReads() {
    appPackageRequestIdRef.current += 1;
    appPackageLoadingKeyRef.current = null;
    appPackageLoadingRef.current = false;
    setAppPackageLoading(false);

    publicAppPackageRequestIdRef.current += 1;
    publicAppPackageLoadingKeyRef.current = null;
    publicAppPackageLoadingRef.current = false;
    setPublicAppPackageLoading(false);
  }

  async function loadAppPackageInfo(options: AppPackageReadOptions = {}) {
    if (!authToken || !isAdmin) {
      appPackageRequestIdRef.current += 1;
      appPackageLoadingKeyRef.current = null;
      appPackageLoadingRef.current = false;
      setAppPackageLoading(false);
      setAppPackageInfo(null);
      return;
    }

    if (appPackageMutationRef.current !== null) {
      return;
    }

    const requestKey = createAppPackageRequestKey('admin', authToken, isAdmin);
    if (!options.force && appPackageLoadingKeyRef.current === requestKey) {
      return;
    }

    appPackageRequestIdRef.current += 1;
    const requestId = appPackageRequestIdRef.current;
    appPackageLoadingKeyRef.current = requestKey;
    appPackageLoadingRef.current = true;
    setAppPackageLoading(true);

    try {
      const nextPackageInfo = await fetchAdminAppPackage(authToken);
      if (!isCurrentAppPackageRequest(appPackageRequestIdRef, appPackageLoadingKeyRef, requestId, requestKey, 'admin')) {
        return;
      }

      setAppPackageInfo(nextPackageInfo);
    } catch (loadError) {
      if (isCurrentAppPackageRequest(appPackageRequestIdRef, appPackageLoadingKeyRef, requestId, requestKey, 'admin')) {
        message.error(loadError instanceof Error ? loadError.message : '加载 APK 信息失败。');
      }
    } finally {
      if (isCurrentAppPackageRequest(appPackageRequestIdRef, appPackageLoadingKeyRef, requestId, requestKey, 'admin')) {
        appPackageLoadingKeyRef.current = null;
        appPackageLoadingRef.current = false;
        setAppPackageLoading(false);
      }
    }
  }

  async function loadPublicAppPackageInfo(options: AppPackageReadOptions = {}) {
    if (appPackageMutationRef.current !== null) {
      return;
    }

    const requestKey = createAppPackageRequestKey('public', null, true);
    if (!options.force && publicAppPackageLoadingKeyRef.current === requestKey) {
      return;
    }

    publicAppPackageRequestIdRef.current += 1;
    const requestId = publicAppPackageRequestIdRef.current;
    publicAppPackageLoadingKeyRef.current = requestKey;
    publicAppPackageLoadingRef.current = true;
    setPublicAppPackageLoading(true);
    setPublicAppPackageError(null);

    try {
      const nextPackageInfo = await fetchPublicAppPackage();
      if (
        !isCurrentAppPackageRequest(
          publicAppPackageRequestIdRef,
          publicAppPackageLoadingKeyRef,
          requestId,
          requestKey,
          'public',
        )
      ) {
        return;
      }

      setPublicAppPackageInfo(nextPackageInfo);
    } catch (loadError) {
      if (
        isCurrentAppPackageRequest(
          publicAppPackageRequestIdRef,
          publicAppPackageLoadingKeyRef,
          requestId,
          requestKey,
          'public',
        )
      ) {
        setPublicAppPackageInfo(null);
        setPublicAppPackageError(loadError instanceof Error ? loadError.message : '加载 APK 下载信息失败。');
      }
    } finally {
      if (
        isCurrentAppPackageRequest(
          publicAppPackageRequestIdRef,
          publicAppPackageLoadingKeyRef,
          requestId,
          requestKey,
          'public',
        )
      ) {
        publicAppPackageLoadingKeyRef.current = null;
        publicAppPackageLoadingRef.current = false;
        setPublicAppPackageLoading(false);
      }
    }
  }

  async function loadAppPackageState(options: AppPackageReadOptions = {}) {
    await Promise.all([
      loadAppPackageInfo(options),
      loadPublicAppPackageInfo(options),
    ]);
  }

  function resetAppPackageUploadDraft() {
    appPackageUploadForm.resetFields();
    setSelectedAppPackageFile(null);
  }

  function closeAppPackageUploadModal() {
    if (appPackageMutationRef.current !== null) {
      return;
    }

    resetAppPackageUploadDraft();
    setAppPackageUploadOpen(false);
  }

  function openAppPackageUploadModal() {
    if (!authToken || !isAdmin || appPackageLoadingRef.current || appPackageMutationRef.current !== null) {
      return;
    }

    resetAppPackageUploadDraft();
    setAppPackageUploadOpen(true);
  }

  function handleAppPackageFilePickerClick() {
    if (appPackageLoadingRef.current || appPackageMutationRef.current !== null) {
      return;
    }

    const input = appPackageInputRef.current;

    if (!input) {
      return;
    }

    try {
      if (typeof input.showPicker === 'function') {
        input.showPicker();
        return;
      }
    } catch {
      // 部分浏览器限制 showPicker，回退到 click。
    }

    input.click();
  }

  function handleAppPackageFileChange(event: ChangeEvent<HTMLInputElement>) {
    if (!authToken || !isAdmin || appPackageLoadingRef.current || appPackageMutationRef.current !== null) {
      event.target.value = '';
      return;
    }

    const selectedFile = event.target.files?.[0] ?? null;
    event.target.value = '';

    if (!selectedFile) {
      return;
    }

    if (!selectedFile.name.toLowerCase().endsWith('.apk')) {
      message.error('请上传 APK 安装包文件。');
      return;
    }

    setSelectedAppPackageFile(selectedFile);
  }

  async function submitAppPackageUpload(values: AppPackageUploadFormValues) {
    if (!authToken || !isAdmin || appPackageLoadingRef.current || appPackageMutationRef.current !== null) {
      return false;
    }

    if (!selectedAppPackageFile) {
      message.error('请先选择 APK 安装包。');
      return false;
    }

    const requestToken = authToken;
    const requestKey = createAppPackageMutationRequestKey('upload', requestToken, isAdmin);
    cancelAppPackageReads();
    appPackageMutationRef.current = 'upload';
    setAppPackageUploading(true);

    try {
      const nextPackageInfo = await uploadAdminAppPackage(
        selectedAppPackageFile,
        values.versionName.trim(),
        values.releaseNotes.trim(),
        requestToken,
      );
      if (!isCurrentAppPackageMutationRequest(requestKey, 'upload')) {
        return false;
      }

      setAppPackageInfo(nextPackageInfo);
      setPublicAppPackageInfo(nextPackageInfo);
      setPublicAppPackageError(null);
      resetAppPackageUploadDraft();
      setAppPackageUploadOpen(false);
      message.success('APK、版本号和更新说明已同步更新。');
      return true;
    } catch (uploadError) {
      if (isCurrentAppPackageMutationRequest(requestKey, 'upload')) {
        message.error(uploadError instanceof Error ? uploadError.message : 'APK 上传失败。');
      }
      return false;
    } finally {
      appPackageMutationRef.current = null;
      setAppPackageUploading(false);
    }
  }

  async function deleteCurrentAppPackage() {
    if (!authToken || !isAdmin || appPackageLoadingRef.current || appPackageMutationRef.current !== null) {
      return false;
    }

    const requestToken = authToken;
    const requestKey = createAppPackageMutationRequestKey('delete', requestToken, isAdmin);
    cancelAppPackageReads();
    appPackageMutationRef.current = 'delete';
    setAppPackageDeleting(true);

    try {
      await deleteAdminAppPackage(requestToken);
      if (!isCurrentAppPackageMutationRequest(requestKey, 'delete')) {
        return false;
      }

      setAppPackageInfo(createEmptyAppPackageInfo());
      setPublicAppPackageInfo(createEmptyAppPackageInfo());
      setPublicAppPackageError(null);
      message.success('当前安装包已移除。');
      return true;
    } catch (deleteError) {
      if (isCurrentAppPackageMutationRequest(requestKey, 'delete')) {
        message.error(deleteError instanceof Error ? deleteError.message : '移除安装包失败。');
      }
      return false;
    } finally {
      appPackageMutationRef.current = null;
      setAppPackageDeleting(false);
    }
  }

  useEffect(() => {
    void loadPublicAppPackageInfo();
  }, []);

  useEffect(() => {
    appPackageRequestIdRef.current += 1;
    appPackageLoadingKeyRef.current = null;
    appPackageLoadingRef.current = false;
    setAppPackageLoading(false);
    setAppPackageInfo(null);
  }, [authToken, isAdmin]);

  useEffect(() => {
    if (!isAppPackageView) {
      return;
    }

    void loadAppPackageState();
  }, [authToken, isAdmin, isAppPackageView]);

  return {
    appPackageInfo,
    publicAppPackageInfo,
    appPackageLoading,
    publicAppPackageLoading,
    appPackageUploading,
    appPackageDeleting,
    appPackageUploadOpen,
    selectedAppPackageFile,
    publicAppPackageError,
    appPackageUploadForm,
    appPackageInputRef,
    loadAppPackageInfo,
    loadPublicAppPackageInfo,
    loadAppPackageState,
    closeAppPackageUploadModal,
    openAppPackageUploadModal,
    handleAppPackageFilePickerClick,
    handleAppPackageFileChange,
    submitAppPackageUpload,
    deleteCurrentAppPackage,
  };
}
