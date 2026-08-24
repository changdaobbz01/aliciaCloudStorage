# Alicia Identity API

`identityApi` is the future owner of Alicia's shared identity boundary.

Current status:

- The module is buildable and runs as the identity service in the default compose stack.
- It exposes an independent health endpoint for deployment checks.
- It owns login, token refresh/logout, current-user identity reads, refresh-session reads/revocation, profile writes, password changes, email-code registration, and administrator identity management.
- It exposes a read-only administrator audit-log query endpoint for identity security review.
- It persists refresh-token sessions in `identity_refresh_token`; login/registration return both `token` and `refreshToken`.
- It issues JWT access tokens with configurable `alg`, `iss`, `aud`, and `kid`, plus `sub`, `iat`, `exp`, `ver`, and optional `sid`; HS256 remains the default, RS256/JWKS can be enabled by configuration, verification accepts configured previous HS256/RSA JWT keys, and legacy two-part access tokens are no longer accepted.
- It owns identity Flyway migrations under `src/main/resources/db/identity-migration` and records them in `identity_flyway_schema_history`.
- It reads and writes the identity-owned `identity_user` table; older `sys_user` naming is finalized by the Identity V2 migration.
- `CloudStorageApi` preflights RS256 access tokens with the Identity JWKS, then calls Identity for strongly consistent role, status, token-version, and refresh-session validation before adding cloud-drive profile data such as quota and home background.

Local endpoints:

- `GET /api/identity/health`
- `GET /api/identity/health/dependencies`
- `GET /api/identity/.well-known/jwks.json`
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
The auth endpoints are the production identity boundary. `POST /api/identity/auth/token/refresh` requires a JSON body containing `refreshToken` and rotates it; Authorization-only refresh is no longer accepted. `GET /api/identity/auth/sessions` lists the current user's refresh sessions without exposing token material, and `DELETE /api/identity/auth/sessions/{sessionId}` revokes one of that user's sessions and records `SESSION_REVOKE` audit events. `POST /api/identity/auth/logout` revokes the current refresh session by default, while `{"allDevices":true}` increments `token_version` and revokes all refresh sessions for the user. Email registration in this module creates only the identity user; cloud-drive profile provisioning remains owned by `CloudStorageApi`.

JWT signing is configured through `ALICIA_AUTH_TOKEN_ALGORITHM`, `ALICIA_AUTH_TOKEN_ISSUER`, `ALICIA_AUTH_TOKEN_AUDIENCE`, and `ALICIA_AUTH_TOKEN_KEY_ID`; the default compose stack keeps `HS256` and production-compatible metadata for the shared-domain deployment, while the current production `.env` overrides signing to `RS256/alicia-rs256-20260822035821`. For HS256 key rotation, put the new signing key in `ALICIA_AUTH_TOKEN_SECRET` and `ALICIA_AUTH_TOKEN_KEY_ID`, then keep old verification keys in `ALICIA_AUTH_TOKEN_PREVIOUS_KEYS` using `old-kid=old-secret;older-kid=older-secret` until older access tokens expire. For RS256, configure `ALICIA_AUTH_TOKEN_ALGORITHM=RS256`, a PKCS#8 private key in `ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY`, its X.509 public key in `ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY`, and any historical public keys in `ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS` using `old-kid=public-key;older-kid=public-key`.

Use `deploy/scripts/generate-identity-rs256-env.sh` to generate an RS256 key pair and a git-ignored `.env` snippet. When switching from HS256 to RS256, keep the previous HS256 signing secret in `ALICIA_AUTH_TOKEN_PREVIOUS_KEYS` until older access tokens expire; if `.env` omits `ALICIA_AUTH_TOKEN_KEY_ID`, the helper uses the compose default `alicia-hs256-v1`.
Before editing production `.env`, use `deploy/scripts/verify-identity-rs256-dry-run.sh` to temporarily start identity with the generated RS256 snippet, run the shared route verification, and restore the container from `.env`.
After the dry run passes, use `deploy/scripts/prepare-identity-rs256-cutover-env.sh` to create a git-ignored candidate `.env` and print explicit cutover and rollback commands without exposing private key material in the terminal. The prepare helper can also derive the previous HS256 verification key from the current `.env` for older snippets.
When a historical HS256 JWT key is no longer needed, use `deploy/scripts/prepare-identity-hs256-key-removal-env.sh` to create a candidate `.env` that removes it from `ALICIA_AUTH_TOKEN_PREVIOUS_KEYS`; it defaults to `alicia-hs256-v1` and does not overwrite production `.env`. In unlaunched environments, `deploy/scripts/apply-identity-hs256-key-removal-env.sh` can perform the backup, apply, identity restart, verification, and automatic rollback as one operation.

Schema migrations:

- New identity-owned migrations go in `src/main/resources/db/identity-migration`.
- Flyway uses the dedicated table `identity_flyway_schema_history`, separate from CloudStorageApi's historical `flyway_schema_history`.
- `V2__rename_sys_user_to_identity_user.sql` finalizes the identity table name as `identity_user`.
- During the shared-database transition, CloudStorageApi keeps its already-applied V1-V16 migration files for compatibility; V16 removes cloud-table foreign keys that pointed at the identity table. Do not add new identity table changes there.
- CloudStorageApi has a migration boundary test that fails when new identity-owned schema fragments are added to its migration directory.
- identityApi has the matching boundary test that fails when cloud-drive schema fragments are added to Identity migrations.

Planned migration order:

1. Keep public identity writes on `/api/identity/auth/**` and `/api/identity/admin/**`.
2. Keep cloud-drive aggregate profile reads and media uploads on `/api/cloud-profile/**`.
3. Continue reducing direct identity-table coupling from `CloudStorageApi`.
4. Move to a dedicated identity-owned database/schema once the service boundary is stable.
