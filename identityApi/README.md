# Alicia Identity API

`identityApi` is the future owner of Alicia's shared identity boundary.

Current status:

- The module is scaffolded and buildable.
- It exposes an independent health endpoint for deployment checks.
- It can read public identity fields from the existing `sys_user` table.
- No production traffic is routed here yet.
- Existing login, registration, token, and account-management behavior still runs in `CloudStorageApi`.

Local endpoints:

- `GET /api/identity/health`
- `GET /api/identity/internal/users/{userId}`
- `POST /api/identity/auth/login`
- `GET /api/identity/auth/me`

The internal user endpoint is read-only and does not return `password_hash`.
The login and current-user endpoints are for isolated identity verification only; production `/api/auth/**` still routes to `CloudStorageApi`.

Planned migration order:

1. Verify login and current-user reads inside this module.
2. Move email registration and verification.
3. Move password changes and administrator identity management.
4. Let `CloudStorageApi` consume identity tokens instead of issuing them.
