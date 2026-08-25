import type { LucideIcon } from 'lucide-react';
import { Clock, LogIn, RefreshCw, RotateCcw, Search, ShieldCheck, ShieldX, UserCog } from 'lucide-react';
import { Button, DatePicker, Form, Input, InputNumber, Select, Space, Table, Tag, Typography } from 'antd';
import type { TableProps } from 'antd';
import type { Dayjs } from 'dayjs';
import dayjs from 'dayjs';
import { useEffect, useState } from 'react';
import type {
  IdentityAuditEventType,
  IdentityAuditLog,
  IdentityAuditLogPage,
  IdentityAuditLogQuery,
  IdentityAuditOutcome,
} from '../../types';
import { Icon } from '../../components/Icon';

const { RangePicker } = DatePicker;
const DEFAULT_PAGE_SIZE = 20;

type IdentityAuditLogPanelProps = {
  page: IdentityAuditLogPage | null;
  query: IdentityAuditLogQuery;
  loading: boolean;
  onApplyQuery: (query: IdentityAuditLogQuery) => void;
  onPageChange: (page: number, size: number) => void;
  onRefresh: () => void;
};

const eventOptions: Array<{ value: IdentityAuditEventType; label: string }> = [
  { value: 'LOGIN', label: '登录' },
  { value: 'TOKEN_REFRESH', label: 'Token 续签' },
  { value: 'LOGOUT', label: '退出登录' },
  { value: 'SESSION_REVOKE', label: '会话撤销' },
  { value: 'PROFILE_UPDATE', label: '资料更新' },
  { value: 'PASSWORD_CHANGE', label: '修改密码' },
  { value: 'ADMIN_USER_CREATE', label: '管理员建号' },
  { value: 'ADMIN_APP_ROLE_UPDATE', label: '应用角色调整' },
  { value: 'ADMIN_PASSWORD_RESET', label: '管理员重置密码' },
  { value: 'EMAIL_REGISTRATION_CODE_REQUEST', label: '注册验证码' },
  { value: 'EMAIL_REGISTRATION_VERIFY', label: '邮箱注册验证' },
];

const eventLabels = eventOptions.reduce<Record<IdentityAuditEventType, string>>(
  (labels, option) => ({ ...labels, [option.value]: option.label }),
  {} as Record<IdentityAuditEventType, string>,
);

const outcomeOptions: Array<{ value: IdentityAuditOutcome; label: string }> = [
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILURE', label: '失败' },
];

type DateRangeValue = [Dayjs | null, Dayjs | null] | null;
type QuickFilter = {
  key: string;
  label: string;
  icon: LucideIcon;
  createPatch: () => Partial<IdentityAuditLogQuery>;
};
type FilterToken = {
  key: string;
  label: string;
  value: string;
  clearPatch: Partial<IdentityAuditLogQuery>;
  danger?: boolean;
};

const detailLabels: Record<string, string> = {
  already_registered: '邮箱已注册，未重复发送',
  all_devices: '退出全部设备',
  CLOUD_ADMIN: '授予云盘管理员',
  CLOUD_USER: '调整为普通云盘用户',
  current_session: '退出当前会话',
  RAG_ADMIN: '授予 RAG 管理员',
  RAG_USER: '调整为普通 RAG 用户',
  sent: '验证码已发送',
  token_without_session: 'Token 缺少会话信息，已提升版本',
};

const quickFilters: QuickFilter[] = [
  {
    key: 'recent24h',
    label: '最近 24 小时',
    icon: Clock,
    createPatch: () => ({
      createdFrom: dayjs().subtract(24, 'hour').format('YYYY-MM-DDTHH:mm:ss'),
      createdTo: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
    }),
  },
  {
    key: 'failures',
    label: '失败事件',
    icon: ShieldX,
    createPatch: () => ({ eventType: null, outcome: 'FAILURE' }),
  },
  {
    key: 'loginFailures',
    label: '登录失败',
    icon: LogIn,
    createPatch: () => ({ eventType: 'LOGIN', outcome: 'FAILURE' }),
  },
  {
    key: 'sessionRevoke',
    label: '会话撤销',
    icon: UserCog,
    createPatch: () => ({ eventType: 'SESSION_REVOKE', outcome: null }),
  },
  {
    key: 'appRole',
    label: '权限调整',
    icon: ShieldCheck,
    createPatch: () => ({ eventType: 'ADMIN_APP_ROLE_UPDATE', outcome: null }),
  },
];

