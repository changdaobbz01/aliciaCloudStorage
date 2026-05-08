import { DeleteOutlined, DownloadOutlined, UploadOutlined } from '@ant-design/icons';
import { Alert, Button, Popconfirm, Space, Spin, Typography } from 'antd';
import type { AppPackageInfo } from '../types';

type AppPackagePanelProps = {
  packageInfo: AppPackageInfo | null;
  loading: boolean;
  uploading: boolean;
  onUploadClick: () => void;
  onDeletePackage: () => void;
};

function formatBytes(value: number | null) {
  if (value === null || value <= 0) {
    return '0 B';
  }

  if (value < 1024) {
    return `${value} B`;
  }

  const units = ['KB', 'MB', 'GB', 'TB'];
  let size = value;
  let unitIndex = -1;

  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }

  return `${size.toFixed(size >= 100 ? 0 : 1)} ${units[unitIndex]}`;
}

export function AppPackagePanel({
  packageInfo,
  loading,
  uploading,
  onUploadClick,
  onDeletePackage,
}: AppPackagePanelProps) {
  const packageAvailable = packageInfo?.available ?? false;
  const downloadUrl = packageInfo?.downloadUrl ?? '/api/app-package/download/current';

  return (
    <section className="content-panel account-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>APP 上传</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">
            这里用于上传安卓 APK。上传成功后，首页左下角的下载二维码和下载按钮会自动指向这条正式地址。
          </Typography.Paragraph>
        </div>

        <div className="panel-actions">
          <Button type="primary" icon={<UploadOutlined />} loading={uploading} onClick={onUploadClick}>
            上传 APK
          </Button>
          {packageAvailable ? (
            <Popconfirm
              title="移除当前安装包"
              description="移除后，首页下载二维码会暂时失效。"
              okText="移除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={onDeletePackage}
            >
              <Button danger icon={<DeleteOutlined />} disabled={uploading}>
                移除当前包
              </Button>
            </Popconfirm>
          ) : null}
        </div>
      </div>

      <div className="management-summary-grid app-package-summary-grid">
        <div className="management-summary-card">
          <div className="management-summary-label">当前状态</div>
          <div className="management-summary-value">{packageAvailable ? '已上传' : '未上传'}</div>
        </div>
        <div className="management-summary-card">
          <div className="management-summary-label">安装包大小</div>
          <div className="management-summary-value">{formatBytes(packageInfo?.fileSizeBytes ?? null)}</div>
        </div>
        <div className="management-summary-card">
          <div className="management-summary-label">最近更新时间</div>
          <div className="management-summary-value">
            {packageInfo?.uploadedAt ? new Date(packageInfo.uploadedAt).toLocaleString('zh-CN') : '暂无'}
          </div>
        </div>
      </div>

      {loading ? (
        <div className="loading-box">
          <Spin size="large" />
        </div>
      ) : (
        <div className="app-package-grid">
          <div className="app-package-card">
            <Typography.Title level={5}>当前安装包</Typography.Title>
            {packageAvailable ? (
              <Space direction="vertical" size={10} style={{ display: 'flex' }}>
                <Typography.Text strong>{packageInfo?.fileName}</Typography.Text>
                <Typography.Text className="muted-text">
                  公共下载地址固定不变，替换新版 APK 后无需再修改首页二维码。
                </Typography.Text>
                <Typography.Paragraph copyable={{ text: downloadUrl }} className="app-package-url">
                  {downloadUrl}
                </Typography.Paragraph>
                <a href={downloadUrl} target="_blank" rel="noreferrer" className="sider-download-link app-package-link">
                  <DownloadOutlined />
                  下载当前 APK
                </a>
              </Space>
            ) : (
              <Alert
                type="info"
                showIcon
                message="当前还没有可下载的安装包"
                description="请先上传一个 APK，上传成功后首页下载码会自动生效。"
              />
            )}
          </div>

          <div className="app-package-card">
            <Typography.Title level={5}>上传说明</Typography.Title>
            <ul className="app-package-list">
              <li>支持直接上传安卓 APK 文件。</li>
              <li>系统会保留一份“当前正式安装包”，后续再次上传会直接覆盖。</li>
              <li>首页左侧二维码和下载按钮都走同一条公开下载地址。</li>
            </ul>
          </div>
        </div>
      )}
    </section>
  );
}
