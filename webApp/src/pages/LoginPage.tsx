import { App as AntApp, Button, Card, Form, Input, QRCode, Segmented, Typography } from 'antd';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { RegulatoryFooter } from '../components/RegulatoryFooter';
import { useSession } from '../context/session-context';
import { fetchPublicAppPackage, login, requestEmailRegistrationCode, verifyEmailRegistration } from '../lib/api';
import type { AppPackageInfo, LoginPayload, VerifyEmailRegistrationPayload } from '../types';

type AuthMode = 'login' | 'register';

type RegisterFormValues = VerifyEmailRegistrationPayload & {
  confirmPassword: string;
};

function trimFormString(value: unknown) {
  return typeof value === 'string' ? value.trim() : '';
}

function firstFormErrorMessage(errorInfo: unknown) {
  const fields = (errorInfo as { errorFields?: Array<{ errors?: unknown[] }> }).errorFields ?? [];
  for (const field of fields) {
    const message = field.errors?.find((error) => typeof error === 'string');
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
  }

  return null;
}

function resolveDownloadUrl(downloadPath: string) {
  if (/^https?:\/\//i.test(downloadPath)) {
    return downloadPath;
  }

  if (typeof window === 'undefined') {
    return downloadPath;
  }

  return new URL(downloadPath, window.location.origin).toString();
}

