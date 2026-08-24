import {
  AudioOutlined,
  CopyOutlined,
  FileImageOutlined,
  FileOutlined,
  FileTextOutlined,
  FileZipOutlined,
  FolderFilled,
  LinkOutlined,
  LockOutlined,
  VideoCameraOutlined,
} from '@ant-design/icons';
import { Alert, App as AntApp, Button, Form, Input, Modal, Select, Space, Switch, Typography } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { useEffect, useMemo, useState } from 'react';
import { AliciaModalTitle } from '../../components/AliciaModalTitle';
import { fetchStorageFileAccessUrl } from '../../lib/api';
import type { ShareLinkSummary, StorageNode } from '../../types';
import { formatFileSize, resolveShareUrl } from './driveShared';
import type { CreateShareFormValues } from './types';

type DriveShareCreateModalProps = {
  targets: StorageNode[];
  authToken: string | null;
  creating: boolean;
  form: FormInstance<CreateShareFormValues>;
  lastCreatedShare: ShareLinkSummary | null;
  lastCreatedPassword: string | null;
  onClose: () => void;
  onSubmit: (values: CreateShareFormValues) => void | Promise<unknown>;
};

const imageExtensions = new Set(['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'heic', 'heif']);
const videoExtensions = new Set(['mp4', 'mov', 'm4v', 'webm', 'avi', 'mkv']);
const audioExtensions = new Set(['mp3', 'wav', 'm4a', 'aac', 'flac', 'ogg']);
const archiveExtensions = new Set(['zip', 'rar', '7z', 'tar', 'gz']);

function normalizedExtension(target: StorageNode) {
  return target.extension?.replace(/^\./, '').toLowerCase() ?? '';
}

function isImageTarget(target: StorageNode) {
  return target.type === 'FILE' && (
    target.mimeType?.toLowerCase().startsWith('image/') || imageExtensions.has(normalizedExtension(target))
  );
}

function ShareTargetThumbnail({ target, authToken }: { target: StorageNode; authToken: string | null }) {
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  useEffect(() => {
    setPreviewUrl(null);
    if (!authToken || !isImageTarget(target)) {
      return;
    }

    const controller = new AbortController();
    void fetchStorageFileAccessUrl(target.id, authToken, 'inline', controller.signal)
      .then((access) => setPreviewUrl(access.url))
      .catch((error) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) {
          setPreviewUrl(null);
        }
      });

    return () => controller.abort();
  }, [authToken, target.id]);

  if (previewUrl) {
    return <img className="share-target-preview" src={previewUrl} alt="" />;
  }

  const extension = normalizedExtension(target);
  let icon = <FileOutlined />;
  if (target.type === 'FOLDER') {
    icon = <FolderFilled />;
  } else if (isImageTarget(target)) {
    icon = <FileImageOutlined />;
  } else if (target.mimeType?.startsWith('video/') || videoExtensions.has(extension)) {
    icon = <VideoCameraOutlined />;
  } else if (target.mimeType?.startsWith('audio/') || audioExtensions.has(extension)) {
    icon = <AudioOutlined />;
  } else if (archiveExtensions.has(extension)) {
    icon = <FileZipOutlined />;
  } else if (target.mimeType?.startsWith('text/') || ['txt', 'pdf', 'doc', 'docx', 'xls', 'xlsx'].includes(extension)) {
    icon = <FileTextOutlined />;
  }

  return <span className={`share-target-icon share-target-icon-${target.type.toLowerCase()}`}>{icon}</span>;
}

async function copyText(value: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value);
    return;
  }

  const input = document.createElement('textarea');
  input.value = value;
  input.style.position = 'fixed';
  input.style.opacity = '0';
  document.body.appendChild(input);
  input.select();
  document.execCommand('copy');
  input.remove();
}

