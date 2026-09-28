#!/usr/bin/env bash

rag_execution_fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

rag_execution_ok() {
    printf '[OK] %s\n' "$1"
}

rag_execution_require_boolean() {
    local name="$1"
    local value="$2"

    [[ "$value" == "true" || "$value" == "false" ]] \
        || rag_execution_fail "$name must be 'true' or 'false' (current: '$value')."
}

rag_execution_require_positive_integer() {
    local name="$1"
    local value="$2"

    [[ "$value" =~ ^[1-9][0-9]*$ ]] \
        || rag_execution_fail "$name must be a positive integer (current: '$value')."
}

rag_execution_require_non_negative_integer() {
    local name="$1"
    local value="$2"

    [[ "$value" =~ ^[0-9]+$ ]] \
        || rag_execution_fail "$name must be a non-negative integer (current: '$value')."
}

rag_execution_docker() {
    local command=(docker)

    if [[ "${ALICIA_DOCKER_SUDO:-auto}" == "true" ]]; then
        command=(sudo docker)
    elif [[ "${ALICIA_DOCKER_SUDO:-auto}" == "auto" && "${EUID:-$(id -u)}" -ne 0 ]]; then
        if ! docker info >/dev/null 2>&1; then
            command=(sudo docker)
        fi
    fi

    "${command[@]}" "$@"
}

rag_execution_trim() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
}

rag_execution_dotenv_value() {
    local file="$1"
    local key="$2"
    local line

    [[ -f "$file" ]] || return 0
    line="$(sed -n "s/^[[:space:]]*$key[[:space:]]*=[[:space:]]*//p" "$file" | tail -n 1)"
    line="${line%$'\r'}"
    line="${line%%#*}"
    line="$(rag_execution_trim "$line")"

    if [[ "${line:0:1}" == "\"" && "${line: -1}" == "\"" ]]; then
        line="${line:1:${#line}-2}"
    elif [[ "${line:0:1}" == "'" && "${line: -1}" == "'" ]]; then
        line="${line:1:${#line}-2}"
    fi

    printf '%s' "$line"
}

rag_execution_replace_or_append_env() {
    local input_file="$1"
    local output_file="$2"
    local key="$3"
    local value="$4"

    KEY="$key" VALUE="$value" awk '
        BEGIN { replaced = 0 }
        $0 ~ "^[[:space:]]*" ENVIRON["KEY"] "[[:space:]]*=" {
            print ENVIRON["KEY"] "=" ENVIRON["VALUE"]
            replaced = 1
            next
        }
        { print }
        END {
            if (replaced == 0) {
                print ENVIRON["KEY"] "=" ENVIRON["VALUE"]
            }
        }
    ' "$input_file" > "$output_file"
}

rag_execution_validate_stage() {
    case "$1" in
        foundation|shadow|admin-single)
            ;;
        *)
            rag_execution_fail "Unsupported rollout stage '$1'. Expected foundation, shadow, or admin-single."
            ;;
    esac
}

rag_execution_normalize_action() {
    local action
    action="$(printf '%s' "${1:-FOLDER_CREATE}" | tr '[:lower:]' '[:upper:]')"

    case "$action" in
        NODE_RENAME|NODE_TRASH|NODE_MOVE|FOLDER_CREATE|SHARE_CREATE)
            printf '%s' "$action"
            ;;
        *)
            rag_execution_fail "Admin single-action canary does not allow '$action'."
            ;;
    esac
}

rag_execution_stage_config() {
    local stage="$1"
    local action
    rag_execution_validate_stage "$stage"
    action="$(rag_execution_normalize_action "${2:-FOLDER_CREATE}")"

    printf 'ALICIA_RAG_EXECUTION_ROLLOUT_STAGE=%s\n' "$stage"
    printf 'ALICIA_RAG_EXECUTION_ADMIN_ONLY=true\n'

    case "$stage" in
        foundation)
            cat <<'EOF'
