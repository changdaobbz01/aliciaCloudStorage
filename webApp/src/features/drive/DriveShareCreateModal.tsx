import { CopyOutlined, LinkOutlined, LockOutlined } from '@ant-design/icons';
import { Alert, App as AntApp, Button, Form, Input, Modal, Select, Space, Switch, Typography } from 'antd';
import type { FormInstance } from 'antd/es/form';
import type { ShareLinkSummary, StorageNode } from '../../types';
import { resolveShareUrl } from './driveShared';
import type { CreateShareFormValues } from './types';

type DriveShareCreateModalProps = {
  target: StorageNode | null;
  creating: boolean;
  form: FormInstance<CreateShareFormValues>;
  lastCreatedShare: ShareLinkSummary | null;
  lastCreatedPassword: string | null;
  onClose: () => void;
  onSubmit: (values: CreateShareFormValues) => void | Promise<unknown>;
};

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
  target,
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
      title={lastCreatedShare ? '分享链接已创建' : '创建分享'}
      open={target !== null}
      onCancel={onClose}
      onOk={() => void form.submit()}
      okText="创建分享"
      cancelText={lastCreatedShare ? '关闭' : '取消'}
      confirmLoading={creating}
      destroyOnHidden
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
        <Form form={form} layout="vertical" onFinish={(values) => void onSubmit(values)}>
          <Typography.Paragraph className="muted-text">
            {target ? `正在分享：${target.name}` : '请选择要分享的项目。'}
          </Typography.Paragraph>

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
