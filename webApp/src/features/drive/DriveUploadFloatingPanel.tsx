import { ChevronDown, CircleX, RefreshCw, Upload } from 'lucide-react';
import { Button, Progress, Typography } from 'antd';
import { useState } from 'react';
import type { DriveUploadTask } from './types';
import { Icon } from '../../components/Icon';

type DriveUploadFloatingPanelProps = {
  uploading: boolean;
  uploadTasks: DriveUploadTask[];
  overallUploadProgress: number;
  onCancelActiveUploads: () => void;
  onRetryFailedUploads: () => void;
  onClearUploadHistory: () => void;
  onRetryUploadTask: (taskId: string) => void;
  onCancelUploadTask: (taskId: string) => void;
  getUploadTaskStatusText: (task: DriveUploadTask) => string;
};

function formatBytes(value: number) {
  if (value === 0) {
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

export function DriveUploadFloatingPanel({
  uploading,
  uploadTasks,
  overallUploadProgress,
  onCancelActiveUploads,
  onRetryFailedUploads,
  onClearUploadHistory,
  onRetryUploadTask,
  onCancelUploadTask,
  getUploadTaskStatusText,
}: DriveUploadFloatingPanelProps) {
  const [collapsed, setCollapsed] = useState(false);
  const uploadFailedCount = uploadTasks.filter((task) => task.status === 'error').length;
  const uploadSuccessCount = uploadTasks.filter((task) => task.status === 'success').length;
  const uploadCanceledCount = uploadTasks.filter((task) => task.status === 'canceled').length;

  if (uploadTasks.length === 0) {
    return null;
  }

  if (collapsed) {
    return (
      <button type="button" className="upload-floating-chip" onClick={() => setCollapsed(false)}>
        <Icon icon={Upload} />
        <span>上传队列</span>
        <strong>{overallUploadProgress}%</strong>
      </button>
    );
  }

  return (
    <section className="upload-floating-panel" role="dialog" aria-label="上传队列">
      <div className="upload-floating-header">
        <div className="upload-floating-title">
          <span className="upload-floating-icon">
            <Icon icon={Upload} />
          </span>
          <div>
            <Typography.Title level={5}>上传队列</Typography.Title>
            <Typography.Text className="muted-text">
              {uploading
                ? `正在处理 ${uploadTasks.length} 个文件`
                : `成功 ${uploadSuccessCount} 个，失败 ${uploadFailedCount} 个，取消 ${uploadCanceledCount} 个`}
            </Typography.Text>
          </div>
        </div>

        <div className="upload-floating-actions">
          {uploading ? (
            <Button
              className="upload-floating-action-button"
              size="small"
              danger
              icon={<Icon icon={CircleX} />}
              onClick={onCancelActiveUploads}
            >
              取消上传
            </Button>
          ) : null}
          {uploadFailedCount > 0 ? (
            <Button
              className="upload-floating-action-button"
              size="small"
              icon={<Icon icon={RefreshCw} />}
              onClick={onRetryFailedUploads}
              disabled={uploading}
            >
              继续失败项
            </Button>
          ) : null}
          <Button
            className="upload-floating-action-button"
            size="small"
            onClick={onClearUploadHistory}
            disabled={uploading}
          >
            清除记录
          </Button>
          <Button
            className="upload-floating-collapse-button"
            size="small"
            icon={<Icon icon={ChevronDown} />}
            onClick={() => setCollapsed(true)}
            aria-label="收起上传队列"
          />
        </div>
      </div>

      <Progress
        percent={overallUploadProgress}
        status={uploadFailedCount > 0 && !uploading ? 'exception' : undefined}
      />

      <div className="upload-floating-task-list">
        {uploadTasks.map((task) => (
          <div key={task.id} className="upload-floating-task-row">
            <div className="upload-task-main">
              <div className="upload-task-name">{task.file.name}</div>
              <Typography.Text className="muted-text">{formatBytes(task.totalBytes || task.file.size)}</Typography.Text>
            </div>

            <div className="upload-task-side">
              <Typography.Text className="muted-text">{getUploadTaskStatusText(task)}</Typography.Text>
              {task.status === 'error' ? (
                <Button size="small" type="link" onClick={() => onRetryUploadTask(task.id)} disabled={uploading}>
                  继续
                </Button>
              ) : null}
              {['uploading', 'retrying', 'completing'].includes(task.status) ? (
                <Button size="small" type="link" danger onClick={() => onCancelUploadTask(task.id)}>
                  取消
                </Button>
              ) : null}
            </div>

            <Progress
              percent={task.status === 'success' ? 100 : task.progress}
              size="small"
              showInfo={false}
              status={
                task.status === 'error' || task.status === 'canceled'
                  ? 'exception'
                  : task.status === 'success'
                    ? 'success'
                    : 'active'
              }
            />

            {task.error ? <div className="upload-task-error">{task.error}</div> : null}
          </div>
        ))}
      </div>
    </section>
  );
}