ALICIA_RAG_EXECUTION_ENABLED=false
ALICIA_RAG_EXECUTION_REGISTRATION_ENABLED=false
ALICIA_RAG_EXECUTION_PUBLIC_CONFIRM_ENABLED=false
ALICIA_RAG_EXECUTION_WORKER_ENABLED=false
ALICIA_RAG_EXECUTION_CLOUD_DISPATCH_ENABLED=false
ALICIA_RAG_EXECUTION_ALLOWED_ACTIONS=
ALICIA_RAG_EXECUTION_SHADOW_REGISTRATION_ENABLED=false
ALICIA_RAG_EXECUTION_CLOUD_ACTIONS_ENABLED=false
EOF
            ;;
        shadow)
            cat <<'EOF'
ALICIA_RAG_EXECUTION_ENABLED=true
ALICIA_RAG_EXECUTION_REGISTRATION_ENABLED=true
ALICIA_RAG_EXECUTION_PUBLIC_CONFIRM_ENABLED=false
ALICIA_RAG_EXECUTION_WORKER_ENABLED=false
ALICIA_RAG_EXECUTION_CLOUD_DISPATCH_ENABLED=false
ALICIA_RAG_EXECUTION_ALLOWED_ACTIONS=
ALICIA_RAG_EXECUTION_SHADOW_REGISTRATION_ENABLED=true
ALICIA_RAG_EXECUTION_CLOUD_ACTIONS_ENABLED=false
EOF
            ;;
        admin-single)
            cat <<EOF
ALICIA_RAG_EXECUTION_ENABLED=true
ALICIA_RAG_EXECUTION_REGISTRATION_ENABLED=true
ALICIA_RAG_EXECUTION_PUBLIC_CONFIRM_ENABLED=true
ALICIA_RAG_EXECUTION_WORKER_ENABLED=true
ALICIA_RAG_EXECUTION_CLOUD_DISPATCH_ENABLED=true
ALICIA_RAG_EXECUTION_ALLOWED_ACTIONS=$action
ALICIA_RAG_EXECUTION_SHADOW_REGISTRATION_ENABLED=true
ALICIA_RAG_EXECUTION_CLOUD_ACTIONS_ENABLED=true
EOF
            ;;
    esac
}

rag_execution_assert_env_stage() {
    local env_file="$1"
    local stage="$2"
    local action="${3:-FOLDER_CREATE}"
    local key
    local expected
    local actual

    [[ -f "$env_file" ]] || rag_execution_fail "Env file not found: $env_file"

    while IFS='=' read -r key expected; do
        [[ -n "$key" ]] || continue
        actual="$(rag_execution_dotenv_value "$env_file" "$key")"
        if [[ "$actual" != "$expected" ]]; then
            rag_execution_fail "$key must be '$expected' for stage $stage (current: '${actual:-<empty>}')."
        fi
    done < <(rag_execution_stage_config "$stage" "$action")

    local db_password
    local registration_secret
    local cloud_secret
    db_password="$(rag_execution_dotenv_value "$env_file" ALICIA_RAG_EXECUTION_DB_PASSWORD)"
    [[ ${#db_password} -ge 24 && "$db_password" != "CHANGE_ME" && "$db_password" != *"change-me"* ]] \
        || rag_execution_fail "ALICIA_RAG_EXECUTION_DB_PASSWORD must be a non-placeholder value of at least 24 characters."

    if [[ "$stage" != "foundation" ]]; then
        registration_secret="$(rag_execution_dotenv_value "$env_file" ALICIA_RAG_EXECUTION_SERVICE_SECRET)"
        [[ ${#registration_secret} -ge 32 ]] \
            || rag_execution_fail "ALICIA_RAG_EXECUTION_SERVICE_SECRET must contain at least 32 characters."
    fi

    if [[ "$stage" == "admin-single" ]]; then
        cloud_secret="$(rag_execution_dotenv_value "$env_file" ALICIA_RAG_EXECUTION_CLOUD_SERVICE_SECRET)"
        [[ ${#cloud_secret} -ge 32 ]] \
            || rag_execution_fail "ALICIA_RAG_EXECUTION_CLOUD_SERVICE_SECRET must contain at least 32 characters."
        [[ "$cloud_secret" != "$registration_secret" ]] \
            || rag_execution_fail "Registration and Cloud action HMAC secrets must be different."
    fi
}
