import { App as AntApp, Button, Card, Form, Input, Typography } from 'antd';
import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useSession } from '../context/session-context';
import { login } from '../lib/api';
import type { LoginPayload } from '../types';

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
      <Card className="login-card" bordered={false}>
        <div className="login-brand">
          <span className="login-badge">Alicia Cloud</span>
          <Typography.Title level={2}>欢迎登录云盘</Typography.Title>
          <Typography.Paragraph type="secondary" className="login-subtitle">
            使用管理员分配的手机号和密码进入你的个人空间。
          </Typography.Paragraph>
        </div>

        <Form<LoginPayload> form={form} layout="vertical" onFinish={handleFinish}>
          <Form.Item
            name="phoneNumber"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号。' },
              { pattern: /^1\d{10}$/, message: '请输入 11 位手机号。' },
            ]}
          >
            <Input placeholder="请输入手机号" autoComplete="username" />
          </Form.Item>

          <Form.Item
            name="password"
            label="密码"
            rules={[{ required: true, message: '请输入密码。' }]}
          >
            <Input.Password placeholder="请输入密码" autoComplete="current-password" />
          </Form.Item>

          <Button type="primary" htmlType="button" block className="login-submit" onClick={() => void form.submit()}>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