export function LoginPage() {
  const { message } = AntApp.useApp();
  const navigate = useNavigate();
  const location = useLocation();
  const { authToken, setCurrentSession } = useSession();
  const [loginForm] = Form.useForm<LoginPayload>();
  const [registerForm] = Form.useForm<RegisterFormValues>();
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [appPackageInfo, setAppPackageInfo] = useState<AppPackageInfo | null>(null);
  const [sendingCode, setSendingCode] = useState(false);
  const [registering, setRegistering] = useState(false);
  const [codeCooldown, setCodeCooldown] = useState(0);
  const [registrationEmail, setRegistrationEmail] = useState('');

  function resolveLoginDestination() {
    const destination = typeof location.state === 'object' &&
      location.state &&
      'from' in location.state &&
      typeof location.state.from === 'string'
      ? location.state.from
      : '/';

    if (!destination.startsWith('/') || destination.startsWith('//')) {
      return '/';
    }

    return destination;
  }

  useEffect(() => {
    let cancelled = false;

    async function loadPublicAppPackage() {
      try {
        const nextPackageInfo = await fetchPublicAppPackage();
        if (!cancelled) {
          setAppPackageInfo(nextPackageInfo);
        }
      } catch {
        if (!cancelled) {
          setAppPackageInfo(null);
        }
      }
    }

    void loadPublicAppPackage();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (authToken) {
      void navigate(resolveLoginDestination(), { replace: true });
    }
  }, [authToken, navigate]);

  useEffect(() => {
    if (codeCooldown <= 0) {
      return;
    }

    const timer = window.setTimeout(() => {
      setCodeCooldown((current) => Math.max(0, current - 1));
    }, 1000);

    return () => window.clearTimeout(timer);
  }, [codeCooldown]);

  async function handleFinish(values: LoginPayload) {
    try {
      const session = await login({
        identifier: trimFormString(values.identifier),
        password: values.password,
      });
      setCurrentSession(session);
      message.success('登录成功。');

      void navigate(resolveLoginDestination(), { replace: true });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败。');
    }
  }

  async function handleSendRegistrationCode() {
    try {
      const email = resolveRegistrationEmail();
      registerForm.setFieldValue('email', email);

      if (!email) {
        registerForm.setFields([{ name: 'email', errors: ['请输入邮箱。'] }]);
        message.error('请输入邮箱。');
        return;
      }

      await registerForm.validateFields([['email']]);
      setSendingCode(true);
      await requestEmailRegistrationCode({ email });
      setCodeCooldown(60);
      message.success('如果邮箱可用，验证码会发送到该邮箱。');
    } catch (error) {
      if (error instanceof Error) {
        message.error(error.message);
      }
    } finally {
      setSendingCode(false);
    }
  }

  async function handleRegister(values: RegisterFormValues) {
    try {
      setRegistering(true);
      const email = trimFormString(values.email) || resolveRegistrationEmail();
      if (!email) {
        registerForm.setFields([{ name: 'email', errors: ['请输入邮箱。'] }]);
        message.error('请输入邮箱。');
        return;
      }

      const session = await verifyEmailRegistration({
        email,
        code: trimFormString(values.code),
        nickname: trimFormString(values.nickname),
        password: values.password,
      });
      setCurrentSession(session);
      message.success('注册成功，欢迎使用 Alicia 云盘。');
      void navigate(resolveLoginDestination(), { replace: true });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '注册失败。');
    } finally {
      setRegistering(false);
    }
  }

  function handleLoginFailed(errorInfo: unknown) {
    message.error(firstFormErrorMessage(errorInfo) ?? '请填写账号和密码。');
  }

  function handleRegisterFailed(errorInfo: unknown) {
    message.error(firstFormErrorMessage(errorInfo) ?? '请完整填写注册信息。');
  }

  function resolveRegistrationEmail() {
    const formEmail = trimFormString(registerForm.getFieldValue('email'));
    if (formEmail) {
      return formEmail;
    }

    const stateEmail = trimFormString(registrationEmail);
    if (stateEmail) {
      return stateEmail;
    }

    if (typeof document === 'undefined') {
      return '';
    }

    const input = document.getElementById('registration-email-input');
    return input instanceof HTMLInputElement ? trimFormString(input.value) : '';
  }

  const appDownloadAvailable = appPackageInfo?.available ?? false;
  const appDownloadUrl = appDownloadAvailable
    ? resolveDownloadUrl(appPackageInfo?.downloadUrl ?? '/api/app-package/download/current')
    : null;

  return (
    <div className="login-shell">
      <Card className="login-card" bordered={false}>
        <div className="login-brand">
          <span className="login-badge">Alicia Cloud</span>
          <Typography.Title level={2}>{authMode === 'login' ? '欢迎登录云盘' : '创建 Alicia 账号'}</Typography.Title>
          <Typography.Paragraph type="secondary" className="login-subtitle">
            {authMode === 'login'
              ? '使用手机号或已验证邮箱进入你的个人空间。'
              : '通过邮箱验证码创建你的个人云盘空间。'}
          </Typography.Paragraph>
        </div>

        <div className="login-mode-switch">
          <Segmented<AuthMode>
            block
            value={authMode}
            onChange={setAuthMode}
            options={[
              { label: '登录', value: 'login' },
              { label: '注册', value: 'register' },
            ]}
          />
        </div>

        {authMode === 'login' ? (
          <Form<LoginPayload>
            form={loginForm}
            layout="vertical"
            onFinish={handleFinish}
            onFinishFailed={handleLoginFailed}
          >
            <Form.Item
              name="identifier"
              label="手机号或邮箱"
              rules={[{ required: true, message: '请输入手机号或邮箱。' }]}
            >
              <Input placeholder="请输入手机号或邮箱" autoComplete="username" />
            </Form.Item>

            <Form.Item
              name="password"
              label="密码"
              rules={[{ required: true, message: '请输入密码。' }]}
            >
              <Input.Password placeholder="请输入密码" autoComplete="current-password" />
            </Form.Item>

            <Button type="primary" htmlType="submit" block className="login-submit">
              登录
            </Button>
          </Form>
        ) : (
          <Form<RegisterFormValues>
            form={registerForm}
            layout="vertical"
            onFinish={handleRegister}
            onFinishFailed={handleRegisterFailed}
          >
            <Form.Item
              name="email"
              label="邮箱"
              rules={[
                { required: true, message: '请输入邮箱。' },
                { type: 'email', message: '请输入有效邮箱地址。' },
              ]}
            >
              <Input
                id="registration-email-input"
                placeholder="请输入邮箱"
                autoComplete="email"
                onChange={(event) => setRegistrationEmail(event.target.value)}
              />
            </Form.Item>

            <Form.Item label="验证码" required className="login-code-form-item">
              <div className="login-code-row">
                <Form.Item
                  name="code"
                  noStyle
                  rules={[
                    { required: true, message: '请输入验证码。' },
                    { pattern: /^\d{6}$/, message: '验证码应为 6 位数字。' },
                  ]}
                >
                  <Input placeholder="6 位验证码" inputMode="numeric" maxLength={6} />
                </Form.Item>
                <Button
                  type="default"
                  htmlType="button"
                  className="login-code-button"
                  loading={sendingCode}
                  disabled={codeCooldown > 0}
                  onClick={() => void handleSendRegistrationCode()}
                >
                  {codeCooldown > 0 ? `${codeCooldown}s` : '发送验证码'}
                </Button>
              </div>
            </Form.Item>

            <Form.Item
              name="nickname"
              label="昵称"
              rules={[{ required: true, message: '请输入昵称。' }]}
            >
              <Input placeholder="请输入昵称" autoComplete="nickname" />
            </Form.Item>

            <Form.Item
              name="password"
              label="密码"
              rules={[
                { required: true, message: '请输入密码。' },
                { min: 6, message: '密码长度至少为 6 位。' },
              ]}
            >
              <Input.Password placeholder="至少 6 位" autoComplete="new-password" />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              label="确认密码"
              dependencies={['password']}
              rules={[
                { required: true, message: '请再次输入密码。' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve();
                    }

                    return Promise.reject(new Error('两次输入的密码不一致。'));
                  },
                }),
              ]}
            >
              <Input.Password placeholder="再次输入密码" autoComplete="new-password" />
            </Form.Item>

            <Button
              type="primary"
              htmlType="submit"
              block
              loading={registering}
              className="login-submit"
            >
              注册并登录
            </Button>
          </Form>
        )}
      </Card>

      {appDownloadAvailable && appDownloadUrl ? (
        <aside className="login-download-card" aria-label="安卓版下载">
          <div className="login-download-head">
            <img
              src="/apple-touch-icon.png"
              alt="Alicia云盘"
              className="login-download-icon"
            />
            <div className="login-download-copy">
              <Typography.Text className="login-download-eyebrow">Android App</Typography.Text>
              <Typography.Title level={5} className="login-download-title">
                Alicia云盘 APK
              </Typography.Title>
            </div>
          </div>
          <Typography.Paragraph className="login-download-note">
            扫码下载移动端安装包
          </Typography.Paragraph>
          <a
            href={appDownloadUrl}
            target="_blank"
            rel="noreferrer"
            className="login-download-qr-link"
            aria-label="扫码下载 Android 客户端"
          >
            <div className="login-download-qr">
              <QRCode value={appDownloadUrl} size={124} bordered={false} />
            </div>
          </a>
          <a href={appDownloadUrl} target="_blank" rel="noreferrer" className="login-download-link">
            下载 APK
          </a>
        </aside>
      ) : null}

      <RegulatoryFooter />
    </div>
  );
}
