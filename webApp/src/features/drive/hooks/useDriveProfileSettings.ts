import { Form } from 'antd';
import type { MessageInstance } from 'antd/es/message/interface';
import { useRef, useState, type ChangeEvent } from 'react';
import {
  changePassword,
  clearCurrentUserHomeBackground,
  fetchIdentitySessions,
  revokeIdentitySession,
  updateProfile,
  uploadCurrentUserAvatar,
  uploadCurrentUserHomeBackground,
} from '../../../lib/api';
import type { ChangePasswordPayload, IdentitySession, UpdateProfilePayload, User } from '../../../types';

type PasswordFormValues = ChangePasswordPayload & {
  confirmPassword: string;
};

type UseDriveProfileSettingsOptions = {
  authToken: string | null;
  currentUser: User | null;
  message: MessageInstance;
  updateCurrentUser: (user: User) => void;
  clearCurrentSession: () => void;
  logoutCurrentSession: () => Promise<void>;
  onNavigateToLogin: () => void;
  maxHomeBackgroundBytes: number;
};

export function useDriveProfileSettings({
  authToken,
  currentUser,
  message,
  updateCurrentUser,
  clearCurrentSession,
  logoutCurrentSession,
  onNavigateToLogin,
  maxHomeBackgroundBytes,
}: UseDriveProfileSettingsOptions) {
  const [profileOpen, setProfileOpen] = useState(false);
  const [passwordOpen, setPasswordOpen] = useState(false);
  const [sessionsOpen, setSessionsOpen] = useState(false);
  const [identitySessions, setIdentitySessions] = useState<IdentitySession[]>([]);
  const [identitySessionsLoading, setIdentitySessionsLoading] = useState(false);
  const [identitySessionRevokingId, setIdentitySessionRevokingId] = useState<number | null>(null);
  const [includeRevokedSessions, setIncludeRevokedSessions] = useState(false);
  const [profileSaving, setProfileSaving] = useState(false);
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
      nickname: currentUser.nickname,
      phoneNumber: currentUser.phoneNumber || '',
      avatarUrl: currentUser.avatarUrl ?? '',
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

  async function loadIdentitySessions(nextIncludeRevoked = includeRevokedSessions) {
    if (!authToken) {
      return;
    }

    setIdentitySessionsLoading(true);

    try {
      setIdentitySessions(await fetchIdentitySessions(authToken, nextIncludeRevoked));
    } catch (sessionError) {
      message.error(sessionError instanceof Error ? sessionError.message : '登录会话加载失败。');
    } finally {
      setIdentitySessionsLoading(false);
    }
  }

  function openSessionsModal() {
    setSessionsOpen(true);
    void loadIdentitySessions();
  }

  function closeSessionsModal() {
    setSessionsOpen(false);
  }

  function changeIncludeRevokedSessions(checked: boolean) {
    setIncludeRevokedSessions(checked);
    void loadIdentitySessions(checked);
  }

  async function revokeSession(sessionId: number) {
    if (!authToken) {
      return;
    }

    setIdentitySessionRevokingId(sessionId);

    try {
      await revokeIdentitySession(authToken, sessionId);
      message.success('登录会话已撤销。');
      await loadIdentitySessions(includeRevokedSessions);
    } catch (sessionError) {
      message.error(sessionError instanceof Error ? sessionError.message : '登录会话撤销失败。');
    } finally {
      setIdentitySessionRevokingId(null);
    }
  }

  async function submitProfile(values: UpdateProfilePayload) {
    if (!authToken) {
      return false;
    }

    const nickname = values.nickname.trim();

    if (!nickname) {
      message.error('请输入昵称。');
      return false;
    }

    const avatarUrl = values.avatarUrl?.trim() ? values.avatarUrl.trim() : null;
    const phoneNumber = values.phoneNumber?.trim() ? values.phoneNumber.trim() : null;

    setProfileSaving(true);

    try {
      const updatedUser = await updateProfile(
        {
          nickname,
          phoneNumber,
          avatarUrl,
        },
        authToken,
      );

      updateCurrentUser(updatedUser);
      setProfileOpen(false);
      message.success('个人资料已更新。');
      return true;
    } catch (profileError) {
      message.error(profileError instanceof Error ? profileError.message : '个人资料保存失败。');
      return false;
    } finally {
      setProfileSaving(false);
    }
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

  async function handleLogout() {
    await logoutCurrentSession();
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

    if (event.key === 'sessions') {
      openSessionsModal();
      return;
    }

    if (event.key === 'logout') {
      void handleLogout();
    }
  }

  return {
    profileOpen,
    passwordOpen,
    sessionsOpen,
    identitySessions,
    identitySessionsLoading,
    identitySessionRevokingId,
    includeRevokedSessions,
    profileSaving,
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
    openSessionsModal,
    closeSessionsModal,
    loadIdentitySessions,
    changeIncludeRevokedSessions,
    revokeSession,
    submitProfile,
    submitPassword,
    handleLogout,
    handleAvatarMenuClick,
  };
}
