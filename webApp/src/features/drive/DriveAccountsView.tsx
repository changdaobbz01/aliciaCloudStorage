import { Alert, Tabs } from 'antd';
import { UserManagementPanel } from '../../components/UserManagementPanel';
import type { IdentityAuditLogPage, IdentityAuditLogQuery, User } from '../../types';
import { IdentityAuditLogPanel } from './IdentityAuditLogPanel';

type DriveAccountsViewProps = {
  isAdmin: boolean;
  currentUserId: number | undefined;
  users: User[];
  loading: boolean;
  auditLogPage: IdentityAuditLogPage | null;
  auditLogQuery: IdentityAuditLogQuery;
  auditLogsLoading: boolean;
  onCreateUser: () => void;
  onEditUserQuota: (user: User) => void;
  onResetUserPassword: (user: User) => void;
  onApplyAuditLogQuery: (query: IdentityAuditLogQuery) => void;
  onAuditLogPageChange: (page: number, size: number) => void;
  onRefreshAuditLogs: () => void;
};

export default function DriveAccountsView({
  isAdmin,
  currentUserId,
  users,
  loading,
  auditLogPage,
  auditLogQuery,
  auditLogsLoading,
  onCreateUser,
  onEditUserQuota,
  onResetUserPassword,
  onApplyAuditLogQuery,
  onAuditLogPageChange,
  onRefreshAuditLogs,
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
    <section className="content-panel account-panel">
      <Tabs
        className="account-admin-tabs"
        items={[
          {
            key: 'users',
            label: '账号列表',
            children: (
              <UserManagementPanel
                currentUserId={currentUserId}
                users={users}
                loading={loading}
                onCreateUser={onCreateUser}
                onEditUserQuota={onEditUserQuota}
                onResetUserPassword={onResetUserPassword}
              />
            ),
          },
          {
            key: 'auditLogs',
            label: '审计日志',
            children: (
              <IdentityAuditLogPanel
                page={auditLogPage}
                query={auditLogQuery}
                loading={auditLogsLoading}
                onApplyQuery={onApplyAuditLogQuery}
                onPageChange={onAuditLogPageChange}
                onRefresh={onRefreshAuditLogs}
              />
            ),
          },
        ]}
      />
    </section>
  );
}
