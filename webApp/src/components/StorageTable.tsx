import { Download, Eye, File, FolderOpen, Move, Pencil, Share2, Trash2, Undo2 } from 'lucide-react';
import { Button, Popconfirm, Space, Table, Typography } from 'antd';
import type { TablePaginationConfig, TableProps } from 'antd';
import type { SorterResult } from 'antd/es/table/interface';
import type { SortDirection, StorageNode, StorageNodeSortField, StorageViewMode } from '../types';
import type { DriveDownloadButtonState, DriveStorageMutationKind, DriveStorageMutationState } from '../features/drive/types';
import { Icon } from './Icon';

type StorageTableProps = {
  mode: StorageViewMode;
  items: StorageNode[];
  loading: boolean;
  previewingFileId: number | null;
  storageMutation: DriveStorageMutationState;
  getDownloadButtonState: (item: StorageNode) => DriveDownloadButtonState;
  selectedRowKeys: number[];
  page: number;
  pageSize: number;
  totalItems: number;
  sortBy: StorageNodeSortField;
  sortDirection: SortDirection;
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
  onShareNode: (item: StorageNode) => void;
  onRenameNode: (item: StorageNode) => void;
  onMoveNode: (item: StorageNode) => void;
  onDeleteNode: (item: StorageNode) => void | Promise<void>;
  onRestoreNode: (item: StorageNode) => void | Promise<void>;
  onPermanentlyDeleteNode: (item: StorageNode) => void | Promise<void>;
};

