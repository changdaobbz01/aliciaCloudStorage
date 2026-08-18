# Alicia Identity API

`identityApi` is the future owner of Alicia's shared identity boundary.

Current status:

- The module is scaffolded and buildable.
- It exposes an independent health endpoint for deployment checks.
- It can read public identity fields from the existing `sys_user` table.
- It can independently verify login tokens and run email-code registration against `sys_user`.
- No production traffic is routed here yet.
- Existing production login, registration, token, and account-management behavior still runs in `CloudStorageApi`.

Local endpoints:

- `GET /api/identity/health`
- `GET /api/identity/internal/users/{userId}`
- `POST /api/identity/auth/login`
- `GET /api/identity/auth/me`
- `POST /api/identity/auth/register/email-code`
- `POST /api/identity/auth/register/verify`

The internal user endpoint is read-only and does not return `password_hash`.
The auth endpoints are for isolated identity verification only; production `/api/auth/**` still routes to `CloudStorageApi`.
Email registration in this module creates only the identity user; cloud-drive profile provisioning remains owned by `CloudStorageApi`.

Planned migration order:

1. Verify login, current-user reads, and email registration inside this module.
2. Move password changes and administrator identity management.
3. Let `CloudStorageApi` consume identity tokens instead of issuing them.
4. Route production `/api/auth/**` to identity after dual-track verification.
