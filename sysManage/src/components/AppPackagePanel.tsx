import { Download, Trash2, Upload } from 'lucide-react';
import { Alert, Button, Popconfirm, Space, Spin, Typography } from 'antd';
import type { AppPackageInfo } from '../types';
import { Icon } from './Icon';
import { normalizeAppDownloadPath } from '../features/drive/driveShared';

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
  const downloadUrl = normalizeAppDownloadPath(packageInfo?.downloadUrl);
  const versionName = packageInfo?.versionName?.trim() || '未填写';
  const releaseNotes = packageInfo?.releaseNotes?.trim() || '当前安装包尚未填写更新说明。';

  return (
    <section className="content-panel account-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>APP 更新管理</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">
            在这里上传安卓 APK，同时维护版本号和更新说明。移动端启动时会读取这里的最新版本信息，并提示用户更新。
          </Typography.Paragraph>
        </div>

        <div className="panel-actions">
          <Button type="primary" icon={<Icon icon={Upload} />} loading={uploading} onClick={onUploadClick}>
            上传新版本
          </Button>
          {packageAvailable ? (
            <Popconfirm
              title="移除当前安装包？"
              description="移除后，首页下载入口和 APP 更新检查都会暂时失效。"
              okText="移除"
              cancelText="取消"
              okButtonProps={{ danger: true }}
              onConfirm={onDeletePackage}
            >
              <Button danger icon={<Icon icon={Trash2} />} disabled={uploading}>
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
          <div className="management-summary-label">当前版本</div>
          <div className="management-summary-value">{versionName}</div>
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
              <Space direction="vertical" size={12} style={{ display: 'flex' }}>
                <Typography.Text strong>{packageInfo?.fileName}</Typography.Text>
                <div className="app-package-meta">
                  <Typography.Text strong>更新版本</Typography.Text>
                  <Typography.Text>{versionName}</Typography.Text>
                </div>
                <div className="app-package-meta">
                  <Typography.Text strong>更新说明</Typography.Text>
                  <Typography.Paragraph className="app-package-release-notes">
                    {releaseNotes}
                  </Typography.Paragraph>
                </div>
                <Typography.Text className="muted-text">
                  公共下载地址固定不变，替换新版 APK 后，首页二维码和 APP 内更新弹窗都会自动指向这条地址。
                </Typography.Text>
                <Typography.Paragraph copyable={{ text: downloadUrl }} className="app-package-url">
                  {downloadUrl}
                </Typography.Paragraph>
                <a href={downloadUrl} target="_blank" rel="noreferrer" className="sider-download-link app-package-link">
                  <Icon icon={Download} />
                  下载当前 APK
                </a>
              </Space>
            ) : (
              <Alert
                type="info"
                showIcon
                message="当前还没有可下载的安装包"
                description="请先上传一个 APK，并同步填写版本号和更新说明。上传完成后，首页下载入口和 APP 更新检测会自动生效。"
              />
            )}
          </div>

          <div className="app-package-card">
            <Typography.Title level={5}>上传说明</Typography.Title>
            <ul className="app-package-list">
              <li>支持直接上传安卓 APK 文件。</li>
              <li>上传时必须同步填写更新版本和更新说明，供移动端判断是否提示升级。</li>
              <li>系统只保留一份“当前正式安装包”，再次上传会直接覆盖旧版本。</li>
              <li>公共下载地址固定不变，首页下载入口与 APP 内更新弹窗共用同一地址。</li>
            </ul>
          </div>
        </div>
      )}
    </section>
  );
}
