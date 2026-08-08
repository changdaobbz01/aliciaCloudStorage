import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CloudDownloadOutlined,
  DeleteOutlined,
  FileZipOutlined,
  FolderOpenOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import { Button, Empty, Popover, Progress, Space, Tag, Typography } from 'antd';
import { formatFileSize } from './driveShared';
import type { DriveDownloadTask, DriveDownloadTaskStatus } from './types';

type DriveDownloadsViewProps = {
  tasks: DriveDownloadTask[];
  activeCount: number;
  onCancelTask: (taskId: string) => void;
  onRetryTask: (taskId: string) => void;
  onClearFinished: () => void;
  onClearHistory: () => void;
};

const ACTIVE_DOWNLOAD_STATUSES = new Set<DriveDownloadTaskStatus>([
  'queued',
  'preparing',
  'downloading',
  'saving',
]);

function isActiveTask(task: DriveDownloadTask) {
  return ACTIVE_DOWNLOAD_STATUSES.has(task.status);
}

function formatTaskStatus(task: DriveDownloadTask) {
  switch (task.status) {
    case 'queued':
      return '排队中';
    case 'preparing':
      return '准备中';
    case 'downloading':
      return '下载中';
    case 'saving':
      return '保存中';
    case 'success':
      return '已完成';
    case 'error':
      return '下载失败';
    case 'canceled':
      return '已取消';
    default:
      return '未知状态';
  }
}

function resolveStatusColor(task: DriveDownloadTask) {
  switch (task.status) {
    case 'success':
      return 'success';
    case 'error':
      return 'error';
    case 'canceled':
      return 'default';
    case 'saving':
      return 'processing';
    default:
      return 'blue';
  }
}

function formatTaskSize(task: DriveDownloadTask) {
  if (task.totalBytes !== null && task.totalBytes > 0) {
    return `${formatFileSize(task.loadedBytes)} / ${formatFileSize(task.totalBytes)}`;
  }

  if (task.loadedBytes > 0) {
    return `已接收 ${formatFileSize(task.loadedBytes)}`;
  }

  return task.status === 'queued' ? '等待开始' : '等待接收';
}

function formatTaskTime(timestamp: number | null) {
  if (!timestamp) {
    return '进行中';
  }

  return new Date(timestamp).toLocaleString('zh-CN');
}

function renderTaskProgress(task: DriveDownloadTask) {
  if (task.percent !== null) {
    return (
      <Progress
        percent={task.percent}
        size="small"
        status={task.status === 'error' || task.status === 'canceled' ? 'exception' : task.status === 'success' ? 'success' : 'active'}
      />
    );
  }

  if (!isActiveTask(task)) {
    return null;
  }

  return (
    <div className="download-indeterminate-bar" aria-label="下载进度计算中">
      <span />
    </div>
  );
}

function renderTaskRow(
  task: DriveDownloadTask,
  onCancelTask: (taskId: string) => void,
  onRetryTask: (taskId: string) => void,
) {
  const active = isActiveTask(task);

  return (
    <div key={task.id} className="download-task-row">
      <div className="download-task-main">
        <span className={`download-task-icon${task.sourceType === 'archive' ? ' download-task-icon-archive' : ''}`}>
          {task.sourceType === 'archive' ? <FileZipOutlined /> : <CloudDownloadOutlined />}
        </span>

        <div className="download-task-copy">
          <Typography.Text strong ellipsis={{ tooltip: task.displayName }} className="download-task-title">
            {task.displayName}
          </Typography.Text>
          <Typography.Text className="muted-text download-task-meta">
            {task.fileName ?? (task.sourceType === 'archive' ? 'ZIP 打包下载' : '文件下载')}
          </Typography.Text>
        </div>
      </div>

      <div className="download-task-detail">
        <Tag color={resolveStatusColor(task)}>{formatTaskStatus(task)}</Tag>
        <Typography.Text className="muted-text">{formatTaskSize(task)}</Typography.Text>
        <Typography.Text className="muted-text">{formatTaskTime(task.finishedAt)}</Typography.Text>
      </div>

      <Space size="small" className="download-task-actions">
        {active ? (
          <Button size="small" danger icon={<CloseCircleOutlined />} onClick={() => onCancelTask(task.id)}>
            取消
          </Button>
        ) : null}

        {task.status === 'error' ? (
          <Button size="small" icon={<ReloadOutlined />} onClick={() => onRetryTask(task.id)}>
            重新下载
          </Button>
        ) : null}
      </Space>

      {renderTaskProgress(task)}

      {task.error && task.status === 'error' ? <div className="download-task-error">{task.error}</div> : null}
    </div>
  );
}

const downloadLocationContent = (
  <div className="download-location-popover">
    <Typography.Text strong>文件已交给浏览器保存</Typography.Text>
    <Typography.Paragraph>
      可在浏览器右上角下载列表中打开文件或查看所在文件夹。
    </Typography.Paragraph>
    <Typography.Text className="muted-text">Windows 常见位置：C:\Users\你的用户名\Downloads</Typography.Text>
  </div>
);

export default function DriveDownloadsView({
  tasks,
  activeCount,
  onCancelTask,
  onRetryTask,
  onClearFinished,
  onClearHistory,
}: DriveDownloadsViewProps) {
  const activeTasks = tasks.filter(isActiveTask);
  const historyTasks = tasks.filter((task) => !isActiveTask(task)).reverse();
  const completedCount = tasks.filter((task) => task.status === 'success').length;
  const failedCount = tasks.filter((task) => task.status === 'error').length;
  const clearFinishedDisabled = !tasks.some((task) => task.status === 'success' || task.status === 'canceled');
  const clearHistoryDisabled = historyTasks.length === 0;

  return (
    <section className="content-panel downloads-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>下载管理</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">
            集中查看当前下载、失败重试和已完成记录。
          </Typography.Paragraph>
        </div>

        <div className="panel-actions">
          <Popover placement="bottomRight" trigger="click" content={downloadLocationContent}>
            <Button icon={<FolderOpenOutlined />}>查看下载位置</Button>
          </Popover>
          <Button icon={<CheckCircleOutlined />} disabled={clearFinishedDisabled} onClick={onClearFinished}>
            清除已完成
          </Button>
          <Button danger icon={<DeleteOutlined />} disabled={clearHistoryDisabled} onClick={onClearHistory}>
            清除历史
          </Button>
        </div>
      </div>

      <div className="download-summary-strip">
        <div>
          <span>下载中</span>
          <strong>{activeCount}</strong>
        </div>
        <div>
          <span>已完成</span>
          <strong>{completedCount}</strong>
        </div>
        <div>
          <span>失败</span>
          <strong>{failedCount}</strong>
        </div>
      </div>

      <section className="download-section">
        <div className="download-section-head">
          <Typography.Title level={5}>下载中</Typography.Title>
        </div>

        {activeTasks.length > 0 ? (
          <div className="download-task-list">
            {activeTasks.map((task) => renderTaskRow(task, onCancelTask, onRetryTask))}
          </div>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无正在下载的任务" />
        )}
      </section>

      <section className="download-section">
        <div className="download-section-head">
          <Typography.Title level={5}>历史记录</Typography.Title>
        </div>

        {historyTasks.length > 0 ? (
          <div className="download-task-list">
            {historyTasks.map((task) => renderTaskRow(task, onCancelTask, onRetryTask))}
          </div>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无已下载记录" />
        )}
      </section>
    </section>
  );
}
