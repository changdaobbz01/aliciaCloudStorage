# Alicia Identity API

`identityApi` is the future owner of Alicia's shared identity boundary.

Current status:

- The module is scaffolded and buildable.
- It exposes an independent health endpoint for deployment checks.
- No production traffic is routed here yet.
- Existing login, registration, token, and account-management behavior still runs in `CloudStorageApi`.

Planned migration order:

1. Move login and current-user reads into this module.
2. Move email registration and verification.
3. Move password changes and administrator identity management.
4. Let `CloudStorageApi` consume identity tokens instead of issuing them.
