#!/usr/bin/env bash
set -Eeuo pipefail

SNIPPET_FILE="${1:-}"
SNIPPET_DIR="${ALICIA_RS256_SNIPPET_DIR:-deploy/generated/identity-rs256}"
ENV_FILE="${ALICIA_RS256_BASE_ENV_FILE:-.env}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
VERIFY_SCRIPT="${ALICIA_RS256_VERIFY_SCRIPT:-deploy/scripts/verify-identity-cloud-routes.sh}"
IDENTITY_BASE_URL="${ALICIA_IDENTITY_BASE_URL:-http://127.0.0.1:8093}"
PUBLIC_BASE_URL="${ALICIA_PUBLIC_BASE_URL:-https://127.0.0.1}"
CURL_TIMEOUT="${ALICIA_VERIFY_CURL_TIMEOUT_SECONDS:-12}"
INSECURE_TLS="${ALICIA_VERIFY_INSECURE_TLS:-true}"
BUILD_IDENTITY="${ALICIA_RS256_DRY_RUN_BUILD:-false}"
RESTORE_IDENTITY="${ALICIA_RS256_DRY_RUN_RESTORE:-true}"
VERIFY_RESTORED_IDENTITY="${ALICIA_RS256_DRY_RUN_VERIFY_RESTORE:-true}"

IDENTITY_BASE_URL="${IDENTITY_BASE_URL%/}"
PUBLIC_BASE_URL="${PUBLIC_BASE_URL%/}"

CURL_ARGS=(-sS --max-time "$CURL_TIMEOUT")
if [[ "$INSECURE_TLS" == "true" ]]; then
    CURL_ARGS+=(-k)
fi

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