export function DriveShareCreateModal({
  targets,
  authToken,
  creating,
  form,
  lastCreatedShare,
  lastCreatedPassword,
  onClose,
  onSubmit,
}: DriveShareCreateModalProps) {
  const { message } = AntApp.useApp();
  const passwordEnabled = Form.useWatch('passwordEnabled', form);
  const shareUrl = lastCreatedShare ? resolveShareUrl(lastCreatedShare.shareCode) : '';
  const selectionSummary = useMemo(() => {
    const files = targets.filter((target) => target.type === 'FILE');
    return {
      containsFolder: files.length !== targets.length,
      fileBytes: files.reduce((total, target) => total + target.size, 0),
    };
  }, [targets]);

  async function handleCopy(value: string, successText: string) {
    try {
      await copyText(value);
      message.success(successText);
    } catch {
      message.error('复制失败，请手动复制。');
    }
  }

  return (
    <Modal
      title={(
        <AliciaModalTitle eyebrow="Share">
          {lastCreatedShare ? '分享链接已创建' : '创建分享'}
        </AliciaModalTitle>
      )}
      rootClassName="alicia-modal alicia-share-modal"
      open={targets.length > 0}
      onCancel={onClose}
      onOk={() => void form.submit()}
      okText="创建分享"
      cancelText={lastCreatedShare ? '关闭' : '取消'}
      confirmLoading={creating}
      closable={!creating}
      maskClosable={!creating}
      keyboard={!creating}
      cancelButtonProps={{ disabled: creating }}
      destroyOnHidden
      width={580}
      footer={
        lastCreatedShare
          ? [
              <Button key="done" type="primary" onClick={onClose}>
                完成
              </Button>,
            ]
          : undefined
      }
    >
      {lastCreatedShare ? (
        <div className="share-created-result">
          <Alert
            type="success"
            showIcon
            message="链接已生成"
            description="访问者打开链接后，需要按分享设置完成提取码校验和登录。"
          />

          <div className="share-copy-row">
            <Typography.Text className="share-copy-label">分享链接</Typography.Text>
            <Input readOnly value={shareUrl} prefix={<LinkOutlined />} />
            <Button icon={<CopyOutlined />} onClick={() => void handleCopy(shareUrl, '分享链接已复制。')}>
              复制链接
            </Button>
          </div>

          {lastCreatedPassword ? (
            <div className="share-copy-row">
              <Typography.Text className="share-copy-label">提取码</Typography.Text>
              <Input readOnly value={lastCreatedPassword} prefix={<LockOutlined />} />
              <Button icon={<CopyOutlined />} onClick={() => void handleCopy(lastCreatedPassword, '提取码已复制。')}>
                复制提取码
              </Button>
            </div>
          ) : null}
        </div>
      ) : (
        <Form form={form} layout="vertical" disabled={creating} onFinish={(values) => void onSubmit(values)}>
          <section className="share-content-module" aria-label="分享内容">
            <div className="share-content-header">
              <div>
                <Typography.Text strong>分享内容</Typography.Text>
                <Typography.Text className="muted-text">已选 {targets.length} 项</Typography.Text>
              </div>
              <Typography.Text className="share-content-summary">
                {selectionSummary.containsFolder
                  ? `含文件夹 · 文件合计 ${formatFileSize(selectionSummary.fileBytes)}`
                  : `合计 ${formatFileSize(selectionSummary.fileBytes)}`}
              </Typography.Text>
            </div>

            <div className="share-target-list">
              {targets.map((target) => (
                <div className="share-target-row" key={target.id}>
                  <ShareTargetThumbnail target={target} authToken={authToken} />
                  <div className="share-target-copy">
                    <Typography.Text ellipsis={{ tooltip: target.name }} className="share-target-name">
                      {target.name}
                    </Typography.Text>
                    <Typography.Text className="muted-text">
                      {target.type === 'FOLDER' ? '文件夹' : formatFileSize(target.size)}
                    </Typography.Text>
                  </div>
                </div>
              ))}
            </div>

            {selectionSummary.containsFolder ? (
              <Typography.Text className="share-folder-notice">
                文件夹分享会包含其当前及后续新增的有效内容。
              </Typography.Text>
            ) : null}
          </section>

          <Form.Item
            name="title"
            label="分享名称"
            rules={[
              { required: true, message: '请输入分享名称。' },
              { max: 255, message: '分享名称不能超过 255 个字符。' },
            ]}
          >
            <Input placeholder="分享名称" />
          </Form.Item>

          <Form.Item name="expiresInDays" label="有效期" rules={[{ required: true, message: '请选择有效期。' }]}>
            <Select
              options={[
                { label: '7 天', value: 7 },
                { label: '1 天', value: 1 },
                { label: '30 天', value: 30 },
                { label: '永久有效', value: 0 },
              ]}
            />
          </Form.Item>

          <Space className="share-switch-row" size={16} wrap>
            <Form.Item name="allowDownload" valuePropName="checked" className="share-switch-item">
              <Switch checkedChildren="允许下载" unCheckedChildren="禁止下载" />
            </Form.Item>
            <Form.Item name="allowSave" valuePropName="checked" className="share-switch-item">
              <Switch checkedChildren="允许保存" unCheckedChildren="禁止保存" />
            </Form.Item>
            <Form.Item name="passwordEnabled" valuePropName="checked" className="share-switch-item">
              <Switch checkedChildren="需要提取码" unCheckedChildren="无提取码" />
            </Form.Item>
          </Space>

          {passwordEnabled ? (
            <Form.Item
              name="password"
              label="提取码"
              rules={[
                { required: true, message: '请输入提取码。' },
                { min: 4, message: '提取码至少 4 个字符。' },
                { max: 32, message: '提取码不能超过 32 个字符。' },
              ]}
            >
              <Input.Password placeholder="4 到 32 个字符" />
            </Form.Item>
          ) : null}
        </Form>
      )}
    </Modal>
  );
}
