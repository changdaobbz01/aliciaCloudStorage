import { Form } from 'antd';
import { useMemo, useState } from 'react';
import type { CreateFolderPayload, RenameNodePayload, StorageNode } from '../../../types';
import { ROOT_PARENT_KEY } from '../driveShared';
import type { DriveStorageMutationState, FolderTreeNode } from '../types';

type MoveNodeFormValues = {
  parentKey: string;
};

type UseDriveStorageDialogsOptions = {
  selectedItems: StorageNode[];
  folderOptions: StorageNode[];
  storageMutation: DriveStorageMutationState;
  loadFolderOptions: () => Promise<void>;
  createFolderNode: (values: CreateFolderPayload) => Promise<boolean>;
  renameNode: (target: StorageNode | null, values: RenameNodePayload) => Promise<boolean>;
  moveNodes: (targets: StorageNode[], parentKey: string) => Promise<boolean>;
};

export function useDriveStorageDialogs({
  selectedItems,
  folderOptions,
  storageMutation,
  loadFolderOptions,
  createFolderNode,
  renameNode,
  moveNodes,
}: UseDriveStorageDialogsOptions) {
  const [createFolderOpen, setCreateFolderOpen] = useState(false);
  const [renameTarget, setRenameTarget] = useState<StorageNode | null>(null);
  const [moveTargets, setMoveTargets] = useState<StorageNode[]>([]);
  const [createFolderForm] = Form.useForm<CreateFolderPayload>();
  const [renameForm] = Form.useForm<RenameNodePayload>();
  const [moveForm] = Form.useForm<MoveNodeFormValues>();

  const folderTreeData = useMemo(() => {
    const allChildrenMap = new Map<number | null, StorageNode[]>();

    folderOptions.forEach((folder) => {
      const siblings = allChildrenMap.get(folder.parentId) ?? [];
      siblings.push(folder);
      allChildrenMap.set(folder.parentId, siblings);
    });

    const blockedIds = new Set<number>();
    const collectDescendantIds = (folderId: number) => {
      (allChildrenMap.get(folderId) ?? []).forEach((childFolder) => {
        blockedIds.add(childFolder.id);
        collectDescendantIds(childFolder.id);
      });
    };

    moveTargets
      .filter((target) => target.type === 'FOLDER')
      .forEach((target) => {
        blockedIds.add(target.id);
        collectDescendantIds(target.id);
      });

    const visibleChildrenMap = new Map<number | null, StorageNode[]>();
    folderOptions
      .filter((folder) => !blockedIds.has(folder.id))
      .forEach((folder) => {
        const siblings = visibleChildrenMap.get(folder.parentId) ?? [];
        siblings.push(folder);
        visibleChildrenMap.set(folder.parentId, siblings);
      });

    const buildTree = (parentId: number | null): FolderTreeNode[] =>
      (visibleChildrenMap.get(parentId) ?? [])
        .sort((left, right) => left.name.localeCompare(right.name, 'zh-CN'))
        .map((folder) => {
          const children = buildTree(folder.id);

          return {
            title: folder.name,
            value: String(folder.id),
            children: children.length > 0 ? children : undefined,
          };
        });

    return [
      {
        title: '根目录',
        value: ROOT_PARENT_KEY,
        children: buildTree(null),
      },
    ];
  }, [folderOptions, moveTargets]);

  function openCreateFolderModal() {
    if (storageMutation) {
      return;
    }

    createFolderForm.resetFields();
    setCreateFolderOpen(true);
  }

  function closeCreateFolderModal() {
    if (storageMutation) {
      return;
    }

    setCreateFolderOpen(false);
  }

  function openRenameModal(item: StorageNode) {
    if (storageMutation) {
      return;
    }

    renameForm.setFieldsValue({ name: item.name });
    setRenameTarget(item);
  }

  function closeRenameModal() {
    if (storageMutation) {
      return;
    }

    setRenameTarget(null);
  }

  function openMoveModal(targets: StorageNode[] | StorageNode) {
    if (storageMutation) {
      return;
    }

    const normalizedTargets = Array.isArray(targets) ? targets : [targets];
    const firstTarget = normalizedTargets[0];

    if (!firstTarget) {
      return;
    }

    const sameParent = normalizedTargets.every((target) => target.parentId === firstTarget.parentId);
    moveForm.setFieldsValue({
      parentKey: sameParent
        ? firstTarget.parentId === null
          ? ROOT_PARENT_KEY
          : String(firstTarget.parentId)
        : ROOT_PARENT_KEY,
    });
    setMoveTargets(normalizedTargets);
    void loadFolderOptions();
  }

  function closeMoveModal() {
    if (storageMutation) {
      return;
    }

    setMoveTargets([]);
  }

  function openBatchMoveModal() {
    if (storageMutation) {
      return;
    }

    if (selectedItems.length === 0) {
      return;
    }

    openMoveModal(selectedItems);
  }

  async function submitCreateFolder(values: CreateFolderPayload) {
    if (storageMutation) {
      return false;
    }

    const success = await createFolderNode(values);
    if (!success) {
      return false;
    }

    createFolderForm.resetFields();
    setCreateFolderOpen(false);
    return true;
  }

  async function submitRename(values: RenameNodePayload) {
    if (storageMutation) {
      return false;
    }

    const success = await renameNode(renameTarget, values);
    if (!success) {
      return false;
    }

    setRenameTarget(null);
    renameForm.resetFields();
    return true;
  }

  async function submitMove(values: MoveNodeFormValues) {
    if (storageMutation) {
      return false;
    }

    const success = await moveNodes(moveTargets, values.parentKey);
    if (!success) {
      return false;
    }

    setMoveTargets([]);
    moveForm.resetFields();
    return true;
  }

  return {
    createFolderOpen,
    renameTarget,
    moveTargets,
    moveTarget: moveTargets.length === 1 ? moveTargets[0] : null,
    moveDialogTitle: moveTargets.length > 1 ? '批量移动' : '移动',
    createFolderForm,
    renameForm,
    moveForm,
    folderTreeData,
    openCreateFolderModal,
    closeCreateFolderModal,
    openRenameModal,
    closeRenameModal,
    openMoveModal,
    closeMoveModal,
    openBatchMoveModal,
    submitCreateFolder,
    submitRename,
    submitMove,
  };
}
