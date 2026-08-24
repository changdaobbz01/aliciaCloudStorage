#!/usr/bin/env bash
set -Eeuo pipefail

TARGET_KEY_ID="${1:-${ALICIA_HS256_PREVIOUS_KEY_ID:-alicia-hs256-v1}}"
ENV_FILE="${ALICIA_HS256_CLEANUP_BASE_ENV_FILE:-.env}"
PREPARE_SCRIPT="${ALICIA_HS256_CLEANUP_PREPARE_SCRIPT:-deploy/scripts/prepare-identity-hs256-key-removal-env.sh}"
VERIFY_SCRIPT="${ALICIA_HS256_CLEANUP_VERIFY_SCRIPT:-deploy/scripts/verify-identity-cloud-routes.sh}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
ROLLBACK_ON_FAILURE="${ALICIA_HS256_CLEANUP_ROLLBACK_ON_VERIFY_FAILURE:-true}"

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

ok() {
    printf '[OK] %s\n' "$1"
}

trim() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

extract_output_field() {
    local label="$1"
    local value
    value="$(printf '%s\n' "$PREPARE_OUTPUT" | sed -n "s/^[[:space:]]*$label:[[:space:]]*//p" | tail -n 1)"
    trim "$value"
}

compose() {
    local command=(docker compose)

    if [[ "${ALICIA_DOCKER_SUDO:-auto}" == "true" ]]; then
        command=(sudo docker compose)
    elif [[ "${ALICIA_DOCKER_SUDO:-auto}" == "auto" && "${EUID:-$(id -u)}" -ne 0 ]]; then
        if ! docker compose ps >/dev/null 2>&1; then
            command=(sudo docker compose)
        fi
    fi

    local file
    for file in $COMPOSE_FILES; do
        command+=(-f "$file")
    done

    "${command[@]}" "$@"
}

restart_identity() {
    compose up -d --no-build identity
}

run_route_verification() {
    ALICIA_VERIFY_TOKEN_ALGORITHM="$CURRENT_ALGORITHM" \
    ALICIA_VERIFY_TOKEN_KEY_ID="$CURRENT_KEY_ID" \
    ALICIA_VERIFY_FORBID_PREVIOUS_KEY_ID="$TARGET_KEY_ID" \
    bash "$VERIFY_SCRIPT"
}

run_rollback_verification() {
    ALICIA_VERIFY_TOKEN_ALGORITHM="$CURRENT_ALGORITHM" \
    ALICIA_VERIFY_TOKEN_KEY_ID="$CURRENT_KEY_ID" \
    bash "$VERIFY_SCRIPT"
}

rollback() {
    printf '\nRolling back identity env from backup...\n' >&2
    install -m 600 "$BACKUP_FILE" "$ENV_FILE"
    restart_identity
    run_rollback_verification
}

[[ -f "$ENV_FILE" ]] || fail "Base env file not found: $ENV_FILE"
[[ -f "$PREPARE_SCRIPT" ]] || fail "Prepare script not found: $PREPARE_SCRIPT"
[[ -f "$VERIFY_SCRIPT" ]] || fail "Verify script not found: $VERIFY_SCRIPT"

if ! PREPARE_OUTPUT="$(
    ALICIA_HS256_CLEANUP_BASE_ENV_FILE="$ENV_FILE" \
    ALICIA_HS256_CLEANUP_VERIFY_SCRIPT="$VERIFY_SCRIPT" \
    ALICIA_COMPOSE_FILES="$COMPOSE_FILES" \
    bash "$PREPARE_SCRIPT" "$TARGET_KEY_ID" 2>&1
)"; then
    printf '%s\n' "$PREPARE_OUTPUT" >&2
    exit 1
fi

printf '%s\n' "$PREPARE_OUTPUT"

CANDIDATE_FILE="$(extract_output_field "candidate env")"
BACKUP_FILE="$(extract_output_field "backup path")"
CURRENT_ALG_KID="$(extract_output_field "current alg/kid")"
CURRENT_ALGORITHM="$(trim "${CURRENT_ALG_KID%%/*}")"
CURRENT_KEY_ID="$(trim "${CURRENT_ALG_KID#*/}")"

[[ -n "$CANDIDATE_FILE" ]] || fail "Could not read candidate env path from prepare output."
[[ -n "$BACKUP_FILE" ]] || fail "Could not read backup path from prepare output."
[[ -n "$CURRENT_ALGORITHM" && -n "$CURRENT_KEY_ID" && "$CURRENT_ALG_KID" == */* ]] \
    || fail "Could not read current alg/kid from prepare output."
[[ -f "$CANDIDATE_FILE" ]] || fail "Candidate env file not found: $CANDIDATE_FILE"
[[ ! -e "$BACKUP_FILE" ]] || fail "Backup path already exists: $BACKUP_FILE"

printf '\nApplying HS256 previous-key removal...\n'
printf '  backup:    %s\n' "$BACKUP_FILE"
printf '  candidate: %s\n' "$CANDIDATE_FILE"
install -m 600 "$ENV_FILE" "$BACKUP_FILE"
install -m 600 "$CANDIDATE_FILE" "$ENV_FILE"

if ! restart_identity; then
    printf '[FAIL] identity restart failed after env replacement.\n' >&2
    if [[ "$ROLLBACK_ON_FAILURE" == "true" ]]; then
        rollback
    fi
    exit 1
fi

if ! run_route_verification; then
    printf '[FAIL] verification failed after HS256 previous-key removal.\n' >&2
    if [[ "$ROLLBACK_ON_FAILURE" == "true" ]]; then
        rollback
    fi
    exit 1
fi

ok "historical HS256 key $TARGET_KEY_ID removed and verification passed"
