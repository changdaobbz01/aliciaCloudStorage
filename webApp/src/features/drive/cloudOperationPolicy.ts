import type { StorageNode } from '../../types';

export const MAX_BATCH_NODE_OPERATION_ITEMS = 500;
export const MAX_STORAGE_ARCHIVE_ROOTS = 100;
export const MAX_SHARE_TARGETS = 20;
export const MAX_SHARE_SAVE_SELECTED_ITEMS = 500;
export const MAX_NODE_NAME_LENGTH = 255;

type ValidationResult<T> =
  | {
      valid: true;
      value: T;
    }
  | {
      valid: false;
      message: string;
    };

export function uniqueStorageNodes(nodes: StorageNode[]) {
  return [...new Map(nodes.map((node) => [node.id, node])).values()];
}

export function uniquePositiveNodeIds(nodeIds: number[]) {
  return nodeIds.filter((nodeId) => Number.isInteger(nodeId) && nodeId > 0)
    .filter((nodeId, index, normalizedIds) => normalizedIds.indexOf(nodeId) === index);
}

export function validateBatchNodeTargets(
  nodes: StorageNode[],
  emptyMessage: string,
): ValidationResult<{ targets: StorageNode[]; nodeIds: number[] }> {
  const targets = uniqueStorageNodes(nodes).filter((node) => Number.isInteger(node.id) && node.id > 0);

  if (targets.length === 0) {
    return { valid: false, message: emptyMessage };
  }

  if (targets.length > MAX_BATCH_NODE_OPERATION_ITEMS) {
    return { valid: false, message: `单次最多处理 ${MAX_BATCH_NODE_OPERATION_ITEMS} 个项目。` };
  }

  return {
    valid: true,
    value: {
      targets,
      nodeIds: targets.map((target) => target.id),
    },
  };
}

export function validateArchiveNodeIds(
  rawNodeIds: number[],
  emptyMessage: string,
): ValidationResult<number[]> {
  const nodeIds = uniquePositiveNodeIds(rawNodeIds);

  if (nodeIds.length === 0) {
    return { valid: false, message: emptyMessage };
  }

  if (nodeIds.length > MAX_STORAGE_ARCHIVE_ROOTS) {
    return { valid: false, message: `单次最多打包下载 ${MAX_STORAGE_ARCHIVE_ROOTS} 个项目，请减少选择后重试。` };
  }

  return { valid: true, value: nodeIds };
}

export function validateShareSaveNodeIds(
  rawNodeIds: number[],
): ValidationResult<number[]> {
  const nodeIds = uniquePositiveNodeIds(rawNodeIds);

  if (nodeIds.length === 0) {
    return { valid: false, message: '请先选择要保存的分享内容。' };
  }

  if (nodeIds.length > MAX_SHARE_SAVE_SELECTED_ITEMS) {
    return { valid: false, message: `单次最多保存 ${MAX_SHARE_SAVE_SELECTED_ITEMS} 个分享项目，请减少选择后重试。` };
  }

  return { valid: true, value: nodeIds };
}

export function parseFolderParentKey(parentKey: string, rootParentKey: string): ValidationResult<number | null> {
  if (parentKey === rootParentKey) {
    return { valid: true, value: null };
  }

  const parentId = Number(parentKey);
  if (!Number.isInteger(parentId) || parentId <= 0) {
    return { valid: false, message: '请选择有效的目标文件夹。' };
  }

  return { valid: true, value: parentId };
}

export function validateStorageNodeName(rawName: string, currentName?: string | null): ValidationResult<string> {
  const normalizedName = rawName.trim();

  if (!normalizedName) {
    return { valid: false, message: '名称不能为空。' };
  }

  if (normalizedName.length > MAX_NODE_NAME_LENGTH) {
    return { valid: false, message: `名称长度不能超过 ${MAX_NODE_NAME_LENGTH} 个字符。` };
  }

  if (normalizedName.includes('/') || normalizedName.includes('\\')) {
    return { valid: false, message: '名称不能包含斜杠。' };
  }

  if (currentName !== null && currentName !== undefined && normalizedName === currentName) {
    return { valid: false, message: '请输入新的名称。' };
  }

  return { valid: true, value: normalizedName };
}
