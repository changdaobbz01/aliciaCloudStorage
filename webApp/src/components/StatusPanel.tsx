import {
  CheckCircleTwoTone,
  DatabaseOutlined,
  DeleteOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  HddOutlined,
  PictureOutlined,
} from '@ant-design/icons';
import { Button, Progress, Space, Typography } from 'antd';
import type { CSSProperties, ReactNode } from 'react';
import type { DriveOverview, HealthResponse, UsageHistoryPoint } from '../types';

type StatusPanelProps = {
  health: HealthResponse | null;
  overview: DriveOverview | null;
  usageHistory: UsageHistoryPoint[];
  backgroundImage: string | null;
  onChooseBackground: () => void;
  onClearBackground: () => void;
};

type StatItem = {
  label: string;
  value: string | number;
  icon: ReactNode;
};

function formatBytes(value: number) {
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

function formatStorageLimit(value: number | null) {
  return value === null ? '无上限' : formatBytes(value);
}

function formatChartDate(value: string) {
  const [, month, day] = value.split('-');
  return month && day ? `${month}/${day}` : value;
}

function UsageTrendChart({ points }: { points: UsageHistoryPoint[] }) {
  const width = 680;
  const height = 260;
  const padding = { top: 24, right: 24, bottom: 42, left: 58 };
  const chartWidth = width - padding.left - padding.right;
  const chartHeight = height - padding.top - padding.bottom;
  const values = points.map((point) => point.usedBytes);
  const maxValue = Math.max(...values, 1);
  const minValue = Math.min(...values, 0);
  const range = Math.max(maxValue - minValue, 1);
  const getX = (index: number) =>
    padding.left + (points.length <= 1 ? chartWidth : (chartWidth * index) / (points.length - 1));
  const getY = (value: number) => padding.top + chartHeight - ((value - minValue) / range) * chartHeight;
  const path = points
    .map((point, index) => `${index === 0 ? 'M' : 'L'} ${getX(index)} ${getY(point.usedBytes)}`)
    .join(' ');
  const areaPath =
    points.length > 0
      ? `${path} L ${getX(points.length - 1)} ${padding.top + chartHeight} L ${getX(0)} ${
          padding.top + chartHeight
        } Z`
      : '';
  const lastPoint = points.at(-1);
  const xLabels =
    points.length > 0 ? [points[0], points[Math.floor(points.length / 2)], points[points.length - 1]] : [];

  return (
    <div className="usage-chart-wrap">
      <svg className="usage-chart" viewBox={`0 0 ${width} ${height}`} role="img" aria-label="近 30 日空间占用趋势">
        {[0, 1, 2, 3].map((line) => {
          const y = padding.top + (chartHeight * line) / 3;

          return (
            <line
              key={line}
              x1={padding.left}
              x2={width - padding.right}
              y1={y}
              y2={y}
              className="usage-chart-grid"
            />
          );
        })}
        <text x={padding.left - 12} y={padding.top + 6} className="usage-chart-axis" textAnchor="end">
          {formatBytes(maxValue)}
        </text>
        <text x={padding.left - 12} y={padding.top + chartHeight} className="usage-chart-axis" textAnchor="end">
          {formatBytes(minValue)}
        </text>
        {areaPath ? <path d={areaPath} className="usage-chart-area" /> : null}
        {path ? <path d={path} className="usage-chart-line" /> : null}
        {lastPoint ? (
          <circle
            cx={getX(points.length - 1)}
            cy={getY(lastPoint.usedBytes)}
            r="5"
            className="usage-chart-dot"
          />
        ) : null}
        {xLabels.map((point, index) => (
          <text
            key={`${point.date}-${index}`}
            x={index === 0 ? padding.left : index === 1 ? padding.left + chartWidth / 2 : width - padding.right}
            y={height - 12}
            className="usage-chart-axis"
            textAnchor={index === 0 ? 'start' : index === 1 ? 'middle' : 'end'}
          >
            {formatChartDate(point.date)}
          </text>
        ))}
      </svg>
    </div>
  );
}

export function StatusPanel({
  health,
  overview,
  usageHistory,
  backgroundImage,
  onChooseBackground,
  onClearBackground,
}: StatusPanelProps) {
  const serviceOnline = health?.status === 'ok';
  const adminView = (overview?.scope ?? 'USER') === 'ADMIN';
  const usedBytes = overview?.usedBytes ?? 0;
  const totalSpaceBytes = overview?.totalSpaceBytes ?? null;
  const trendCurrentBytes = overview?.actualUsedBytes ?? usedBytes;
  const hasFiniteTotalSpace = totalSpaceBytes !== null && totalSpaceBytes > 0;
  const usagePercent =
    hasFiniteTotalSpace && totalSpaceBytes > 0 ? Math.min(100, Math.round((usedBytes / totalSpaceBytes) * 100)) : 0;
  const usedLabel = adminView ? '当前总占用' : '已用空间';
  const totalLabel = adminView ? 'COS 总容量' : '总空间';
  const meterTitle = adminView ? '全站存储占用' : '空间使用率';
  const chartTitle = adminView ? '近 30 日全站存储占用变化' : '近 30 日占用空间变化';
  const chartSubtitle = adminView ? '按所有账号文件元数据回算' : '按每天结束时的文件元数据回算';
  const overviewSubtitle = adminView ? '查看所有账号的实际总占用，以及 COS 容量语义。' : '把当前账号的核心空间状态集中放在一处。';
  const panelStyle = backgroundImage
    ? ({
        '--home-background-image': `url(${backgroundImage})`,
      } as CSSProperties)
    : undefined;

  const statItems: StatItem[] = [
    {
      label: '服务状态',
      value: serviceOnline ? '在线' : '待检查',
      icon: <CheckCircleTwoTone twoToneColor={serviceOnline ? '#2563eb' : '#f59e0b'} />,
    },
    { label: '总项目数', value: overview?.totalItems ?? 0, icon: <DatabaseOutlined /> },
    { label: '文件夹数', value: overview?.totalFolders ?? 0, icon: <FolderOpenOutlined /> },
    { label: usedLabel, value: formatBytes(usedBytes), icon: <FileTextOutlined /> },
    { label: totalLabel, value: formatStorageLimit(totalSpaceBytes), icon: <HddOutlined /> },
  ];

  return (
    <section className={`home-dashboard${backgroundImage ? ' home-dashboard-with-background' : ''}`} style={panelStyle}>
      <div className="home-summary-card">
        <div className="home-card-header">
          <div>
            <Typography.Title level={3}>主页概览</Typography.Title>
            <Typography.Text className="muted-text">{overviewSubtitle}</Typography.Text>
          </div>

          <Space wrap className="home-card-actions">
            <Button icon={<PictureOutlined />} onClick={onChooseBackground}>
              设置背景图
            </Button>
            {backgroundImage ? (
              <Button icon={<DeleteOutlined />} onClick={onClearBackground}>
                移除背景
              </Button>
            ) : null}
          </Space>
        </div>

        <div className="home-stat-grid">
          {statItems.map((item) => (
            <div key={item.label} className="home-stat-item">
              <span className="home-stat-icon">{item.icon}</span>
              <div>
                <div className="home-stat-label">{item.label}</div>
                <div className="home-stat-value">{item.value}</div>
              </div>
            </div>
          ))}
        </div>

        <div className="home-space-meter">
          <div className="home-meter-copy">
            <Typography.Text className="home-meter-title">{meterTitle}</Typography.Text>
            <Typography.Text className="muted-text">
              {adminView ? '当前总占用' : '当前已用'} {formatBytes(usedBytes)} / {formatStorageLimit(totalSpaceBytes)}
            </Typography.Text>
          </div>

          {hasFiniteTotalSpace ? (
            <Progress percent={usagePercent} strokeColor="#2563eb" trailColor="#e5edff" />
          ) : (
            <Typography.Paragraph className="muted-text">
              COS 存储桶没有固定容量上限，所以管理员主页会按实际总占用展示。
            </Typography.Paragraph>
          )}
        </div>
      </div>

      <div className="home-chart-card">
        <div className="home-card-header">
          <div>
            <Typography.Title level={4}>{chartTitle}</Typography.Title>
            <Typography.Text className="muted-text">{chartSubtitle}</Typography.Text>
          </div>
          <div className="home-chart-current">{formatBytes(trendCurrentBytes)}</div>
        </div>

        <UsageTrendChart points={usageHistory} />
      </div>
    </section>
  );
}
