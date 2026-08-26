import { Form, Input, Modal, TreeSelect } from 'antd';
import type { FormInstance } from 'antd/es/form';
import { AliciaModalTitle } from '../../components/AliciaModalTitle';
import type { CreateFolderPayload, RenameNodePayload, StorageNode } from '../../types';
import { validateStorageNodeName } from './cloudOperationPolicy';
import type { FolderTreeNode } from './types';

type MoveNodeFormValues = {
  parentKey: string;
};

type DriveStorageActionModalsProps = {
  createFolderOpen: boolean;
  renameTarget: StorageNode | null;
  moveTargetsCount: number;
  moveDialogTitle: string;
  folderOptionsLoading: boolean;
  folderTreeData: FolderTreeNode[];
  createFolderForm: FormInstance<CreateFolderPayload>;
  renameForm: FormInstance<RenameNodePayload>;
  moveForm: FormInstance<MoveNodeFormValues>;
  onCloseCreateFolder: () => void;
  onSubmitCreateFolder: (values: CreateFolderPayload) => void | Promise<unknown>;
  onCloseRename: () => void;
  onSubmitRename: (values: RenameNodePayload) => void | Promise<unknown>;
  onCloseMove: () => void;
  onSubmitMove: (values: MoveNodeFormValues) => void | Promise<unknown>;
};

function storageNodeNameRule(currentName?: string | null) {
  return {
    validator: async (_: unknown, value?: string) => {
      const validation = validateStorageNodeName(value ?? '', currentName);
      if (!validation.valid) {
        throw new Error(validation.message);
      }
    },
  };
}

export function DriveStorageActionModals({
  createFolderOpen,
  renameTarget,
  moveTargetsCount,
  moveDialogTitle,
  folderOptionsLoading,
  folderTreeData,
  createFolderForm,
  renameForm,
  moveForm,
  onCloseCreateFolder,
  onSubmitCreateFolder,
  onCloseRename,
  onSubmitRename,
  onCloseMove,
  onSubmitMove,
}: DriveStorageActionModalsProps) {
  return (
    <>
      <Modal
        title={<AliciaModalTitle eyebrow="Storage">新建文件夹</AliciaModalTitle>}
        rootClassName="alicia-modal alicia-storage-modal"
        open={createFolderOpen}
        onCancel={onCloseCreateFolder}
        onOk={() => void createFolderForm.submit()}
        okText="创建"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={createFolderForm} layout="vertical" onFinish={(values) => void onSubmitCreateFolder(values)}>
          <Form.Item
            name="folderName"
            label="文件夹名称"
            rules={[storageNodeNameRule()]}
          >
            <Input placeholder="例如：项目资料" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={<AliciaModalTitle eyebrow="Storage">重命名</AliciaModalTitle>}
        rootClassName="alicia-modal alicia-storage-modal"
        open={renameTarget !== null}
        onCancel={onCloseRename}
        onOk={() => void renameForm.submit()}
        okText="保存"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={renameForm} layout="vertical" onFinish={(values) => void onSubmitRename(values)}>
          <Form.Item
            name="name"
            label="名称"
            rules={[storageNodeNameRule(renameTarget?.name)]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={<AliciaModalTitle eyebrow="Storage">{moveDialogTitle}</AliciaModalTitle>}
        rootClassName="alicia-modal alicia-storage-modal"
        open={moveTargetsCount > 0}
        onCancel={onCloseMove}
        onOk={() => void moveForm.submit()}
        okText="移动"
        cancelText="取消"
        destroyOnHidden
      >
        <Form form={moveForm} layout="vertical" onFinish={(values) => void onSubmitMove(values)}>
          <Form.Item
            name="parentKey"
            label="目标文件夹"
            rules={[{ required: true, message: '请选择目标文件夹。' }]}
          >
            <TreeSelect
              showSearch
              treeDefaultExpandAll
              treeData={folderTreeData}
              treeNodeFilterProp="title"
              disabled={folderOptionsLoading}
              placeholder="选择目标文件夹"
            />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
