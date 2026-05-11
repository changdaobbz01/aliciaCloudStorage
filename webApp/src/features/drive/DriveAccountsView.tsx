import { Alert } from 'antd';
import { UserManagementPanel } from '../../components/UserManagementPanel';
import type { User } from '../../types';

type DriveAccountsViewProps = {
  isAdmin: boolean;
  currentUserId: number | undefined;
  users: User[];
  loading: boolean;
  onCreateUser: () => void;
  onEditUserQuota: (user: User) => void;
  onResetUserPassword: (user: User) => void;
};

export default function DriveAccountsView({
  isAdmin,
  currentUserId,
  users,
  loading,
  onCreateUser,
  onEditUserQuota,
  onResetUserPassword,
}: DriveAccountsViewProps) {
  if (!isAdmin) {
    return (
      <section className="content-panel">
        <Alert
          type="warning"
          showIcon
          message="当前账号没有账号管理权限"
          description="只有管理员可以新增和查看账号。"
        />
      </section>
    );
  }

  return (
    <UserManagementPanel
      currentUserId={currentUserId}
      users={users}
      loading={loading}
      onCreateUser={onCreateUser}
      onEditUserQuota={onEditUserQuota}
      onResetUserPassword={onResetUserPassword}
    />
  );
}
