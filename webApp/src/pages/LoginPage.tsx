import { App as AntApp, Button, Card, Form, Input, Space, Typography } from 'antd';
import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useSession } from '../context/session-context';
import { login } from '../lib/api';
import type { LoginPayload } from '../types';

/**
 * 渲染登录页面，并在登录成功后跳转到用户原本想访问的页面。
 */
export function LoginPage() {
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const { authToken, setCurrentSession } = useSession();
  const [form] = Form.useForm<LoginPayload>();

  useEffect(() => {
    if (authToken) {
      void navigate('/', { replace: true });
    }
  }, [authToken, navigate]);

  /**
   * 提交登录表单，调用后端接口建立新的登录态。
   */
  async function handleFinish(values: LoginPayload) {
    try {
      const session = await login(values);
      setCurrentSession(session);
      message.success('登录成功。');

      const destination =
        typeof location.state === 'object' &&
        location.state &&
        'from' in location.state &&
        typeof location.state.from === 'string'
          ? location.state.from
          : '/';

      void navigate(destination, { replace: true });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败。');
    }
  }

  return (
    <div className="login-shell">
      <div className="login-copy">
        <Typography.Title>Alicia 云盘</Typography.Title>
        <Typography.Paragraph>
          当前版本由管理员统一分配账号。普通用户可登录后修改个人资料与密码，密码修改必须先校验旧密码。
        </Typography.Paragraph>

        <div className="login-credentials">
          <Card size="small">
            <Typography.Text strong>管理员演示账号</Typography.Text>
            <div className="muted-text">手机号：13800000000</div>
            <div className="muted-text">密码：Admin@123</div>
          </Card>
          <Card size="small">
            <Typography.Text strong>普通用户演示账号</Typography.Text>
            <div className="muted-text">手机号：13900000000</div>
            <div className="muted-text">密码：User@123</div>
          </Card>
        </div>
      </div>

      <Card className="login-card">
        <Typography.Title level={3}>账号登录</Typography.Title>
        <Typography.Paragraph type="secondary">请使用手机号和密码登录云盘。</Typography.Paragraph>

        <Form<LoginPayload>
          form={form}
          layout="vertical"
          onFinish={handleFinish}
          initialValues={{ phoneNumber: '13800000000', password: 'Admin@123' }}
        >
          <Form.Item
            name="phoneNumber"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号。' },
              { pattern: /^1\d{10}$/, message: '请输入 11 位手机号。' },
            ]}
          >
            <Input placeholder="13800000000" autoComplete="username" />
          </Form.Item>

          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码。' }]}
          >
            <Input.Password placeholder="请输入密码" autoComplete="current-password" />
          </Form.Item>

          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Button type="primary" htmlType="button" block onClick={() => void form.submit()}>
              登录
            </Button>
            <Typography.Text type="secondary">
              当前系统不向普通用户开放自助注册入口。
            </Typography.Text>
          </Space>
        </Form>
      </Card>
    </div>
  );
}
