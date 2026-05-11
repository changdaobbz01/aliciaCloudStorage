import {
  DeleteOutlined,
  FolderAddOutlined,
  ReloadOutlined,
  RollbackOutlined,
  SwapOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { Alert, Breadcrumb, Button, Popconfirm, Progress, Segmented, Space, Spin, Typography } from 'antd';
import { StorageTable } from '../../components/StorageTable';
import type { SortDirection, StorageNode, StorageNodeFilter, StorageNodeSortField } from '../../types';
import type { DriveListState, DriveUploadTask, FolderCrumb } from './types';

type DriveExplorerViewProps = {
  mode: 'drive' | 'trash';
  title: string;
  description: string;
  breadcrumbs: FolderCrumb[];
  nodeTypeFilter: StorageNodeFilter;
  error: string | null;
  loading: boolean;
  uploading: boolean;
  items: StorageNode[];
  selectedItems: StorageNode[];
  selectedRowKeys: number[];
  listState: DriveListState;
  uploadTasks: DriveUploadTask[];
  overallUploadProgress: number;
  downloadingFileId: number | null;
  previewingFileId: number | null;
  onRefresh: () => void;
  onUploadClick: () => void;
  onCreateFolderClick: () => void;
  onNodeTypeFilterChange: (value: StorageNodeFilter) => void;
  onJumpToCrumb: (index: number) => void;
  onRestoreSelection: () => void;
  onDeleteSelection: () => void;
  onPermanentDeleteSelection: () => void;
  onOpenBatchMove: () => void;
  onCancelActiveUploads: () => void;
  onRetryFailedUploads: () => void;
  onClearUploadHistory: () => void;
  onRetryUploadTask: (taskId: string) => void;
  onCancelUploadTask: (taskId: string) => void;
  getUploadTaskStatusText: (task: DriveUploadTask) => string;
  onSelectionChange: (items: StorageNode[]) => void;
  onTableChange: (options: {
    page: number;
    pageSize: number;
    sortBy: StorageNodeSortField;
    sortDirection: SortDirection;
  }) => void;
  onOpenFolder: (item: StorageNode) => void;
  onPreviewFile: (item: StorageNode) => void | Promise<void>;
  onDownloadFile: (item: StorageNode) => void;
  onRenameNode: (item: StorageNode) => void;
  onMoveNode: (item: StorageNode) => void;
  onDeleteNode: (item: StorageNode) => void | Promise<void>;
  onRestoreNode: (item: StorageNode) => void | Promise<void>;
  onPermanentlyDeleteNode: (item: StorageNode) => void | Promise<void>;
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

export default function DriveExplorerView({
  mode,
  title,
  description,
  breadcrumbs,
  nodeTypeFilter,
  error,
  loading,
  uploading,
  items,
  selectedItems,
  selectedRowKeys,
  listState,
  uploadTasks,
  overallUploadProgress,
  downloadingFileId,
  previewingFileId,
  onRefresh,
  onUploadClick,
  onCreateFolderClick,
  onNodeTypeFilterChange,
  onJumpToCrumb,
  onRestoreSelection,
  onDeleteSelection,
  onPermanentDeleteSelection,
  onOpenBatchMove,
  onCancelActiveUploads,
  onRetryFailedUploads,
  onClearUploadHistory,
  onRetryUploadTask,
  onCancelUploadTask,
  getUploadTaskStatusText,
  onSelectionChange,
  onTableChange,
  onOpenFolder,
  onPreviewFile,
  onDownloadFile,
  onRenameNode,
  onMoveNode,
  onDeleteNode,
  onRestoreNode,
  onPermanentlyDeleteNode,
}: DriveExplorerViewProps) {
  const isTrashMode = mode === 'trash';
  const selectedCount = selectedItems.length;
  const uploadPanelVisible = uploadTasks.length > 0;
  const uploadFailedCount = uploadTasks.filter((task) => task.status === 'error').length;
  const uploadSuccessCount = uploadTasks.filter((task) => task.status === 'success').length;
  const uploadCanceledCount = uploadTasks.filter((task) => task.status === 'canceled').length;

  return (
    <section className="content-panel drive-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>{title}</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">{description}</Typography.Paragraph>
        </div>

        <div className="panel-actions">
          {!isTrashMode ? (
            <>
              <Button type="primary" icon={<UploadOutlined />} loading={uploading} onClick={onUploadClick}>
                上传文件
              </Button>
              <Button icon={<FolderAddOutlined />} onClick={onCreateFolderClick}>
                新建文件夹
              </Button>
            </>
          ) : null}

          <Button icon={<ReloadOutlined />} onClick={onRefresh}>
            刷新
          </Button>

          {selectedCount > 0 ? <span className="selection-pill">已选 {selectedCount} 项</span> : null}

          {isTrashMode ? (
            <>
              <Button icon={<RollbackOutlined />} disabled={selectedCount === 0} onClick={onRestoreSelection}>
                恢复所选
              </Button>
              <Popconfirm
                title="彻底删除所选项目"
                description="彻底删除后无法从回收站恢复。"
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                disabled={selectedCount === 0}
                onConfirm={onPermanentDeleteSelection}
              >
                <Button danger icon={<DeleteOutlined />} disabled={selectedCount === 0}>
                  彻底删除所选
                </Button>
              </Popconfirm>
            </>
          ) : (
            <>
              <Button icon={<SwapOutlined />} disabled={selectedCount === 0} onClick={onOpenBatchMove}>
                移动所选
              </Button>
              <Popconfirm
                title="移入回收站"
                description="可稍后在回收站中恢复或彻底删除。"
                okText="删除"
                cancelText="取消"
                okButtonProps={{ danger: true }}
                disabled={selectedCount === 0}
                onConfirm={onDeleteSelection}
              >
                <Button danger icon={<DeleteOutlined />} disabled={selectedCount === 0}>
                  删除所选
                </Button>
              </Popconfirm>
            </>
          )}
        </div>
      </div>

      <div className="drive-toolbar">
        <div className="drive-toolbar-left">
          {isTrashMode ? (
            <Typography.Text className="drive-toolbar-note">回收站里的项目支持恢复，也支持彻底删除。</Typography.Text>
          ) : (
            <Breadcrumb
              items={breadcrumbs.map((crumb, index) => ({
                title: (
                  <button className="crumb-button" onClick={() => onJumpToCrumb(index)}>
                    {crumb.label}
                  </button>
                ),
              }))}
            />
          )}
        </div>

        <div className="drive-toolbar-right">
          <Segmented<StorageNodeFilter>
            value={nodeTypeFilter}
            onChange={onNodeTypeFilterChange}
            options={[
              { label: '全部', value: 'ALL' },
              { label: '文件夹', value: 'FOLDER' },
              { label: '文件', value: 'FILE' },
            ]}
          />
        </div>
      </div>

      {error ? <Alert type="error" showIcon message="文件列表加载失败" description={error} /> : null}

      {uploadPanelVisible ? (
        <section className="upload-panel">
          <div className="upload-panel-header">
            <div>
              <Typography.Title level={5}>上传队列</Typography.Title>
              <Typography.Text className="muted-text">
                {uploading
                  ? `正在处理 ${uploadTasks.length} 个文件`
                  : `成功 ${uploadSuccessCount} 个，失败 ${uploadFailedCount} 个，取消 ${uploadCanceledCount} 个`}
              </Typography.Text>
            </div>
            <Space wrap>
              {uploading ? (
                <Button size="small" danger onClick={onCancelActiveUploads}>
                  取消当前上传
                </Button>
              ) : null}
              {uploadFailedCount > 0 ? (
                <Button size="small" onClick={onRetryFailedUploads} disabled={uploading}>
                  继续失败项
                </Button>
              ) : null}
              <Button size="small" onClick={onClearUploadHistory} disabled={uploading}>
                清除记录
              </Button>
            </Space>
          </div>

          <Progress
            percent={overallUploadProgress}
            status={uploadFailedCount > 0 && !uploading ? 'exception' : undefined}
          />

          <div className="upload-task-list">
            {uploadTasks.map((task) => (
              <div key={task.id} className="upload-task-row">
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
      ) : null}

      {loading ? (
        <div className="loading-box">
          <Spin size="large" />
        </div>
      ) : (
        <StorageTable
          mode={mode}
          items={items}
          loading={false}
          downloadingFileId={downloadingFileId}
          previewingFileId={previewingFileId}
          selectedRowKeys={selectedRowKeys}
          page={listState.page}
          pageSize={listState.size}
          totalItems={listState.totalItems}
          sortBy={listState.sortBy}
          sortDirection={listState.sortDirection}
          onSelectionChange={onSelectionChange}
          onTableChange={onTableChange}
          onOpenFolder={onOpenFolder}
          onPreviewFile={onPreviewFile}
          onDownloadFile={onDownloadFile}
          onRenameNode={onRenameNode}
          onMoveNode={onMoveNode}
          onDeleteNode={onDeleteNode}
          onRestoreNode={onRestoreNode}
          onPermanentlyDeleteNode={onPermanentlyDeleteNode}
        />
      )}
    </section>
  );
}
