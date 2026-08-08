import {
  CloudDownloadOutlined,
  DeleteOutlined,
  FolderAddOutlined,
  ReloadOutlined,
  RollbackOutlined,
  SwapOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { Alert, Breadcrumb, Button, Popconfirm, Segmented, Spin, Typography } from 'antd';
import { StorageTable } from '../../components/StorageTable';
import type { SortDirection, StorageNode, StorageNodeFilter, StorageNodeSortField } from '../../types';
import type { DriveDownloadButtonState, DriveListState, FolderCrumb } from './types';

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
  downloadSelectionState: DriveDownloadButtonState;
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
  onDownloadSelection: () => void;
  onSelectionChange: (items: StorageNode[]) => void;
  onTableChange: (options: {
    page: number;
    pageSize: number;
    sortBy: StorageNodeSortField;
    sortDirection: SortDirection;
  }) => void;
  onOpenFolder: (item: StorageNode) => void;
  onPreviewFile: (item: StorageNode) => void | Promise<void>;
  onDownloadNode: (item: StorageNode) => void;
  getNodeDownloadButtonState: (item: StorageNode) => DriveDownloadButtonState;
  onShareNode: (item: StorageNode) => void;
  onRenameNode: (item: StorageNode) => void;
  onMoveNode: (item: StorageNode) => void;
  onDeleteNode: (item: StorageNode) => void | Promise<void>;
  onRestoreNode: (item: StorageNode) => void | Promise<void>;
  onPermanentlyDeleteNode: (item: StorageNode) => void | Promise<void>;
};

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
  downloadSelectionState,
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
  onDownloadSelection,
  onSelectionChange,
  onTableChange,
  onOpenFolder,
  onPreviewFile,
  onDownloadNode,
  getNodeDownloadButtonState,
  onShareNode,
  onRenameNode,
  onMoveNode,
  onDeleteNode,
  onRestoreNode,
  onPermanentlyDeleteNode,
}: DriveExplorerViewProps) {
  const isTrashMode = mode === 'trash';
  const selectedCount = selectedItems.length;

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
              <Button
                icon={<CloudDownloadOutlined />}
                disabled={selectedCount === 0 || downloadSelectionState.busy}
                onClick={onDownloadSelection}
              >
                {downloadSelectionState.label}
              </Button>
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
          {selectedCount > 0 ? <span className="selection-pill">已选 {selectedCount} 项</span> : null}
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

      {loading ? (
        <div className="loading-box">
          <Spin size="large" />
        </div>
      ) : (
        <StorageTable
          mode={mode}
          items={items}
          loading={false}
          previewingFileId={previewingFileId}
          getDownloadButtonState={getNodeDownloadButtonState}
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
          onDownloadNode={onDownloadNode}
          onShareNode={onShareNode}
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
