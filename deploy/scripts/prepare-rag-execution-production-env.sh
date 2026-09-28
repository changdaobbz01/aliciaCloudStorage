#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deploy/scripts/lib/rag-execution-production-common.sh
source "$SCRIPT_DIR/lib/rag-execution-production-common.sh"

STAGE="${1:-${ALICIA_RAG_EXECUTION_ROLLOUT_STAGE:-}}"
ACTION="${2:-${ALICIA_RAG_EXECUTION_CANARY_ACTION:-FOLDER_CREATE}}"
ENV_FILE="${ALICIA_RAG_EXECUTION_BASE_ENV_FILE:-.env}"
OUTPUT_DIR="${ALICIA_RAG_EXECUTION_ENV_OUTPUT_DIR:-deploy/generated/rag-execution}"
VERIFY_SCRIPT="${ALICIA_RAG_EXECUTION_VERIFY_SCRIPT:-deploy/scripts/verify-rag-execution-production.sh}"
UPDATE_SCRIPT="${ALICIA_RAG_EXECUTION_UPDATE_SCRIPT:-deploy/scripts/update-rag-execution-production.sh}"

[[ -n "$STAGE" ]] || rag_execution_fail "Rollout stage is required: foundation, shadow, or admin-single."
rag_execution_validate_stage "$STAGE"
ACTION="$(rag_execution_normalize_action "$ACTION")"
[[ -f "$ENV_FILE" ]] || rag_execution_fail "Base env file not found: $ENV_FILE"

generate_secret() {
    if command -v openssl >/dev/null 2>&1; then
        openssl rand -hex 32
        return
    fi
    command -v od >/dev/null 2>&1 || rag_execution_fail "openssl or od is required to generate secrets."
    od -An -N32 -tx1 /dev/urandom | tr -d ' \n'
}

set_candidate_value() {
    local key="$1"
    local value="$2"
    local next_file="$CANDIDATE_FILE.next"
    rag_execution_replace_or_append_env "$CANDIDATE_FILE" "$next_file" "$key" "$value"
    mv "$next_file" "$CANDIDATE_FILE"
}

ensure_secret() {
    local key="$1"
    local minimum_length="$2"
    local current
    current="$(rag_execution_dotenv_value "$CANDIDATE_FILE" "$key")"
    if [[ ${#current} -lt "$minimum_length" || "$current" == "CHANGE_ME" || "$current" == *"change-me"* || "$current" == replace-with-* ]]; then
        set_candidate_value "$key" "$(generate_secret)"
        GENERATED_SECRET_KEYS+=("$key")
    fi
}

umask 077
mkdir -p "$OUTPUT_DIR"
TIMESTAMP="$(date -u +%Y%m%d%H%M%S)"
CANDIDATE_FILE="$OUTPUT_DIR/$STAGE.candidate.$TIMESTAMP.env"
BACKUP_FILE="$OUTPUT_DIR/$STAGE.backup.$TIMESTAMP.env"
GENERATED_SECRET_KEYS=()

cp "$ENV_FILE" "$CANDIDATE_FILE"
while IFS='=' read -r key value; do
    [[ -n "$key" ]] || continue
    set_candidate_value "$key" "$value"
done < <(rag_execution_stage_config "$STAGE" "$ACTION")

ensure_secret ALICIA_RAG_EXECUTION_DB_PASSWORD 24
if [[ "$STAGE" != "foundation" ]]; then
    ensure_secret ALICIA_RAG_EXECUTION_SERVICE_SECRET 32
fi
if [[ "$STAGE" == "admin-single" ]]; then
    ensure_secret ALICIA_RAG_EXECUTION_CLOUD_SERVICE_SECRET 32
    registration_secret="$(rag_execution_dotenv_value "$CANDIDATE_FILE" ALICIA_RAG_EXECUTION_SERVICE_SECRET)"
    cloud_secret="$(rag_execution_dotenv_value "$CANDIDATE_FILE" ALICIA_RAG_EXECUTION_CLOUD_SERVICE_SECRET)"
    if [[ "$registration_secret" == "$cloud_secret" ]]; then
        set_candidate_value ALICIA_RAG_EXECUTION_CLOUD_SERVICE_SECRET "$(generate_secret)"
        GENERATED_SECRET_KEYS+=("ALICIA_RAG_EXECUTION_CLOUD_SERVICE_SECRET")
    fi
fi

chmod 600 "$CANDIDATE_FILE"
rag_execution_assert_env_stage "$CANDIDATE_FILE" "$STAGE" "$ACTION"

printf 'Prepared RAG execution production candidate:\n'
printf '  stage:             %s\n' "$STAGE"
if [[ "$STAGE" == "admin-single" ]]; then
    printf '  allowed action:    %s\n' "$ACTION"
fi
printf '  base env:          %s\n' "$ENV_FILE"
printf '  candidate env:     %s\n' "$CANDIDATE_FILE"
printf '  backup path:       %s\n' "$BACKUP_FILE"
printf '  generated secrets: %s\n' "${#GENERATED_SECRET_KEYS[@]}"
printf 'Secret values were written only to the mode-600 candidate and are not printed.\n'
printf '\nPreflight the candidate without changing production:\n'
printf '  ALICIA_RAG_EXECUTION_ENV_FILE=%q bash %q %q %q --preflight\n' \
    "$CANDIDATE_FILE" "$VERIFY_SCRIPT" "$STAGE" "$ACTION"
printf '\nInstall and apply only during an approved production window:\n'
printf '  install -m 600 %q %q\n' "$ENV_FILE" "$BACKUP_FILE"
printf '  install -m 600 %q %q\n' "$CANDIDATE_FILE" "$ENV_FILE"
printf '  bash %q --stage %q --action %q --apply\n' "$UPDATE_SCRIPT" "$STAGE" "$ACTION"
printf '\nRollback configuration:\n'
printf '  install -m 600 %q %q\n' "$BACKUP_FILE" "$ENV_FILE"
printf '  bash %q --stage foundation --apply\n' "$UPDATE_SCRIPT"