dotenv_value() {
    local file="$1"
    local key="$2"
    local line
    if [[ ! -f "$file" ]]; then
        return 0
    fi

    line="$(sed -n "s/^[[:space:]]*$key[[:space:]]*=[[:space:]]*//p" "$file" | tail -n 1)"
    line="${line%$'\r'}"
    line="${line%%#*}"
    line="${line#"${line%%[![:space:]]*}"}"
    line="${line%"${line##*[![:space:]]}"}"

    if [[ "${line:0:1}" == "\"" && "${line: -1}" == "\"" ]]; then
        line="${line:1:${#line}-2}"
    elif [[ "${line:0:1}" == "'" && "${line: -1}" == "'" ]]; then
        line="${line:1:${#line}-2}"
    fi

    printf '%s' "$line"
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

latest_snippet() {
    ls -1t "$SNIPPET_DIR"/*.env 2>/dev/null | head -n 1 || true
}

wait_for_identity() {
    local attempt
    for attempt in $(seq 1 30); do
        if curl -fsS "${CURL_ARGS[@]}" "$IDENTITY_BASE_URL/api/identity/health" >/dev/null 2>&1; then
            return 0
        fi
        sleep 2
    done

    fail "identity did not become healthy at $IDENTITY_BASE_URL"
}

up_identity() {
    if [[ "$BUILD_IDENTITY" == "true" ]]; then
        compose up -d --build identity
    else
        compose up -d --no-build identity
    fi
    wait_for_identity
}

clear_identity_token_env() {
    unset ALICIA_AUTH_TOKEN_ALGORITHM
    unset ALICIA_AUTH_TOKEN_KEY_ID
    unset ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY
    unset ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY
    unset ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS
    unset ALICIA_AUTH_TOKEN_PREVIOUS_KEYS
}

restored=false
restore_identity() {
    if [[ "$RESTORE_IDENTITY" != "true" || "$restored" == "true" ]]; then
        return 0
    fi

    printf '\nRestoring identity container from %s...\n' "$ENV_FILE"
    (
        clear_identity_token_env
        BUILD_IDENTITY=false
        up_identity
    )
    restored=true
}

run_verify() {
    local label="$1"
    local expected_algorithm="${2:-}"
    local expected_key_id="${3:-}"

    printf '\nRunning %s verification...\n' "$label"
    if [[ -n "$expected_algorithm" && -n "$expected_key_id" ]]; then
        ALICIA_VERIFY_ACCOUNT="$ACCOUNT" \
        ALICIA_VERIFY_PASSWORD="$PASSWORD" \
        ALICIA_VERIFY_TOKEN_ALGORITHM="$expected_algorithm" \
        ALICIA_VERIFY_TOKEN_KEY_ID="$expected_key_id" \
        bash "$VERIFY_SCRIPT"
    else
        ALICIA_VERIFY_ACCOUNT="$ACCOUNT" \
        ALICIA_VERIFY_PASSWORD="$PASSWORD" \
        bash "$VERIFY_SCRIPT"
    fi
}

if [[ -z "$SNIPPET_FILE" ]]; then
    SNIPPET_FILE="$(latest_snippet)"
fi
[[ -n "$SNIPPET_FILE" ]] || fail "No RS256 env snippet found under $SNIPPET_DIR."
[[ -f "$SNIPPET_FILE" ]] || fail "RS256 env snippet not found: $SNIPPET_FILE"
[[ -f "$VERIFY_SCRIPT" ]] || fail "Verify script not found: $VERIFY_SCRIPT"

RS256_ALGORITHM="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_ALGORITHM)"
RS256_KEY_ID="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_KEY_ID)"
RS256_PRIVATE_KEY="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY)"
RS256_PUBLIC_KEY="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY)"
RS256_PREVIOUS_RSA_PUBLIC_KEYS="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS)"
RS256_PREVIOUS_KEYS="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_PREVIOUS_KEYS)"

[[ "$RS256_ALGORITHM" == "RS256" ]] || fail "Snippet must set ALICIA_AUTH_TOKEN_ALGORITHM=RS256."
[[ -n "$RS256_KEY_ID" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_KEY_ID."
[[ -n "$RS256_PRIVATE_KEY" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY."
[[ -n "$RS256_PUBLIC_KEY" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY."
if [[ -z "$RS256_PREVIOUS_KEYS" ]]; then
    printf '[WARN] Snippet does not contain ALICIA_AUTH_TOKEN_PREVIOUS_KEYS; existing HS256 access tokens may stop verifying during the dry run.\n' >&2
fi

ACCOUNT="${ALICIA_VERIFY_ACCOUNT:-}"
PASSWORD="${ALICIA_VERIFY_PASSWORD:-}"
if [[ -z "$ACCOUNT" ]]; then
    read -r -p "Identity account/email/phone: " ACCOUNT
fi
if [[ -z "$PASSWORD" ]]; then
    read -r -s -p "Identity password: " PASSWORD
    printf '\n'
fi

printf 'Using RS256 env snippet: %s\n' "$SNIPPET_FILE"
printf 'Temporary JWT config: alg=%s kid=%s\n' "$RS256_ALGORITHM" "$RS256_KEY_ID"
printf 'Public base: %s\n' "$PUBLIC_BASE_URL"

trap 'restore_identity || true' EXIT

export ALICIA_AUTH_TOKEN_ALGORITHM="$RS256_ALGORITHM"
export ALICIA_AUTH_TOKEN_KEY_ID="$RS256_KEY_ID"
export ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY="$RS256_PRIVATE_KEY"
export ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY="$RS256_PUBLIC_KEY"
export ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS="$RS256_PREVIOUS_RSA_PUBLIC_KEYS"
export ALICIA_AUTH_TOKEN_PREVIOUS_KEYS="$RS256_PREVIOUS_KEYS"

printf '\nStarting identity with temporary RS256 config...\n'
up_identity
run_verify "RS256 dry-run" "$RS256_ALGORITHM" "$RS256_KEY_ID"

restore_identity

if [[ "$RESTORE_IDENTITY" == "true" && "$VERIFY_RESTORED_IDENTITY" == "true" ]]; then
    run_verify "restored identity"
elif [[ "$RESTORE_IDENTITY" != "true" ]]; then
    printf '\n[WARN] Identity was left running with the temporary RS256 config because ALICIA_RS256_DRY_RUN_RESTORE=false.\n' >&2
fi

trap - EXIT
printf '\nIdentity RS256 dry run completed.\n'
