import { ReloadOutlined, SearchOutlined, StopOutlined } from '@ant-design/icons';
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

const { RangePicker } = DatePicker;

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
  { value: 'PROFILE_UPDATE', label: '资料更新' },
  { value: 'PASSWORD_CHANGE', label: '修改密码' },
  { value: 'ADMIN_USER_CREATE', label: '管理员建号' },
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

function formatTimestamp(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function formatNullable(value: string | number | null) {
  return value === null ? '-' : String(value);
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
  const pageSize = page?.size ?? query.size ?? 20;
  const totalItems = page?.totalItems ?? 0;

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
      render: (value: string | null) => (
        <Typography.Text ellipsis={{ tooltip: value ?? undefined }} className="table-secondary-text">
          {formatNullable(value)}
        </Typography.Text>
      ),
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

  function handleSearch() {
    onApplyQuery({
      ...draftQuery,
      page: 1,
      size: pageSize,
    });
  }

  function handleReset() {
    const nextQuery = { page: 1, size: pageSize };
    setDraftQuery(nextQuery);
    onApplyQuery(nextQuery);
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
          <Button icon={<ReloadOutlined />} onClick={onRefresh} loading={loading}>
            刷新
          </Button>
        </div>
      </div>

      <Form className="audit-filter-bar" layout="vertical">
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
            onChange={(event) => updateDraft({ identifier: event.target.value })}
            onPressEnter={handleSearch}
          />
        </Form.Item>
        <Form.Item label="时间范围">
          <RangePicker value={toDateRange(draftQuery)} onChange={handleDateRangeChange} />
        </Form.Item>
        <Form.Item label=" ">
          <Space size="small" wrap>
            <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
              查询
            </Button>
            <Button icon={<StopOutlined />} onClick={handleReset}>
              重置
            </Button>
          </Space>
        </Form.Item>
      </Form>

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