function formatBytes(value: number) {
  if (value === 0) {
    return '-';
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

function formatTimestamp(value: string) {
  return new Date(value).toLocaleString('zh-CN');
}

function toSortOrder(activeSortBy: StorageNodeSortField, columnSortBy: StorageNodeSortField, direction: SortDirection) {
  if (activeSortBy !== columnSortBy) {
    return null;
  }

  return direction === 'asc' ? 'ascend' : 'descend';
}

function normalizeSorter(
  sorter: SorterResult<StorageNode> | SorterResult<StorageNode>[],
  fallbackSortBy: StorageNodeSortField,
  fallbackDirection: SortDirection,
): { sortBy: StorageNodeSortField; sortDirection: SortDirection } {
  const normalizedSorter = Array.isArray(sorter) ? sorter[0] : sorter;

  if (!normalizedSorter || !normalizedSorter.columnKey) {
    return { sortBy: fallbackSortBy, sortDirection: fallbackDirection };
  }

  const nextSortBy = normalizedSorter.columnKey as StorageNodeSortField;

  if (!normalizedSorter.order) {
    if (nextSortBy === fallbackSortBy) {
      return {
        sortBy: nextSortBy,
        sortDirection: fallbackDirection === 'asc' ? 'desc' : 'asc',
      };
    }

    return {
      sortBy: nextSortBy,
      sortDirection: 'asc',
    };
  }

  return {
    sortBy: nextSortBy,
    sortDirection: normalizedSorter.order === 'descend' ? 'desc' : 'asc',
  };
}

export function StorageTable({
  mode,
  items,
  loading,
  previewingFileId,
  storageMutation,
  getDownloadButtonState,
  selectedRowKeys,
  page,
  pageSize,
  totalItems,
  sortBy,
  sortDirection,
  onSelectionChange,
  onTableChange,
  onOpenFolder,
  onPreviewFile,
  onDownloadNode,
  onShareNode,
  onRenameNode,
  onMoveNode,
  onDeleteNode,
  onRestoreNode,
  onPermanentlyDeleteNode,
}: StorageTableProps) {
  const isTrashMode = mode === 'trash';
  const timeTitle = isTrashMode ? '删除时间' : '更新时间';
  const storageMutationPending = storageMutation !== null;

  function isNodeStorageMutating(item: StorageNode, kind: DriveStorageMutationKind) {
    return storageMutation?.kind === kind && storageMutation.nodeIds.includes(item.id);
  }

  const columns: TableProps<StorageNode>['columns'] = [
    {
      title: '文件名',
      dataIndex: 'name',
      key: 'name',
      width: 360,
      sorter: true,
      sortDirections: ['ascend', 'descend', 'ascend'],
      sortOrder: toSortOrder(sortBy, 'name', sortDirection),
      render: (_, item) => {
        const meta = item.type === 'FOLDER' ? '文件夹' : item.extension ? item.extension.toUpperCase() : '文件';
        const content = (
          <>
            <span className={`storage-icon-shell${item.type === 'FOLDER' ? ' storage-folder-icon' : ''}`}>
              {item.type === 'FOLDER' ? (
                <Icon icon={FolderOpen} size={18} />
              ) : (
                <Icon icon={File} size={18} />
              )}
            </span>

            <span className="storage-name-copy">
              <Typography.Text strong={item.type === 'FOLDER'} ellipsis={{ tooltip: item.name }} className="storage-name-title">
                {item.name}
              </Typography.Text>
              <Typography.Text className="storage-name-meta">{meta}</Typography.Text>
            </span>
          </>
        );

        if (!isTrashMode && item.type === 'FOLDER') {
          return (
            <button
              type="button"
              className="storage-name-cell storage-folder-entry"
              disabled={storageMutationPending}
              onClick={() => onOpenFolder(item)}
            >
              {content}
            </button>
          );
        }

        return (
          <div className="storage-name-cell">
            {content}
          </div>
        );
      },
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 140,
      sorter: true,
      sortDirections: ['ascend', 'descend', 'ascend'],
      sortOrder: toSortOrder(sortBy, 'size', sortDirection),
      render: (value: number) => formatBytes(value),
    },
    {
      title: timeTitle,
      key: isTrashMode ? 'deletedAt' : 'updatedAt',
      width: 200,
      sorter: true,
      sortDirections: ['ascend', 'descend', 'ascend'],
      sortOrder: toSortOrder(sortBy, isTrashMode ? 'deletedAt' : 'updatedAt', sortDirection),
      render: (_, item) => formatTimestamp(isTrashMode ? item.deletedAt || item.updatedAt : item.updatedAt),
    },
    {
      title: '操作',
      key: 'actions',
      width: isTrashMode ? 240 : 450,
      render: (_, item) => {
        const downloadState = getDownloadButtonState(item);
        const restoring = isNodeStorageMutating(item, 'restore');
        const deleting = isNodeStorageMutating(item, 'delete');
        const permanentlyDeleting = isNodeStorageMutating(item, 'permanent-delete');
        const renaming = isNodeStorageMutating(item, 'rename');
        const moving = isNodeStorageMutating(item, 'move');

        return isTrashMode ? (
          <Space size="small" wrap>
            <Button
              type="link"
              icon={<Icon icon={Undo2} />}
              loading={restoring}
              disabled={storageMutationPending && !restoring}
              onClick={() => onRestoreNode(item)}
            >
              恢复
            </Button>
            <Popconfirm
              title="彻底删除"
              description="彻底删除后无法从回收站恢复。"
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true, loading: permanentlyDeleting, disabled: storageMutationPending && !permanentlyDeleting }}
              cancelButtonProps={{ disabled: permanentlyDeleting }}
              disabled={storageMutationPending && !permanentlyDeleting}
              onConfirm={() => onPermanentlyDeleteNode(item)}
            >
              <Button
                type="link"
                danger
                icon={<Icon icon={Trash2} />}
                loading={permanentlyDeleting}
                disabled={storageMutationPending && !permanentlyDeleting}
              >
                彻底删除
              </Button>
            </Popconfirm>
          </Space>
        ) : (
          <Space size="small" wrap>
            {item.type === 'FILE' ? (
              <Button
                type="link"
                icon={<Icon icon={Eye} />}
                loading={previewingFileId === item.id}
                disabled={storageMutationPending}
                onClick={() => onPreviewFile(item)}
              >
                预览
              </Button>
            ) : null}
            <Button
              type="link"
              icon={<Icon icon={Download} />}
              disabled={downloadState.busy || storageMutationPending}
              onClick={() => onDownloadNode(item)}
            >
              {downloadState.label}
            </Button>
            <Button type="link" icon={<Icon icon={Share2} />} disabled={storageMutationPending} onClick={() => onShareNode(item)}>
              分享
            </Button>
            <Button
              type="link"
              icon={<Icon icon={Pencil} />}
              loading={renaming}
              disabled={storageMutationPending && !renaming}
              onClick={() => onRenameNode(item)}
            >
              重命名
            </Button>
            <Button
              type="link"
              icon={<Icon icon={Move} />}
              loading={moving}
              disabled={storageMutationPending && !moving}
              onClick={() => onMoveNode(item)}
            >
              移动
            </Button>
            <Popconfirm
              title="移入回收站"
              description="稍后仍可在回收站中恢复或彻底删除。"
              okText="删除"
              cancelText="取消"
              okButtonProps={{ danger: true, loading: deleting, disabled: storageMutationPending && !deleting }}
              cancelButtonProps={{ disabled: deleting }}
              disabled={storageMutationPending && !deleting}
              onConfirm={() => onDeleteNode(item)}
            >
              <Button
                type="link"
                danger
                icon={<Icon icon={Trash2} />}
                loading={deleting}
                disabled={storageMutationPending && !deleting}
              >
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <div className="storage-table-shell">
      <Table
        rowKey="id"
        className="storage-data-table"
        rowSelection={{
          selectedRowKeys,
          onChange: (_, selectedRows) => onSelectionChange(selectedRows),
          getCheckboxProps: () => ({ disabled: storageMutation !== null }),
        }}
        loading={loading}
        columns={columns}
        dataSource={items}
        pagination={{
          current: page,
          pageSize,
          total: totalItems,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          position: ['bottomRight'],
          showTotal: (total, range) => `第 ${range[0]}-${range[1]} 项，共 ${total} 项`,
        }}
        size="middle"
        scroll={{ x: isTrashMode ? 980 : 1200 }}
        locale={{ emptyText: isTrashMode ? '回收站里还没有内容。' : '当前目录下还没有内容。' }}
        onChange={(pagination: TablePaginationConfig, _, sorter) => {
          const nextPagination = {
            page: pagination.current ?? page,
            pageSize: pagination.pageSize ?? pageSize,
          };
          const nextSort = normalizeSorter(sorter, sortBy, sortDirection);

          onTableChange({
            ...nextPagination,
            ...nextSort,
          });
        }}
      />
    </div>
  );
}
