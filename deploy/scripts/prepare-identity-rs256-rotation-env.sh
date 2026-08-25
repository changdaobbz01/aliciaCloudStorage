#!/usr/bin/env bash
set -Eeuo pipefail

SNIPPET_FILE="${1:-}"
SNIPPET_DIR="${ALICIA_RS256_SNIPPET_DIR:-deploy/generated/identity-rs256}"
ENV_FILE="${ALICIA_RS256_ROTATION_BASE_ENV_FILE:-${ALICIA_RS256_BASE_ENV_FILE:-.env}}"
OUTPUT_DIR="${ALICIA_RS256_ROTATION_OUTPUT_DIR:-deploy/generated/identity-rs256}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"
VERIFY_SCRIPT="${ALICIA_RS256_ROTATION_VERIFY_SCRIPT:-deploy/scripts/verify-identity-cloud-routes.sh}"

TOKEN_KEYS=(
    ALICIA_AUTH_TOKEN_ALGORITHM
    ALICIA_AUTH_TOKEN_KEY_ID
    ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY
    ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY
    ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS
)

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

latest_snippet() {
    local file
    ls -1t "$SNIPPET_DIR"/*.env 2>/dev/null | while IFS= read -r file; do
        case "$(basename "$file")" in
            *.candidate.*.env|*.backup.*.env|*.rotation.*.env|remove-*) continue ;;
        esac
        printf '%s\n' "$file"
        break
    done
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

build_previous_rsa_public_keys() {
    local current_key_id="$1"
    local current_public_key="$2"
    local new_key_id="$3"
    local existing_previous="$4"
    local entry
    local trimmed_entry
    local entry_key_id
    local result="$current_key_id=$current_public_key"
    local -a entries=()

    RETAINED_PREVIOUS_RSA_COUNT=0
    SKIPPED_DUPLICATE_CURRENT_COUNT=0

    IFS=';' read -r -a entries <<< "$existing_previous"
    for entry in "${entries[@]}"; do
        trimmed_entry="$(trim "$entry")"
        [[ -n "$trimmed_entry" ]] || continue
        [[ "$trimmed_entry" == *"="* ]] || fail "Malformed ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS entry without '='."

        entry_key_id="$(trim "${trimmed_entry%%=*}")"
        [[ -n "$entry_key_id" ]] || fail "Malformed ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS entry with empty key id."
        [[ "$entry_key_id" != "$new_key_id" ]] || fail "New key id is already present in ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS: $new_key_id"

        if [[ "$entry_key_id" == "$current_key_id" ]]; then
            SKIPPED_DUPLICATE_CURRENT_COUNT=$((SKIPPED_DUPLICATE_CURRENT_COUNT + 1))
            continue
        fi

        result="$result;$trimmed_entry"
        RETAINED_PREVIOUS_RSA_COUNT=$((RETAINED_PREVIOUS_RSA_COUNT + 1))
    done

    NEXT_PREVIOUS_RSA_PUBLIC_KEYS="$result"
}

token_key_value() {
    local key="$1"

    if [[ "$key" == "ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS" ]]; then
        printf '%s' "$NEXT_PREVIOUS_RSA_PUBLIC_KEYS"
        return
    fi

    dotenv_value "$SNIPPET_FILE" "$key"
}

if [[ -z "$SNIPPET_FILE" ]]; then
    SNIPPET_FILE="$(latest_snippet)"
fi
[[ -n "$SNIPPET_FILE" ]] || fail "No RS256 env snippet found under $SNIPPET_DIR."
[[ -f "$SNIPPET_FILE" ]] || fail "RS256 env snippet not found: $SNIPPET_FILE"
[[ -f "$ENV_FILE" ]] || fail "Base env file not found: $ENV_FILE"

CURRENT_ALGORITHM="$(dotenv_value "$ENV_FILE" ALICIA_AUTH_TOKEN_ALGORITHM)"
CURRENT_ALGORITHM="${CURRENT_ALGORITHM:-HS256}"
CURRENT_ALGORITHM="$(printf '%s' "$CURRENT_ALGORITHM" | tr '[:lower:]' '[:upper:]')"
CURRENT_KEY_ID="$(dotenv_value "$ENV_FILE" ALICIA_AUTH_TOKEN_KEY_ID)"
CURRENT_PRIVATE_KEY="$(dotenv_value "$ENV_FILE" ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY)"
CURRENT_PUBLIC_KEY="$(dotenv_value "$ENV_FILE" ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY)"
CURRENT_PREVIOUS_RSA_PUBLIC_KEYS="$(dotenv_value "$ENV_FILE" ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS)"

NEW_ALGORITHM="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_ALGORITHM)"
NEW_ALGORITHM="$(printf '%s' "$NEW_ALGORITHM" | tr '[:lower:]' '[:upper:]')"
NEW_KEY_ID="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_KEY_ID)"
NEW_PRIVATE_KEY="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY)"
NEW_PUBLIC_KEY="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY)"

[[ "$CURRENT_ALGORITHM" == "RS256" ]] || fail "Current env must already sign with RS256 before RS256 key rotation."
[[ -n "$CURRENT_KEY_ID" ]] || fail "Base env is missing ALICIA_AUTH_TOKEN_KEY_ID."
[[ -n "$CURRENT_PRIVATE_KEY" ]] || fail "Base env is missing ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY."
[[ -n "$CURRENT_PUBLIC_KEY" ]] || fail "Base env is missing ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY."
[[ "$NEW_ALGORITHM" == "RS256" ]] || fail "Snippet must set ALICIA_AUTH_TOKEN_ALGORITHM=RS256."
[[ -n "$NEW_KEY_ID" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_KEY_ID."
[[ -n "$NEW_PRIVATE_KEY" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY."
[[ -n "$NEW_PUBLIC_KEY" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY."
[[ "$NEW_KEY_ID" =~ ^[A-Za-z0-9._-]+$ ]] || fail "New key id may only contain letters, numbers, dot, underscore, or dash."
[[ "$CURRENT_KEY_ID" != "$NEW_KEY_ID" ]] || fail "New key id must be different from the current signing key id: $CURRENT_KEY_ID"

build_previous_rsa_public_keys "$CURRENT_KEY_ID" "$CURRENT_PUBLIC_KEY" "$NEW_KEY_ID" "$CURRENT_PREVIOUS_RSA_PUBLIC_KEYS"

umask 077
mkdir -p "$OUTPUT_DIR"

TIMESTAMP="$(date -u +%Y%m%d%H%M%S)"
CANDIDATE_FILE="$OUTPUT_DIR/$NEW_KEY_ID.rotation.candidate.$TIMESTAMP.env"
BACKUP_FILE="$OUTPUT_DIR/$NEW_KEY_ID.rotation.backup.$TIMESTAMP.env"
TEMP_FILE="$CANDIDATE_FILE.tmp"
NEXT_FILE="$CANDIDATE_FILE.next"

cp "$ENV_FILE" "$TEMP_FILE"
for key in "${TOKEN_KEYS[@]}"; do
    value="$(token_key_value "$key")"
    replace_or_append_env "$TEMP_FILE" "$NEXT_FILE" "$key" "$value"
    mv "$NEXT_FILE" "$TEMP_FILE"
done
mv "$TEMP_FILE" "$CANDIDATE_FILE"
chmod 600 "$CANDIDATE_FILE"

printf 'Prepared RS256 rotation candidate env:\n'
printf '  base env:              %s\n' "$ENV_FILE"
printf '  snippet:               %s\n' "$SNIPPET_FILE"
printf '  candidate env:         %s\n' "$CANDIDATE_FILE"
printf '  backup path:           %s\n' "$BACKUP_FILE"
printf '  current alg/kid:       %s/%s\n' "$CURRENT_ALGORITHM" "$CURRENT_KEY_ID"
printf '  new alg/kid:           %s/%s\n' "$NEW_ALGORITHM" "$NEW_KEY_ID"
printf '  previous RSA added:    %s\n' "$CURRENT_KEY_ID"
printf '  existing RSA retained: %s\n' "$RETAINED_PREVIOUS_RSA_COUNT"
if [[ "$SKIPPED_DUPLICATE_CURRENT_COUNT" -gt 0 ]]; then
    printf '  duplicate current RSA: %s skipped\n' "$SKIPPED_DUPLICATE_CURRENT_COUNT"
fi
printf '\nSensitive key values are written only to the candidate file and are not printed here.\n'
printf '\nWhen you are ready to rotate the RS256 signing key, run:\n'
printf '  install -m 600 %s %s\n' "$ENV_FILE" "$BACKUP_FILE"
printf '  install -m 600 %s %s\n' "$CANDIDATE_FILE" "$ENV_FILE"
printf '  sudo docker compose'
for file in $COMPOSE_FILES; do
    printf ' -f %s' "$file"
done
printf ' up -d --no-build identity\n'
printf '  ALICIA_VERIFY_TOKEN_ALGORITHM=RS256 ALICIA_VERIFY_TOKEN_KEY_ID=%s bash %s\n' "$NEW_KEY_ID" "$VERIFY_SCRIPT"
printf '\nRollback command if verification fails:\n'
printf '  install -m 600 %s %s\n' "$BACKUP_FILE" "$ENV_FILE"
printf '  sudo docker compose'
for file in $COMPOSE_FILES; do
    printf ' -f %s' "$file"
done
printf ' up -d --no-build identity\n'
printf '  ALICIA_VERIFY_TOKEN_ALGORITHM=RS256 ALICIA_VERIFY_TOKEN_KEY_ID=%s bash %s\n' "$CURRENT_KEY_ID" "$VERIFY_SCRIPT"
printf '\nAfter the old RS256 access-token window is no longer needed, remove the historical RSA key with:\n'
printf '  bash deploy/scripts/prepare-identity-rs256-previous-key-removal-env.sh %s\n' "$CURRENT_KEY_ID"