function formatTimestamp(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function formatNullable(value: string | number | null) {
  return value === null ? '-' : String(value);
}

function formatDetail(value: string | null) {
  if (!value) {
    return '-';
  }

  if (value.startsWith('session_revoke:')) {
    const sessionId = value.replace('session_revoke:', '').trim();
    return sessionId ? `撤销会话 #${sessionId}` : '撤销会话';
  }

  return detailLabels[value] ?? value;
}

function formatQueryDate(value: string | null | undefined) {
  if (!value) {
    return null;
  }

  const parsed = dayjs(value);
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm') : value;
}

function formatDateRange(query: IdentityAuditLogQuery) {
  const from = formatQueryDate(query.createdFrom);
  const to = formatQueryDate(query.createdTo);

  if (from && to) {
    return `${from} - ${to}`;
  }

  if (from) {
    return `${from} 之后`;
  }

  if (to) {
    return `${to} 之前`;
  }

  return null;
}

function toDateRange(query: IdentityAuditLogQuery): DateRangeValue {
  if (!query.createdFrom && !query.createdTo) {
    return null;
  }

  return [
    query.createdFrom ? dayjs(query.createdFrom) : null,
    query.createdTo ? dayjs(query.createdTo) : null,
  ];
}

function toQueryDate(value: Dayjs | null | undefined, endOfDay = false) {
  if (!value) {
    return null;
  }

  return (endOfDay ? value.endOf('day') : value.startOf('day')).format('YYYY-MM-DDTHH:mm:ss');
}

function normalizeQuery(query: IdentityAuditLogQuery, fallbackSize = DEFAULT_PAGE_SIZE): IdentityAuditLogQuery {
  const identifier = query.identifier?.trim();

  return {
    eventType: query.eventType ?? null,
    outcome: query.outcome ?? null,
    actorUserId: query.actorUserId ?? null,
    targetUserId: query.targetUserId ?? null,
    identifier: identifier || null,
    createdFrom: query.createdFrom ?? null,
    createdTo: query.createdTo ?? null,
    page: query.page ?? 1,
    size: query.size ?? fallbackSize,
  };
}

function buildFilterTokens(query: IdentityAuditLogQuery): FilterToken[] {
  const tokens: FilterToken[] = [];

  if (query.eventType) {
    tokens.push({
      key: 'eventType',
      label: '事件',
      value: eventLabels[query.eventType] ?? query.eventType,
      clearPatch: { eventType: null },
    });
  }

  if (query.outcome) {
    tokens.push({
      key: 'outcome',
      label: '结果',
      value: query.outcome === 'SUCCESS' ? '成功' : '失败',
      clearPatch: { outcome: null },
      danger: query.outcome === 'FAILURE',
    });
  }

  if (query.actorUserId !== undefined && query.actorUserId !== null) {
    tokens.push({
      key: 'actorUserId',
      label: '操作者',
      value: String(query.actorUserId),
      clearPatch: { actorUserId: null },
    });
  }

  if (query.targetUserId !== undefined && query.targetUserId !== null) {
    tokens.push({
      key: 'targetUserId',
      label: '目标用户',
      value: String(query.targetUserId),
      clearPatch: { targetUserId: null },
    });
  }

  if (query.identifier?.trim()) {
    tokens.push({
      key: 'identifier',
      label: '标识符',
      value: query.identifier.trim(),
      clearPatch: { identifier: null },
    });
  }

  const dateRange = formatDateRange(query);
  if (dateRange) {
    tokens.push({
      key: 'createdAt',
      label: '时间',
      value: dateRange,
      clearPatch: { createdFrom: null, createdTo: null },
    });
  }

  return tokens;
}

export function IdentityAuditLogPanel({
  page,
  query,
  loading,
  onApplyQuery,
  onPageChange,
  onRefresh,
}: IdentityAuditLogPanelProps) {
  const [draftQuery, setDraftQuery] = useState<IdentityAuditLogQuery>(query);

  useEffect(() => {
    setDraftQuery(query);
  }, [query]);

  const auditLogs = page?.items ?? [];
  const currentPage = page?.page ?? query.page ?? 1;
  const pageSize = page?.size ?? query.size ?? DEFAULT_PAGE_SIZE;
  const totalItems = page?.totalItems ?? 0;
  const rangeStart = totalItems === 0 ? 0 : (currentPage - 1) * pageSize + 1;
  const rangeEnd = totalItems === 0 ? 0 : Math.min(totalItems, currentPage * pageSize);
  const activeFilters = buildFilterTokens(query);

  const columns: TableProps<IdentityAuditLog>['columns'] = [
    {
      title: '事件',
      dataIndex: 'eventType',
      key: 'eventType',
      width: 180,
      render: (eventType: IdentityAuditEventType) => eventLabels[eventType] ?? eventType,
    },
    {
      title: '结果',
      dataIndex: 'outcome',
      key: 'outcome',
      width: 100,
      render: (outcome: IdentityAuditOutcome) => (
        <Tag color={outcome === 'SUCCESS' ? 'green' : 'red'}>{outcome === 'SUCCESS' ? '成功' : '失败'}</Tag>
      ),
    },
    {
      title: '操作者',
      dataIndex: 'actorUserId',
      key: 'actorUserId',
      width: 110,
      render: (value: number | null) => formatNullable(value),
    },
    {
      title: '目标用户',
      dataIndex: 'targetUserId',
      key: 'targetUserId',
      width: 110,
      render: (value: number | null) => formatNullable(value),
    },
    {
      title: '标识符',
      dataIndex: 'identifier',
      key: 'identifier',
      width: 210,
      render: (value: string | null) => (
        <Typography.Text ellipsis={{ tooltip: value ?? undefined }} className="table-primary-text">
          {formatNullable(value)}
        </Typography.Text>
      ),
    },
    {
      title: '详情',
      dataIndex: 'detail',
      key: 'detail',
      width: 280,
      render: (value: string | null) => {
        const detail = formatDetail(value);
        return (
          <Typography.Text ellipsis={{ tooltip: detail === '-' ? undefined : detail }} className="table-secondary-text">
            {detail}
          </Typography.Text>
        );
      },
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 190,
      render: (value: string) => formatTimestamp(value),
    },
  ];

  function updateDraft(next: Partial<IdentityAuditLogQuery>) {
    setDraftQuery((current) => ({ ...current, ...next }));
  }

  function handleDateRangeChange(value: DateRangeValue) {
    updateDraft({
      createdFrom: toQueryDate(value?.[0]),
      createdTo: toQueryDate(value?.[1], true),
    });
  }

  function applyQuery(nextQuery: IdentityAuditLogQuery) {
    const normalizedQuery = normalizeQuery(nextQuery, pageSize);
    setDraftQuery(normalizedQuery);
    onApplyQuery(normalizedQuery);
  }

  function handleSearch() {
    applyQuery({
      ...draftQuery,
      page: 1,
      size: pageSize,
    });
  }

  function handleReset() {
    applyQuery({ page: 1, size: pageSize });
  }

  function applyQuickFilter(filter: QuickFilter) {
    applyQuery({
      ...draftQuery,
      ...filter.createPatch(),
      page: 1,
      size: pageSize,
    });
  }

  function clearFilter(token: FilterToken) {
    applyQuery({
      ...query,
      ...token.clearPatch,
      page: 1,
      size: pageSize,
    });
  }

  return (
    <>
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>身份审计</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">
            查看登录、续签、注销、资料修改、密码变更和管理员操作的身份侧审计记录。
          </Typography.Paragraph>
        </div>
        <div className="panel-actions">
          <Button icon={<Icon icon={RefreshCw} />} onClick={onRefresh} loading={loading}>
            刷新
          </Button>
        </div>
      </div>

      <div className="audit-quick-filters" aria-label="常用审计筛选">
        <span className="audit-quick-label">快捷筛选</span>
        {quickFilters.map((filter) => (
          <Button
            key={filter.key}
            icon={<Icon icon={filter.icon} />}
            onClick={() => applyQuickFilter(filter)}
          >
            {filter.label}
          </Button>
        ))}
      </div>

      <Form className="audit-filter-bar" layout="vertical" onFinish={handleSearch}>
        <Form.Item label="事件类型">
          <Select
            allowClear
            value={draftQuery.eventType ?? undefined}
            options={eventOptions}
            placeholder="全部事件"
            onChange={(value) => updateDraft({ eventType: value ?? null })}
          />
        </Form.Item>
        <Form.Item label="结果">
          <Select
            allowClear
            value={draftQuery.outcome ?? undefined}
            options={outcomeOptions}
            placeholder="全部结果"
            onChange={(value) => updateDraft({ outcome: value ?? null })}
          />
        </Form.Item>
        <Form.Item label="操作者 ID">
          <InputNumber
            min={1}
            precision={0}
            value={draftQuery.actorUserId ?? null}
            placeholder="不限"
            onChange={(value) => updateDraft({ actorUserId: value ?? null })}
          />
        </Form.Item>
        <Form.Item label="目标用户 ID">
          <InputNumber
            min={1}
            precision={0}
            value={draftQuery.targetUserId ?? null}
            placeholder="不限"
            onChange={(value) => updateDraft({ targetUserId: value ?? null })}
          />
        </Form.Item>
        <Form.Item label="标识符">
          <Input
            allowClear
            value={draftQuery.identifier ?? ''}
            placeholder="手机号或邮箱"
            onChange={(event) => updateDraft({ identifier: event.target.value.trim() || null })}
            onPressEnter={handleSearch}
          />
        </Form.Item>
        <Form.Item label="时间范围">
          <RangePicker value={toDateRange(draftQuery)} onChange={handleDateRangeChange} />
        </Form.Item>
        <Form.Item label=" ">
          <Space size="small" wrap>
            <Button type="primary" htmlType="submit" icon={<Icon icon={Search} />}>
              查询
            </Button>
            <Button icon={<Icon icon={RotateCcw} />} onClick={handleReset}>
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>

      <div className="audit-filter-summary">
        <div className="audit-filter-token-list" aria-label="当前审计筛选条件">
          {activeFilters.length > 0 ? (
            activeFilters.map((token) => (
              <Tag
                key={token.key}
                closable
                className={token.danger ? 'audit-filter-token audit-filter-token-danger' : 'audit-filter-token'}
                onClose={(event) => {
                  event.preventDefault();
                  clearFilter(token);
                }}
              >
                <span className="audit-filter-token-label">{token.label}</span>
                {token.value}
              </Tag>
            ))
          ) : (
            <span className="audit-filter-empty">当前显示全部审计记录</span>
          )}
        </div>
        <Typography.Text className="audit-result-count">
          共 <strong>{totalItems}</strong> 条，当前显示 {rangeStart}-{rangeEnd}
        </Typography.Text>
      </div>

      <Table
        rowKey="id"
        className="management-table"
        loading={loading}
        columns={columns}
        dataSource={auditLogs}
        pagination={{
          current: currentPage,
          pageSize,
          total: totalItems,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          position: ['bottomRight'],
          onChange: onPageChange,
        }}
        scroll={{ x: 1180 }}
        locale={{ emptyText: '暂无审计记录。' }}
      />
    </>
  );
}
