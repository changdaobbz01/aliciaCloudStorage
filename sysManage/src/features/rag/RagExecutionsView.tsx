import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Form,
  InputNumber,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { Activity, CircleAlert, Clock3, Eye, RotateCcw, Search, ShieldCheck, Workflow } from 'lucide-react';
import { useEffect, useState } from 'react';
import { Icon } from '../../components/Icon';
import type {
  RagExecutionDetail,
  RagExecutionEvent,
  RagExecutionOverview,
  RagExecutionPage,
  RagExecutionQuery,
  RagExecutionStatus,
  RagExecutionStep,
  RagExecutionSummary,
} from '../../types';

type RagExecutionsViewProps = {
  isRagAdmin: boolean;
  overview: RagExecutionOverview | null;
  executionsPage: RagExecutionPage | null;
  query: RagExecutionQuery;
  loading: boolean;
  detail: RagExecutionDetail | null;
  detailOpen: boolean;
  detailLoading: boolean;
  onRefresh: () => void;
  onApplyQuery: (query: RagExecutionQuery) => void;
  onPageChange: (page: number, size: number) => void;
  onOpenDetail: (executionId: string) => void;
  onCloseDetail: () => void;
};

const statusOptions: Array<{ value: RagExecutionStatus; label: string }> = [
  { value: 'PENDING_CONFIRMATION', label: '等待确认' },
  { value: 'QUEUED', label: '已入队' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'RETRY_WAIT', label: '等待重试' },
  { value: 'WAITING_CLIENT_INPUT', label: '等待客户端输入' },
  { value: 'SUCCEEDED', label: '成功' },
  { value: 'PARTIALLY_SUCCEEDED', label: '部分成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'CANCELLED', label: '已取消' },
  { value: 'EXPIRED', label: '已过期' },
];

const statusLabels = Object.fromEntries(statusOptions.map((item) => [item.value, item.label])) as Record<RagExecutionStatus, string>;

function formatTimestamp(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

function statusColor(status: string) {
  if (status === 'SUCCEEDED') return 'green';
  if (status === 'FAILED' || status === 'PARTIALLY_SUCCEEDED') return 'red';
  if (status === 'RUNNING' || status === 'QUEUED') return 'blue';
  if (status === 'RETRY_WAIT' || status === 'WAITING_CLIENT_INPUT') return 'orange';
  if (status === 'CANCELLED' || status === 'EXPIRED') return 'default';
  return 'gold';
}

function ExecutionStatusTag({ status }: { status: RagExecutionStatus }) {
  return <Tag color={statusColor(status)}>{statusLabels[status] ?? status}</Tag>;
}

function MetricSummary({ overview, loading }: { overview: RagExecutionOverview | null; loading: boolean }) {
  const metrics = [
    { key: 'total', label: '任务总数', value: overview?.totalExecutions ?? 0, detail: '全部已登记任务', icon: Workflow },
    { key: 'active', label: '活动任务', value: overview?.activeExecutions ?? 0, detail: '确认、排队或执行中', icon: Activity },
    { key: 'attention', label: '需关注', value: overview?.attentionExecutions ?? 0, detail: '失败或部分成功', icon: CircleAlert, tone: 'warning' },
    { key: 'retried', label: '发生重试的步骤', value: overview?.retriedSteps ?? 0, detail: `当前等待重试 ${overview?.retryWaitExecutions ?? 0}`, icon: Clock3, tone: 'warning' },
  ];

  return (
    <Spin spinning={loading}>
      <div className="operations-summary-grid">
        {metrics.map((metric) => (
          <div key={metric.key} className={`management-summary-card operations-summary-card operations-summary-card-${metric.tone ?? 'default'}`}>
            <span className="operations-summary-icon"><Icon icon={metric.icon} size={20} /></span>
            <div className="operations-summary-copy">
              <div className="management-summary-label">{metric.label}</div>
              <div className="management-summary-value">{metric.value}</div>
              <Typography.Text className="table-secondary-text">{metric.detail}</Typography.Text>
            </div>
          </div>
        ))}
      </div>
      <div className="operations-overview-meta">
        <span>概览生成时间：{formatTimestamp(overview?.generatedAt)}</span>
        <span>该页面只读，不提供确认、取消、重试或删除操作。</span>
      </div>
    </Spin>
  );
}

function DetailDrawer({ detail, open, loading, onClose }: { detail: RagExecutionDetail | null; open: boolean; loading: boolean; onClose: () => void }) {
  const stepColumns: ColumnsType<RagExecutionStep> = [
    { title: '#', dataIndex: 'stepIndex', width: 56 },
    { title: '动作', dataIndex: 'actionType', width: 170 },
    { title: '状态', dataIndex: 'status', width: 130, render: (value: string) => <Tag color={statusColor(value)}>{value}</Tag> },
    { title: '尝试', dataIndex: 'attempts', width: 72 },
    { title: '错误码', dataIndex: 'errorCode', render: (value: string | null) => value ?? '-' },
    { title: '开始时间', dataIndex: 'startedAt', width: 180, render: formatTimestamp },
    { title: '结束时间', dataIndex: 'finishedAt', width: 180, render: formatTimestamp },
  ];
  const eventColumns: ColumnsType<RagExecutionEvent> = [
    { title: '序号', dataIndex: 'sequence', width: 72 },
    { title: '事件', dataIndex: 'type', width: 240 },
    { title: '时间', dataIndex: 'createdAt', width: 190, render: formatTimestamp },
  ];

  return (
    <Drawer title="RAG 执行任务详情" width={920} open={open} onClose={onClose} destroyOnHidden>
      <Spin spinning={loading}>
        {detail ? (
          <Space direction="vertical" size="large" className="rag-detail-stack">
            <Alert type="info" showIcon message="脱敏只读视图" description="任务详情不包含原始对话、文件名 payload、计划哈希或模型响应。" />
            <Descriptions bordered size="small" column={2}>
              <Descriptions.Item label="任务 ID" span={2}>{detail.execution.executionId}</Descriptions.Item>
              <Descriptions.Item label="用户 ID">{detail.execution.ownerUserId}</Descriptions.Item>
              <Descriptions.Item label="状态"><ExecutionStatusTag status={detail.execution.status} /></Descriptions.Item>
              <Descriptions.Item label="动作">{detail.execution.actionTypes.join(' → ') || '-'}</Descriptions.Item>
              <Descriptions.Item label="最大尝试次数">{detail.execution.maximumAttempts}</Descriptions.Item>
              <Descriptions.Item label="结果码">{detail.execution.resultCode ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="错误码">{detail.execution.errorCode ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatTimestamp(detail.execution.createdAt)}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{formatTimestamp(detail.execution.updatedAt)}</Descriptions.Item>
            </Descriptions>
            <div>
              <Typography.Title level={5}>执行步骤</Typography.Title>
              <Table rowKey="stepId" size="small" columns={stepColumns} dataSource={detail.steps} pagination={false} scroll={{ x: 980 }} />
            </div>
            <div>
              <Typography.Title level={5}>公开事件序列</Typography.Title>
              <Table rowKey="sequence" size="small" columns={eventColumns} dataSource={detail.events} pagination={false} />
            </div>
          </Space>
        ) : null}
      </Spin>
    </Drawer>
  );
}

export function RagExecutionsView({
  isRagAdmin,
  overview,
  executionsPage,
  query,
  loading,
  detail,
  detailOpen,
  detailLoading,
  onRefresh,
  onApplyQuery,
  onPageChange,
  onOpenDetail,
  onCloseDetail,
}: RagExecutionsViewProps) {
  const [draft, setDraft] = useState<RagExecutionQuery>(query);

  useEffect(() => setDraft(query), [query]);

  if (!isRagAdmin) {
    return <section className="content-panel"><Alert type="warning" showIcon message="当前账号没有 RAG 任务查看权限" description="该视图要求显式 RAG_ADMIN 角色。" /></section>;
  }

  const columns: ColumnsType<RagExecutionSummary> = [
    {
      title: '任务',
      key: 'execution',
      fixed: 'left',
      width: 250,
      render: (_, execution) => (
        <div className="user-cell-copy">
          <Typography.Text copyable={{ text: execution.executionId }} ellipsis={{ tooltip: execution.executionId }} className="table-primary-text">
            {execution.executionId}
          </Typography.Text>
          <Typography.Text className="table-secondary-text">用户 #{execution.ownerUserId}</Typography.Text>
        </div>
      ),
    },
    { title: '状态', dataIndex: 'status', width: 130, render: (status: RagExecutionStatus) => <ExecutionStatusTag status={status} /> },
    { title: '动作', dataIndex: 'actionTypes', width: 190, render: (actions: string[]) => actions.join(' → ') || '-' },
    { title: '步骤 / 尝试', key: 'attempts', width: 130, render: (_, execution) => `${execution.stepCount} / ${execution.totalAttempts}` },
    { title: '风险', dataIndex: 'risk', width: 100, render: (risk: string) => <Tag>{risk}</Tag> },
    { title: '结果 / 错误', key: 'result', width: 220, render: (_, execution) => execution.errorCode ?? execution.resultCode ?? '-' },
    { title: '创建时间', dataIndex: 'createdAt', width: 190, render: formatTimestamp },
    { title: '更新时间', dataIndex: 'updatedAt', width: 190, render: formatTimestamp },
    {
      title: '查看',
      key: 'detail',
      fixed: 'right',
      width: 100,
      render: (_, execution) => <Button type="link" icon={<Icon icon={Eye} />} onClick={() => onOpenDetail(execution.executionId)}>详情</Button>,
    },
  ];
  const pagination: TablePaginationConfig = {
    current: executionsPage?.page ?? query.page ?? 1,
    pageSize: executionsPage?.size ?? query.size ?? 20,
    total: executionsPage?.totalItems ?? 0,
    showSizeChanger: true,
    pageSizeOptions: [10, 20, 50, 100],
    onChange: onPageChange,
  };

  return (
    <section className="content-panel operations-panel">
      <div className="panel-header panel-header-spacious">
        <div className="panel-title-copy">
          <Typography.Title level={4}>RAG 执行任务</Typography.Title>
          <Typography.Paragraph className="panel-subtitle">只读查看任务状态、步骤尝试和公开事件，辅助定位卡住、重试和失败任务。</Typography.Paragraph>
        </div>
        <Tag color="green" icon={<Icon icon={ShieldCheck} />}>RAG_ADMIN · 只读</Tag>
      </div>
      <MetricSummary overview={overview} loading={loading} />
      <Form layout="vertical" className="audit-filter-bar operations-filter-bar-compact" onFinish={() => onApplyQuery({ ...draft, page: 1 })}>
        <Form.Item label="任务状态">
          <Select allowClear placeholder="全部状态" value={draft.status ?? undefined} options={statusOptions} onChange={(value) => setDraft((current) => ({ ...current, status: value ?? null }))} />
        </Form.Item>
        <Form.Item label="用户 ID">
          <InputNumber min={1} precision={0} placeholder="全部用户" value={draft.ownerUserId ?? undefined} onChange={(value) => setDraft((current) => ({ ...current, ownerUserId: value ?? null }))} />
        </Form.Item>
        <Form.Item label=" ">
          <Space wrap>
            <Button type="primary" htmlType="submit" icon={<Icon icon={Search} />} loading={loading}>查询</Button>
            <Button icon={<Icon icon={RotateCcw} />} disabled={loading} onClick={() => onApplyQuery({ page: 1, size: query.size ?? 20 })}>重置</Button>
            <Button icon={<Icon icon={Activity} />} disabled={loading} onClick={onRefresh}>刷新</Button>
          </Space>
        </Form.Item>
      </Form>
      <div className="audit-filter-summary"><Typography.Text className="audit-result-count">共 <strong>{executionsPage?.totalItems ?? 0}</strong> 个任务</Typography.Text></div>
      <Table rowKey="executionId" className="management-table" loading={loading} columns={columns} dataSource={executionsPage?.items ?? []} pagination={pagination} scroll={{ x: 1510 }} locale={{ emptyText: '暂无符合条件的 RAG 执行任务。' }} />
      <DetailDrawer detail={detail} open={detailOpen} loading={detailLoading} onClose={onCloseDetail} />
    </section>
  );
}

export default RagExecutionsView;
