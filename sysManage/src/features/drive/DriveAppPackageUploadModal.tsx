import { Upload } from 'lucide-react';
import { Button, Form, Input, Modal, Space, Typography } from 'antd';
import type { FormInstance } from 'antd/es/form';
import type { MutableRefObject, ChangeEvent } from 'react';
import { AliciaModalTitle } from '../../components/AliciaModalTitle';
import { formatFileSize } from './driveShared';
import type { AppPackageUploadFormValues } from './types';
import { Icon } from '../../components/Icon';

type DriveAppPackageUploadModalProps = {
  open: boolean;
  uploading: boolean;
  selectedFile: File | null;
  form: FormInstance<AppPackageUploadFormValues>;
  inputRef: MutableRefObject<HTMLInputElement | null>;
  onClose: () => void;
  onSubmit: (values: AppPackageUploadFormValues) => Promise<unknown> | void;
  onPickFile: () => void;
  onFileChange: (event: ChangeEvent<HTMLInputElement>) => void | Promise<void>;
};

export function DriveAppPackageUploadModal({
  open,
  uploading,
  selectedFile,
  form,
  inputRef,
  onClose,
  onSubmit,
  onPickFile,
  onFileChange,
}: DriveAppPackageUploadModalProps) {
  return (
    <Modal
      title={<AliciaModalTitle eyebrow="Release">上传 APP 更新</AliciaModalTitle>}
      rootClassName="alicia-modal alicia-release-modal"
      open={open}
      onCancel={onClose}
      onOk={() => void form.submit()}
      okText="开始上传"
      cancelText="取消"
      confirmLoading={uploading}
      destroyOnHidden
      maskClosable={!uploading}
      closable={!uploading}
      cancelButtonProps={{ disabled: uploading }}
    >
      <Form form={form} layout="vertical" onFinish={(values) => void onSubmit(values)}>
        <Form.Item label="安装包文件" required>
          <Space direction="vertical" size={8} style={{ display: 'flex' }}>
            <Button icon={<Icon icon={Upload} />} onClick={onPickFile} disabled={uploading}>
              选择 APK
            </Button>
            <input
              ref={inputRef}
              type="file"
              accept=".apk,application/vnd.android.package-archive"
              className="upload-input"
              onChange={(event) => void onFileChange(event)}
            />
            <Typography.Text>
              {selectedFile ? `${selectedFile.name} · ${formatFileSize(selectedFile.size)}` : '尚未选择安装包'}
            </Typography.Text>
            <Typography.Text className="muted-text">
              上传后会覆盖当前正式安装包，但公共下载地址保持不变。
            </Typography.Text>
          </Space>
        </Form.Item>
        <Form.Item
          name="versionName"
          label="更新版本"
          rules={[
            { required: true, message: '请填写更新版本。' },
            { max: 64, message: '更新版本长度不能超过 64 个字符。' },
          ]}
        >
          <Input maxLength={64} placeholder="例如：1.3.0" />
        </Form.Item>
        <Form.Item
          name="releaseNotes"
          label="更新说明"
          rules={[
            { required: true, message: '请填写更新说明。' },
            { max: 4000, message: '更新说明长度不能超过 4000 个字符。' },
          ]}
        >
          <Input.TextArea
            rows={6}
            maxLength={4000}
            showCount
            placeholder={'例如：\n1. 修复启动页闪退问题\n2. 优化文件列表加载速度'}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
