import {
  AndroidOutlined,
  CloudDownloadOutlined,
  CloudServerOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons';
import { App as AntApp, Button, Card, Result, Space, Spin, Typography } from 'antd';
import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { RegulatoryFooter } from '../components/RegulatoryFooter';
import { fetchPublicAppPackage } from '../lib/api';
import type { AppPackageInfo } from '../types';
import { formatFileSize, resolveAppDownloadUrl, resolveShareUrl } from '../features/drive/driveShared';

const ANDROID_PACKAGE_NAME = 'com.alicia.cloudstorage.phone';

function buildShareIntentUrl(shareCode: string) {
  const fallback = new URL('/app-download', window.location.origin);
  fallback.searchParams.set('share', shareCode);

  return `intent://share/${encodeURIComponent(shareCode)}#Intent;scheme=aliciacloud;package=${ANDROID_PACKAGE_NAME};S.browser_fallback_url=${encodeURIComponent(
    fallback.toString(),
  )};S.alicia_web_url=${encodeURIComponent(resolveShareUrl(shareCode))};end`;
}

export function AppDownloadPage() {
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const shareCode = searchParams.get('share')?.trim() || '';
  const [packageInfo, setPackageInfo] = useState<AppPackageInfo | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function loadPackage() {
      setLoading(true);
      setError(null);

      try {
        const nextPackageInfo = await fetchPublicAppPackage();
        if (!cancelled) {
          setPackageInfo(nextPackageInfo);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : '获取安装包信息失败。');
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }

    void loadPackage();

    return () => {
      cancelled = true;
    };
  }, []);

  const downloadUrl = useMemo(
    () => resolveAppDownloadUrl(packageInfo?.downloadUrl),
    [packageInfo?.downloadUrl],
  );
  const canDownload = Boolean(packageInfo?.available && downloadUrl);

  function openShareInApp() {
    if (!shareCode) {
      return;
    }

    window.location.href = buildShareIntentUrl(shareCode);
  }

  function handleDownload() {
    if (!canDownload) {
      message.warning('当前还没有可下载的 Android 安装包。');
      return;
    }

    window.location.href = downloadUrl;
  }

  return (
    <div className="app-download-page">
      <header className="share-page-header">
        <button
          type="button"
          className="brand-block share-brand-block share-brand-link"
          aria-label="进入 Alicia 云盘主页"
          onClick={() => void navigate('/')}
        >
          <CloudServerOutlined className="brand-icon" />
          <div>
            <Typography.Title level={4}>Alicia 云盘</Typography.Title>
            <Typography.Text>移动客户端</Typography.Text>
          </div>
        </button>
      </header>

      <main className="app-download-main">
        {loading ? (
          <div className="loading-box">
            <Spin size="large" />
          </div>
        ) : error ? (
          <Result status="warning" title="安装包信息暂不可用" subTitle={error} />
        ) : (
          <Card className="app-download-panel" bordered={false}>
            <div className="app-download-hero">
              <span className="app-download-icon-badge">
                <AndroidOutlined />
              </span>
              <div>
                <Typography.Text className="header-eyebrow">ANDROID APP</Typography.Text>
                <Typography.Title level={2}>打开 Alicia 云盘移动端</Typography.Title>
                <Typography.Paragraph className="panel-subtitle">
                  已安装 App 时可以直接打开分享详情；未安装时下载最新安装包后再进入。
                </Typography.Paragraph>
              </div>
            </div>

            <div className="app-download-meta-grid">
              <div className="app-download-meta-card">
                <Typography.Text className="muted-text">当前版本</Typography.Text>
                <Typography.Title level={4}>{packageInfo?.versionName || '暂未发布'}</Typography.Title>
              </div>
              <div className="app-download-meta-card">
                <Typography.Text className="muted-text">安装包大小</Typography.Text>
                <Typography.Title level={4}>
                  {packageInfo?.fileSizeBytes ? formatFileSize(packageInfo.fileSizeBytes) : '-'}
                </Typography.Title>
              </div>
              <div className="app-download-meta-card">
                <Typography.Text className="muted-text">发布状态</Typography.Text>
                <Typography.Title level={4}>{packageInfo?.available ? '可下载' : '未发布'}</Typography.Title>
              </div>
            </div>

            {packageInfo?.releaseNotes ? (
              <Typography.Paragraph className="app-download-release-notes">
                {packageInfo.releaseNotes}
              </Typography.Paragraph>
            ) : null}

            <Space className="app-download-actions" wrap>
              {shareCode ? (
                <Button type="primary" size="large" icon={<AndroidOutlined />} onClick={openShareInApp}>
                  打开 App 查看分享
                </Button>
              ) : null}
              <Button
                size="large"
                icon={<CloudDownloadOutlined />}
                disabled={!canDownload}
                onClick={handleDownload}
              >
                下载 Android 安装包
              </Button>
              {shareCode ? (
                <Button size="large" onClick={() => void navigate(`/share/${encodeURIComponent(shareCode)}`)}>
                  继续网页查看
                </Button>
              ) : null}
            </Space>

            <div className="app-download-safety">
              <SafetyCertificateOutlined />
              <Typography.Text>请只通过 Alicia 云盘站点下载安装包，安装前确认来源域名为 windwindwind-alicia.cn。</Typography.Text>
            </div>
          </Card>
        )}
      </main>

      <footer className="share-page-footer">
        <RegulatoryFooter />
      </footer>
    </div>
  );
}
