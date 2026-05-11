import { Form, Input, Modal, TreeSelect } from 'antd';
import type { FormInstance } from 'antd/es/form';
import type { CreateFolderPayload, RenameNodePayload, StorageNode } from '../../types';
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
        title="新建文件夹"
        open={createFolderOpen}
        onCancel={onCloseCreateFolder}
        onOk={() => void createFolderForm.submit()}
        destroyOnHidden
      >
        <Form form={createFolderForm} layout="vertical" onFinish={(values) => void onSubmitCreateFolder(values)}>
          <Form.Item
            name="folderName"
            label="文件夹名称"
            rules={[{ required: true, message: '请输入文件夹名称。' }]}
          >
            <Input placeholder="例如：项目资料" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="重命名"
        open={renameTarget !== null}
        onCancel={onCloseRename}
        onOk={() => void renameForm.submit()}
        destroyOnHidden
      >
        <Form form={renameForm} layout="vertical" onFinish={(values) => void onSubmitRename(values)}>
          <Form.Item
            name="name"
            label="名称"
            rules={[
              { required: true, message: '请输入名称。' },
              { max: 255, message: '名称长度不能超过 255 个字符。' },
            ]}
          >
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={moveDialogTitle}
        open={moveTargetsCount > 0}
        onCancel={onCloseMove}
        onOk={() => void moveForm.submit()}
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
