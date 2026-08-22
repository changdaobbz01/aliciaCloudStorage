# Alicia Identity API

`identityApi` is the future owner of Alicia's shared identity boundary.

Current status:

- The module is buildable and runs as the identity service in the default compose stack.
- It exposes an independent health endpoint for deployment checks.
- It owns login, token refresh/logout, current-user identity reads, refresh-session reads/revocation, profile writes, password changes, email-code registration, and administrator identity management.
- It exposes a read-only administrator audit-log query endpoint for identity security review.
- It persists refresh-token sessions in `identity_refresh_token`; login/registration return both `token` and `refreshToken`.
- It owns identity Flyway migrations under `src/main/resources/db/identity-migration` and records them in `identity_flyway_schema_history`.
- It still reads and writes the existing `sys_user` table during the migration period.
- `CloudStorageApi` consumes identity tokens and only adds cloud-drive profile data such as quota and home background.

Local endpoints:

- `GET /api/identity/health`
- `GET /api/identity/internal/users/{userId}`
- `POST /api/identity/auth/login`
- `GET /api/identity/auth/me`
- `POST /api/identity/auth/token/refresh`
- `POST /api/identity/auth/logout`
- `GET /api/identity/auth/sessions`
- `DELETE /api/identity/auth/sessions/{sessionId}`
- `POST /api/identity/auth/register/email-code`
- `POST /api/identity/auth/register/verify`
- `GET /api/identity/admin/users`
- `POST /api/identity/admin/users`
- `PUT /api/identity/admin/users/{userId}/password`
- `GET /api/identity/admin/audit-logs`

The internal user endpoint is read-only and does not return `password_hash`.
The auth endpoints are the production identity boundary. `POST /api/identity/auth/token/refresh` prefers a JSON body containing `refreshToken` and rotates it; Authorization-only refresh remains as a compatibility path. `GET /api/identity/auth/sessions` lists the current user's refresh sessions without exposing token material, and `DELETE /api/identity/auth/sessions/{sessionId}` revokes one of that user's sessions. `POST /api/identity/auth/logout` revokes the current refresh session by default, while `{"allDevices":true}` increments `token_version` and revokes all refresh sessions for the user. Email registration in this module creates only the identity user; cloud-drive profile provisioning remains owned by `CloudStorageApi`.

Schema migrations:

- New identity-owned migrations go in `src/main/resources/db/identity-migration`.
- Flyway uses the dedicated table `identity_flyway_schema_history`, separate from CloudStorageApi's historical `flyway_schema_history`.
- During the shared-database transition, CloudStorageApi keeps its already-applied V1-V14 migration files for compatibility. Do not add new identity table changes there.
- CloudStorageApi has a migration boundary test that fails when new identity-owned schema fragments are added to its migration directory.
- identityApi has the matching boundary test that fails when cloud-drive schema fragments are added to Identity migrations.

Planned migration order:

1. Keep public identity writes on `/api/identity/auth/**` and `/api/identity/admin/**`.
2. Keep cloud-drive aggregate profile reads and media uploads on `/api/cloud-profile/**`.
3. Continue reducing direct `sys_user` coupling from `CloudStorageApi`.
4. Move to a dedicated identity-owned database/schema once the service boundary is stable.
