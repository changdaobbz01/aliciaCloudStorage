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
  const [appPackageUploadOpen, setAppPackageUploadOpen] = useState(false);
  const [selectedAppPackageFile, setSelectedAppPackageFile] = useState<File | null>(null);
  const [publicAppPackageError, setPublicAppPackageError] = useState<string | null>(null);

  const [appPackageUploadForm] = Form.useForm<AppPackageUploadFormValues>();
  const appPackageInputRef = useRef<HTMLInputElement | null>(null);

  async function loadAppPackageInfo() {
    if (!authToken || !isAdmin) {
      setAppPackageInfo(null);
      return;
    }

    setAppPackageLoading(true);

    try {
      setAppPackageInfo(await fetchAdminAppPackage(authToken));
    } catch (loadError) {
      message.error(loadError instanceof Error ? loadError.message : '加载 APK 信息失败。');
    } finally {
      setAppPackageLoading(false);
    }
  }

  async function loadPublicAppPackageInfo() {
    setPublicAppPackageLoading(true);
    setPublicAppPackageError(null);

    try {
      setPublicAppPackageInfo(await fetchPublicAppPackage());
    } catch (loadError) {
      setPublicAppPackageInfo(null);
      setPublicAppPackageError(loadError instanceof Error ? loadError.message : '加载 APK 下载信息失败。');
    } finally {
      setPublicAppPackageLoading(false);
    }
  }

  function resetAppPackageUploadDraft() {
    appPackageUploadForm.resetFields();
    setSelectedAppPackageFile(null);
  }

  function closeAppPackageUploadModal() {
    if (appPackageUploading) {
      return;
    }

    resetAppPackageUploadDraft();
    setAppPackageUploadOpen(false);
  }

  function openAppPackageUploadModal() {
    resetAppPackageUploadDraft();
    setAppPackageUploadOpen(true);
  }

  function handleAppPackageFilePickerClick() {
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
    if (!authToken || !isAdmin) {
      return false;
    }

    if (!selectedAppPackageFile) {
      message.error('请先选择 APK 安装包。');
      return false;
    }

    setAppPackageUploading(true);

    try {
      const nextPackageInfo = await uploadAdminAppPackage(
        selectedAppPackageFile,
        values.versionName.trim(),
        values.releaseNotes.trim(),
        authToken,
      );
      setAppPackageInfo(nextPackageInfo);
      setPublicAppPackageInfo(nextPackageInfo);
      setPublicAppPackageError(null);
      resetAppPackageUploadDraft();
      setAppPackageUploadOpen(false);
      message.success('APK、版本号和更新说明已同步更新。');
      return true;
    } catch (uploadError) {
      message.error(uploadError instanceof Error ? uploadError.message : 'APK 上传失败。');
      return false;
    } finally {
      setAppPackageUploading(false);
    }
  }

  async function deleteCurrentAppPackage() {
    if (!authToken || !isAdmin) {
      return false;
    }

    try {
      await deleteAdminAppPackage(authToken);
      setAppPackageInfo(createEmptyAppPackageInfo());
      setPublicAppPackageInfo(createEmptyAppPackageInfo());
      setPublicAppPackageError(null);
      message.success('当前安装包已移除。');
      return true;
    } catch (deleteError) {
      message.error(deleteError instanceof Error ? deleteError.message : '移除安装包失败。');
      return false;
    }
  }

  useEffect(() => {
    void loadPublicAppPackageInfo();
  }, []);

  useEffect(() => {
    if (!isAppPackageView) {
      return;
    }

    void loadAppPackageInfo();
  }, [authToken, isAdmin, isAppPackageView]);

  return {
    appPackageInfo,
    publicAppPackageInfo,
    appPackageLoading,
    publicAppPackageLoading,
    appPackageUploading,
    appPackageUploadOpen,
    selectedAppPackageFile,
    publicAppPackageError,
    appPackageUploadForm,
    appPackageInputRef,
    loadAppPackageInfo,
    loadPublicAppPackageInfo,
    closeAppPackageUploadModal,
    openAppPackageUploadModal,
    handleAppPackageFilePickerClick,
    handleAppPackageFileChange,
    submitAppPackageUpload,
    deleteCurrentAppPackage,
  };
}
