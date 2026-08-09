import type { StorageNode } from '../../types';

export function collapseSelectedShareNodeIds(items: StorageNode[], rawSelectedNodeIds: number[]) {
  const nodeIds = new Set(items.map((item) => item.id));
  const selectedNodeIds = new Set(rawSelectedNodeIds.filter((nodeId) => nodeIds.has(nodeId)));
  const parentById = new Map(items.map((item) => [item.id, item.parentId]));
  const childCountById = new Map(items.map((item) => [item.id, 0]));

  items.forEach((item) => {
    if (item.parentId === null || !nodeIds.has(item.parentId)) {
      return;
    }
    childCountById.set(item.parentId, (childCountById.get(item.parentId) ?? 0) + 1);
  });

  const remainingChildCount = new Map(childCountById);
  const fullySelectedChildCount = new Map<number, number>();
  const fullySelectedNodeIds = new Set<number>();
  const pending = items.filter((item) => childCountById.get(item.id) === 0).map((item) => item.id);

  while (pending.length > 0) {
    const nodeId = pending.pop()!;
    const fullySelected = selectedNodeIds.has(nodeId) &&
      (fullySelectedChildCount.get(nodeId) ?? 0) === childCountById.get(nodeId);
    if (fullySelected) {
      fullySelectedNodeIds.add(nodeId);
    }

    const parentId = parentById.get(nodeId) ?? null;
    if (parentId === null || !nodeIds.has(parentId)) {
      continue;
    }
    if (fullySelected) {
      fullySelectedChildCount.set(parentId, (fullySelectedChildCount.get(parentId) ?? 0) + 1);
    }
    const remaining = (remainingChildCount.get(parentId) ?? 0) - 1;
    remainingChildCount.set(parentId, remaining);
    if (remaining === 0) {
      pending.push(parentId);
    }
  }

  return items
    .filter((item) =>
      fullySelectedNodeIds.has(item.id) &&
      (item.parentId === null || !fullySelectedNodeIds.has(item.parentId)),
    )
    .map((item) => item.id);
}
