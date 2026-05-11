import { UploadOutlined } from '@ant-design/icons';
import { Avatar, Button, Form, Input, Modal, Space } from 'antd';
import type { FormInstance } from 'antd/es/form';
import type { MutableRefObject, ChangeEvent } from 'react';
import type { ChangePasswordPayload, UpdateProfilePayload, User } from '../../types';

type PasswordFormValues = ChangePasswordPayload & {
  confirmPassword: string;
};

type DriveProfileModalsProps = {
  currentUser: User | null;
  currentAvatarSrc: string | undefined;
  profileOpen: boolean;
  passwordOpen: boolean;
  avatarUploading: boolean;
  profileForm: FormInstance<UpdateProfilePayload>;
  passwordForm: FormInstance<PasswordFormValues>;
  avatarInputRef: MutableRefObject<HTMLInputElement | null>;
  onCloseProfile: () => void;
  onSubmitProfile: (values: UpdateProfilePayload) => Promise<unknown> | void;
  onAvatarButtonClick: () => void;
  onAvatarFileChange: (event: ChangeEvent<HTMLInputElement>) => void | Promise<void>;
  onClosePassword: () => void;
  onSubmitPassword: (values: PasswordFormValues) => Promise<unknown> | void;
};

export function DriveProfileModals({
  currentUser,
  currentAvatarSrc,
  profileOpen,
  passwordOpen,
  avatarUploading,
  profileForm,
  passwordForm,
  avatarInputRef,
  onCloseProfile,
  onSubmitProfile,
  onAvatarButtonClick,
  onAvatarFileChange,
  onClosePassword,
  onSubmitPassword,
}: DriveProfileModalsProps) {
  return (
    <>
      <Modal
        title="修改个人资料"
        open={profileOpen}
        onCancel={onCloseProfile}
        onOk={() => void profileForm.submit()}
        destroyOnHidden
      >
        <Form form={profileForm} layout="vertical" onFinish={(values) => void onSubmitProfile(values)}>
          <div className="profile-avatar-row">
            <Avatar size={64} src={currentAvatarSrc}>
              {currentUser?.nickname?.slice(0, 1).toUpperCase() ?? 'U'}
            </Avatar>
            <Space wrap>
              <Button icon={<UploadOutlined />} loading={avatarUploading} onClick={onAvatarButtonClick}>
                上传本地头像
              </Button>
              <input
                ref={avatarInputRef}
                type="file"
                accept="image/png,image/jpeg,image/gif,image/webp"
                className="upload-input"
                onChange={(event) => void onAvatarFileChange(event)}
              />
            </Space>
          </div>
          <Form.Item
            name="phoneNumber"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号。' },
              { pattern: /^1\d{10}$/, message: '请输入 11 位手机号。' },
            ]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="nickname"
            label="昵称"
            rules={[{ required: true, message: '请输入昵称。' }]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="修改密码"
        open={passwordOpen}
        onCancel={onClosePassword}
        onOk={() => void passwordForm.submit()}
        destroyOnHidden
      >
        <Form form={passwordForm} layout="vertical" onFinish={(values) => void onSubmitPassword(values)}>
          <Form.Item
            name="oldPassword"
            label="旧密码"
            rules={[{ required: true, message: '请输入旧密码。' }]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码。' },
              { min: 6, message: '密码长度至少为 6 位。' },
            ]}
          >
            <Input.Password />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请再次输入新密码。' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }

                  return Promise.reject(new Error('两次输入的新密码不一致。'));
                },
              }),
            ]}
          >
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
