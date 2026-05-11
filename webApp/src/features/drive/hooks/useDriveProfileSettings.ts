import { Form } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { useRef, useState, type ChangeEvent } from 'react';
import {
  changePassword,
  clearCurrentUserHomeBackground,
  updateProfile,
  uploadCurrentUserAvatar,
  uploadCurrentUserHomeBackground,
} from '../../../lib/api';
import type { ChangePasswordPayload, UpdateProfilePayload, User } from '../../../types';

type PasswordFormValues = ChangePasswordPayload & {
  confirmPassword: string;
};

type UseDriveProfileSettingsOptions = {
  authToken: string | null;
  currentUser: User | null;
  message: MessageInstance;
  updateCurrentUser: (user: User) => void;
  clearCurrentSession: () => void;
  onNavigateToLogin: () => void;
  maxHomeBackgroundBytes: number;
};

export function useDriveProfileSettings({
  authToken,
  currentUser,
  message,
  updateCurrentUser,
  clearCurrentSession,
  onNavigateToLogin,
  maxHomeBackgroundBytes,
}: UseDriveProfileSettingsOptions) {
  const [profileOpen, setProfileOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [avatarUploading, setAvatarUploading] = useState(false);
  const [profileForm] = Form.useForm<UpdateProfilePayload>();
  const [passwordForm] = Form.useForm<PasswordFormValues>();
  const avatarInputRef = useRef<HTMLInputElement | null>(null);
  const backgroundInputRef = useRef<HTMLInputElement | null>(null);

  function openProfileModal() {
    if (!currentUser) {
      return;
    }

    profileForm.setFieldsValue({
      phoneNumber: currentUser.phoneNumber,
      nickname: currentUser.nickname,
    });
    setProfileOpen(true);
  }

  function closeProfileModal() {
    setProfileOpen(false);
  }

  function handleAvatarButtonClick() {
    avatarInputRef.current?.click();
  }

  async function handleAvatarFileChange(event: ChangeEvent<HTMLInputElement>) {
    if (!authToken) {
      return;
    }

    const selectedFile = event.target.files?.[0] ?? null;
    event.target.value = '';

    if (!selectedFile) {
      return;
    }

    if (!selectedFile.type.startsWith('image/')) {
      message.error('请选择图片文件作为头像。');
      return;
    }

    setAvatarUploading(true);

    try {
      const updatedUser = await uploadCurrentUserAvatar(selectedFile, authToken);
      updateCurrentUser(updatedUser);
      profileForm.setFieldsValue({ avatarUrl: updatedUser.avatarUrl ?? '' });
      message.success('头像已更新。');
    } catch (avatarError) {
      message.error(avatarError instanceof Error ? avatarError.message : '头像上传失败。');
    } finally {
      setAvatarUploading(false);
    }
  }

  function handleHomeBackgroundButtonClick() {
    const input = backgroundInputRef.current;

    if (!input) {
      message.error('背景图上传入口初始化失败，请刷新页面后重试。');
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

  async function handleHomeBackgroundFileChange(event: ChangeEvent<HTMLInputElement>) {
    if (!authToken) {
      return;
    }

    const selectedFile = event.target.files?.[0] ?? null;
    event.target.value = '';

    if (!selectedFile) {
      return;
    }

    if (!selectedFile.type.startsWith('image/')) {
      message.error('请选择图片文件作为主页背景。');
      return;
    }

    if (selectedFile.size > maxHomeBackgroundBytes) {
      message.error('背景图不能超过 10 MB，请换一张更小的图片。');
      return;
    }

    try {
      const updatedUser = await uploadCurrentUserHomeBackground(selectedFile, authToken);
      updateCurrentUser(updatedUser);
      message.success('主页背景图已更新。');
    } catch (backgroundError) {
      message.error(backgroundError instanceof Error ? backgroundError.message : '背景图上传失败。');
    }
  }

  async function clearHomeBackground() {
    if (!authToken) {
      return;
    }

    try {
      const updatedUser = await clearCurrentUserHomeBackground(authToken);
      updateCurrentUser(updatedUser);
      message.success('主页背景图已移除。');
    } catch (backgroundError) {
      message.error(backgroundError instanceof Error ? backgroundError.message : '移除背景图失败。');
    }
  }

  function openPasswordModal() {
    passwordForm.resetFields();
    setPasswordOpen(true);
  }

  function closePasswordModal() {
    setPasswordOpen(false);
  }

  async function submitProfile(values: UpdateProfilePayload) {
    if (!authToken) {
      return false;
    }

    const submittedAvatarUrl = (values as Partial<UpdateProfilePayload>).avatarUrl;
    const avatarUrl =
      submittedAvatarUrl === undefined
        ? currentUser?.avatarUrl ?? null
        : submittedAvatarUrl?.trim()
          ? submittedAvatarUrl.trim()
          : null;

    const updatedUser = await updateProfile(
      {
        ...values,
        avatarUrl,
      },
      authToken,
    );

    updateCurrentUser(updatedUser);
    setProfileOpen(false);
    message.success('个人资料已更新。');
    return true;
  }

  async function submitPassword(values: PasswordFormValues) {
    if (!authToken) {
      return false;
    }

    await changePassword(
      {
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      },
      authToken,
    );

    passwordForm.resetFields();
    setPasswordOpen(false);
    message.success('密码修改成功，请重新登录。');
    clearCurrentSession();
    onNavigateToLogin();
    return true;
  }

  function handleLogout() {
    clearCurrentSession();
    onNavigateToLogin();
  }

  function handleAvatarMenuClick(event: { key: string }) {
    if (event.key === 'profile') {
      openProfileModal();
      return;
    }

    if (event.key === 'password') {
      openPasswordModal();
      return;
    }

    if (event.key === 'logout') {
      handleLogout();
    }
  }

  return {
    profileOpen,
    passwordOpen,
    avatarUploading,
    profileForm,
    passwordForm,
    avatarInputRef,
    backgroundInputRef,
    openProfileModal,
    closeProfileModal,
    handleAvatarButtonClick,
    handleAvatarFileChange,
    handleHomeBackgroundButtonClick,
    handleHomeBackgroundFileChange,
    clearHomeBackground,
    openPasswordModal,
    closePasswordModal,
    submitProfile,
    submitPassword,
    handleLogout,
    handleAvatarMenuClick,
  };
}
