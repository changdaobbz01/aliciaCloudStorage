# Alicia Identity API

`identityApi` is the future owner of Alicia's shared identity boundary.

Current status:

- The module is buildable and runs as the identity service in the default compose stack.
- It exposes an independent health endpoint for deployment checks.
- It owns login, current-user identity reads, profile writes, password changes, email-code registration, and administrator identity management.
- It still reads and writes the existing `sys_user` table during the migration period.
- `CloudStorageApi` consumes identity tokens and only adds cloud-drive profile data such as quota and home background.

Local endpoints:

- `GET /api/identity/health`
- `GET /api/identity/internal/users/{userId}`
- `POST /api/identity/auth/login`
- `GET /api/identity/auth/me`
- `POST /api/identity/auth/register/email-code`
- `POST /api/identity/auth/register/verify`

The internal user endpoint is read-only and does not return `password_hash`.
The auth endpoints are the production identity boundary. Email registration in this module creates only the identity user; cloud-drive profile provisioning remains owned by `CloudStorageApi`.

Planned migration order:

1. Keep public identity writes on `/api/identity/auth/**` and `/api/identity/admin/**`.
2. Keep cloud-drive aggregate profile reads and media uploads on `/api/cloud-profile/**`.
3. Continue reducing direct `sys_user` coupling from `CloudStorageApi`.
4. Move to a dedicated identity-owned database/schema once the service boundary is stable.
