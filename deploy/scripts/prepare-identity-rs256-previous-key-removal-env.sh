#!/usr/bin/env bash
set -Eeuo pipefail

TARGET_KEY_ID="${1:-${ALICIA_RS256_PREVIOUS_KEY_ID:-}}"
ENV_FILE="${ALICIA_RS256_CLEANUP_BASE_ENV_FILE:-.env}"
OUTPUT_DIR="${ALICIA_RS256_CLEANUP_OUTPUT_DIR:-deploy/generated/identity-rs256}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
VERIFY_SCRIPT="${ALICIA_RS256_CLEANUP_VERIFY_SCRIPT:-deploy/scripts/verify-identity-cloud-routes.sh}"

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

trim() {
    local value="$1"
    value="${value#"${value%%[![:space:]]*}"}"
    value="${value%"${value##*[![:space:]]}"}"
    printf '%s' "$value"
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
    line="$(trim "$line")"

    if [[ "${line:0:1}" == "\"" && "${line: -1}" == "\"" ]]; then
        line="${line:1:${#line}-2}"
    elif [[ "${line:0:1}" == "'" && "${line: -1}" == "'" ]]; then
        line="${line:1:${#line}-2}"
    fi

    printf '%s' "$line"
}

replace_or_append_env() {
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

filter_previous_rsa_public_keys() {
    local previous_keys="$1"
    local target_key_id="$2"
    local entry
    local trimmed_entry
    local entry_key_id
    local result=""
    local -a entries=()

    REMOVED_COUNT=0
    REMAINING_COUNT=0

    IFS=';' read -r -a entries <<< "$previous_keys"
    for entry in "${entries[@]}"; do
        trimmed_entry="$(trim "$entry")"
        [[ -n "$trimmed_entry" ]] || continue
        [[ "$trimmed_entry" == *"="* ]] || fail "Malformed ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS entry without '='."

        entry_key_id="$(trim "${trimmed_entry%%=*}")"
        [[ -n "$entry_key_id" ]] || fail "Malformed ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS entry with empty key id."

        if [[ "$entry_key_id" == "$target_key_id" ]]; then
            REMOVED_COUNT=$((REMOVED_COUNT + 1))
            continue
        fi

        if [[ -z "$result" ]]; then
            result="$trimmed_entry"
        else
            result="$result;$trimmed_entry"
        fi
        REMAINING_COUNT=$((REMAINING_COUNT + 1))
    done

    FILTERED_PREVIOUS_RSA_PUBLIC_KEYS="$result"
}

[[ -n "$TARGET_KEY_ID" ]] || fail "Target RS256 previous key id is required. Pass it as the first argument."
[[ "$TARGET_KEY_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "Target key id may only contain letters, numbers, dot, underscore, or dash."
[[ -f "$ENV_FILE" ]] || fail "Base env file not found: $ENV_FILE"

TOKEN_ALGORITHM="$(dotenv_value "$ENV_FILE" ALICIA_AUTH_TOKEN_ALGORITHM)"
TOKEN_ALGORITHM="${TOKEN_ALGORITHM:-HS256}"
TOKEN_ALGORITHM="$(printf '%s' "$TOKEN_ALGORITHM" | tr '[:lower:]' '[:upper:]')"
CURRENT_KEY_ID="$(dotenv_value "$ENV_FILE" ALICIA_AUTH_TOKEN_KEY_ID)"
PREVIOUS_RSA_PUBLIC_KEYS="$(dotenv_value "$ENV_FILE" ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS)"
TOKEN_EXPIRE_SECONDS="$(dotenv_value "$ENV_FILE" ALICIA_AUTH_TOKEN_EXPIRE_SECONDS)"
TOKEN_EXPIRE_SECONDS="${TOKEN_EXPIRE_SECONDS:-604800}"

[[ "$TOKEN_ALGORITHM" == "RS256" ]] || fail "Current env must sign with RS256 before removing historical RS256 keys."
[[ -n "$CURRENT_KEY_ID" ]] || fail "Base env is missing ALICIA_AUTH_TOKEN_KEY_ID."
[[ "$CURRENT_KEY_ID" != "$TARGET_KEY_ID" ]] || fail "Refusing to remove the current signing key id: $TARGET_KEY_ID"
[[ -n "$PREVIOUS_RSA_PUBLIC_KEYS" ]] || fail "Base env has no ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS to clean."
[[ "$TOKEN_EXPIRE_SECONDS" =~ ^[0-9]+$ ]] || fail "ALICIA_AUTH_TOKEN_EXPIRE_SECONDS must be a number when set."

filter_previous_rsa_public_keys "$PREVIOUS_RSA_PUBLIC_KEYS" "$TARGET_KEY_ID"
[[ "$REMOVED_COUNT" -gt 0 ]] || fail "Key id $TARGET_KEY_ID was not found in ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS."

umask 077
mkdir -p "$OUTPUT_DIR"

TIMESTAMP="$(date -u +%Y%m%d%H%M%S)"
CANDIDATE_FILE="$OUTPUT_DIR/remove-rsa-$TARGET_KEY_ID.candidate.$TIMESTAMP.env"
BACKUP_FILE="$OUTPUT_DIR/remove-rsa-$TARGET_KEY_ID.backup.$TIMESTAMP.env"
TEMP_FILE="$CANDIDATE_FILE.tmp"

cp "$ENV_FILE" "$TEMP_FILE"
replace_or_append_env "$TEMP_FILE" "$CANDIDATE_FILE" ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS "$FILTERED_PREVIOUS_RSA_PUBLIC_KEYS"
rm -f "$TEMP_FILE"
chmod 600 "$CANDIDATE_FILE"

printf 'Prepared RS256 previous-key removal candidate env:\n'
printf '  base env:           %s\n' "$ENV_FILE"
printf '  candidate env:      %s\n' "$CANDIDATE_FILE"
printf '  backup path:        %s\n' "$BACKUP_FILE"
printf '  current alg/kid:    %s/%s\n' "$TOKEN_ALGORITHM" "$CURRENT_KEY_ID"
printf '  removed RS256 kid:  %s\n' "$TARGET_KEY_ID"
printf '  removed entries:    %s\n' "$REMOVED_COUNT"
printf '  remaining RSA keys: %s\n' "$REMAINING_COUNT"
printf '  access token ttl:   %s seconds\n' "$TOKEN_EXPIRE_SECONDS"
printf '\nApply this after confirming active clients no longer need the historical RS256 JWT key.\n'
printf 'Sensitive key values are written only to the candidate file and are not printed here.\n'
printf '\nWhen you are ready to remove the historical RS256 key, run:\n'
printf '  install -m 600 %s %s\n' "$ENV_FILE" "$BACKUP_FILE"
printf '  install -m 600 %s %s\n' "$CANDIDATE_FILE" "$ENV_FILE"
printf '  sudo docker compose'
for file in $COMPOSE_FILES; do
    printf ' -f %s' "$file"
done
printf ' up -d --no-build identity\n'
printf '  ALICIA_VERIFY_TOKEN_ALGORITHM=RS256 ALICIA_VERIFY_TOKEN_KEY_ID=%s ALICIA_VERIFY_FORBID_PREVIOUS_RSA_KEY_ID=%s bash %s\n' "$CURRENT_KEY_ID" "$TARGET_KEY_ID" "$VERIFY_SCRIPT"
printf '\nRollback command if verification fails:\n'
printf '  install -m 600 %s %s\n' "$BACKUP_FILE" "$ENV_FILE"
printf '  sudo docker compose'
for file in $COMPOSE_FILES; do
    printf ' -f %s' "$file"
done
printf ' up -d --no-build identity\n'
printf '  ALICIA_VERIFY_TOKEN_ALGORITHM=RS256 ALICIA_VERIFY_TOKEN_KEY_ID=%s bash %s\n' "$CURRENT_KEY_ID" "$VERIFY_SCRIPT"
