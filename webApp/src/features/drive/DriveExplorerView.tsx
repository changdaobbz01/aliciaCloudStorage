import { CloudDownload, FileArchive, FileImage, FileText, FolderPlus, LayoutGrid, Move, Music, RefreshCw, Share2, Trash2, Undo2, Upload, Video } from 'lucide-react';
import { Alert, Breadcrumb, Button, Popconfirm, Segmented, Spin, Typography } from 'antd';
import { StorageTable } from '../../components/StorageTable';
import type { SortDirection, StorageFileCategory, StorageNode, StorageNodeFilter, StorageNodeSortField } from '../../types';
import type { DriveDownloadButtonState, DriveListState, FolderCrumb } from './types';
import { Icon } from '../../components/Icon';

type DriveExplorerViewProps = {
  mode: 'drive' | 'trash';
  title: string;
  description: string;
  breadcrumbs: FolderCrumb[];
  nodeTypeFilter: StorageNodeFilter;
  fileCategory: StorageFileCategory | null;
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
  onFileCategoryChange: (value: StorageFileCategory | null) => void;
  onNodeTypeFilterChange: (value: StorageNodeFilter) => void;
  onJumpToCrumb: (index: number) => void;
  onRestoreSelection: () => void;
  onDeleteSelection: () => void;
  onPermanentDeleteSelection: () => void;
  onOpenBatchMove: () => void;
  onDownloadSelection: () => void;
  onShareSelection: () => void;
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

const fileCategoryOptions = [
  { value: null, label: '全部', icon: <Icon icon={LayoutGrid} /> },
  { value: 'IMAGE' as const, label: '相册', icon: <Icon icon={FileImage} /> },
  { value: 'VIDEO' as const, label: '视频', icon: <Icon icon={Video} /> },
  { value: 'DOCUMENT' as const, label: '文档', icon: <Icon icon={FileText} /> },
  { value: 'AUDIO' as const, label: '音频', icon: <Icon icon={Music} /> },
  { value: 'ARCHIVE' as const, label: '压缩包', icon: <Icon icon={FileArchive} /> },
];

export default function DriveExplorerView({
  mode,
  title,
  description,
  breadcrumbs,
  nodeTypeFilter,
  fileCategory,
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
  onFileCategoryChange,
  onNodeTypeFilterChange,
  onJumpToCrumb,
  onRestoreSelection,
  onDeleteSelection,
  onPermanentDeleteSelection,
  onOpenBatchMove,
  onDownloadSelection,
  onShareSelection,
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
  const activeCategory = fileCategory;

  return (
    <section className="content-panel drive-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>{title}</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">{description}</Typography.Paragraph>
        </div>

        <div className="panel-actions">
          {!isTrashMode && !activeCategory ? (
            <>
              <Button type="primary" icon={<Icon icon={Upload} />} loading={uploading} onClick={onUploadClick}>
                上传文件
              </Button>
              <Button icon={<Icon icon={FolderPlus} />} onClick={onCreateFolderClick}>
                新建文件夹
              </Button>
            </>
          ) : null}

          <Button icon={<Icon icon={RefreshCw} />} onClick={onRefresh}>
            刷新
          </Button>

          {isTrashMode ? (
            <>
              <Button icon={<Icon icon={Undo2} />} disabled={selectedCount === 0} onClick={onRestoreSelection}>
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
                <Button danger icon={<Icon icon={Trash2} />} disabled={selectedCount === 0}>
                  彻底删除所选
                </Button>
              </Popconfirm>
            </>
          ) : (
            <>
              <Button
                icon={<Icon icon={CloudDownload} />}
                disabled={selectedCount === 0 || downloadSelectionState.busy}
                onClick={onDownloadSelection}
              >
                {downloadSelectionState.label}
              </Button>
              <Button
                icon={<Icon icon={Share2} />}
                disabled={selectedCount === 0}
                title={selectedCount > 20 ? '单个分享最多包含 20 个项目' : undefined}
                onClick={onShareSelection}
              >
                分享所选
              </Button>
              <Button icon={<Icon icon={Move} />} disabled={selectedCount === 0} onClick={onOpenBatchMove}>
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
                <Button danger icon={<Icon icon={Trash2} />} disabled={selectedCount === 0}>
                  删除所选
                </Button>
              </Popconfirm>
            </>
          )}
        </div>
      </div>

      {!isTrashMode ? (
        <div className="drive-category-strip" aria-label="文件分类">
          {fileCategoryOptions.map((option) => {
            const active = option.value === activeCategory;

            return (
              <button
                key={option.value ?? 'ALL'}
                type="button"
                className={`drive-category-button${active ? ' drive-category-button-active' : ''}`}
                onClick={() => onFileCategoryChange(option.value)}
              >
                <span className="drive-category-icon">{option.icon}</span>
                <span className="drive-category-label">{option.label}</span>
              </button>
            );
          })}
        </div>
      ) : null}

      <div className="drive-toolbar">
        <div className="drive-toolbar-left">
          {isTrashMode ? (
            <Typography.Text className="drive-toolbar-note">回收站里的项目支持恢复，也支持彻底删除。</Typography.Text>
          ) : activeCategory ? (
            <Typography.Text className="drive-toolbar-note">全盘分类 / {title}</Typography.Text>
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
          {activeCategory ? (
            <span className="drive-category-mode-pill">分类视图</span>
          ) : (
            <Segmented<StorageNodeFilter>
              value={nodeTypeFilter}
              onChange={onNodeTypeFilterChange}
              options={[
                { label: '全部', value: 'ALL' },
                { label: '文件夹', value: 'FOLDER' },
                { label: '文件', value: 'FILE' },
              ]}
            />
          )}
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
