#!/usr/bin/env bash
set -Eeuo pipefail

SNIPPET_FILE="${1:-}"
SNIPPET_DIR="${ALICIA_RS256_SNIPPET_DIR:-deploy/generated/identity-rs256}"
ENV_FILE="${ALICIA_RS256_BASE_ENV_FILE:-.env}"
OUTPUT_DIR="${ALICIA_RS256_OUTPUT_DIR:-deploy/generated/identity-rs256}"
COMPOSE_FILES="${ALICIA_COMPOSE_FILES:-compose.yaml compose.https.yaml}"

TOKEN_KEYS=(
    ALICIA_AUTH_TOKEN_ALGORITHM
    ALICIA_AUTH_TOKEN_KEY_ID
    ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY
    ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY
    ALICIA_AUTH_TOKEN_PREVIOUS_RSA_PUBLIC_KEYS
    ALICIA_AUTH_TOKEN_PREVIOUS_KEYS
)

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

latest_snippet() {
    ls -1t "$SNIPPET_DIR"/*.env 2>/dev/null | head -n 1 || true
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

if [[ -z "$SNIPPET_FILE" ]]; then
    SNIPPET_FILE="$(latest_snippet)"
fi
[[ -n "$SNIPPET_FILE" ]] || fail "No RS256 env snippet found under $SNIPPET_DIR."
[[ -f "$SNIPPET_FILE" ]] || fail "RS256 env snippet not found: $SNIPPET_FILE"
[[ -f "$ENV_FILE" ]] || fail "Base env file not found: $ENV_FILE"

RS256_ALGORITHM="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_ALGORITHM)"
RS256_KEY_ID="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_KEY_ID)"
RS256_PRIVATE_KEY="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY)"
RS256_PUBLIC_KEY="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY)"
RS256_PREVIOUS_KEYS="$(dotenv_value "$SNIPPET_FILE" ALICIA_AUTH_TOKEN_PREVIOUS_KEYS)"

[[ "$RS256_ALGORITHM" == "RS256" ]] || fail "Snippet must set ALICIA_AUTH_TOKEN_ALGORITHM=RS256."
[[ -n "$RS256_KEY_ID" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_KEY_ID."
[[ -n "$RS256_PRIVATE_KEY" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_RSA_PRIVATE_KEY."
[[ -n "$RS256_PUBLIC_KEY" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_RSA_PUBLIC_KEY."
[[ -n "$RS256_PREVIOUS_KEYS" ]] || fail "Snippet is missing ALICIA_AUTH_TOKEN_PREVIOUS_KEYS for HS256 compatibility."

umask 077
mkdir -p "$OUTPUT_DIR"

TIMESTAMP="$(date -u +%Y%m%d%H%M%S)"
CANDIDATE_FILE="$OUTPUT_DIR/$RS256_KEY_ID.candidate.$TIMESTAMP.env"
BACKUP_FILE="$OUTPUT_DIR/$RS256_KEY_ID.backup.$TIMESTAMP.env"
TEMP_FILE="$CANDIDATE_FILE.tmp"
NEXT_FILE="$CANDIDATE_FILE.next"

cp "$ENV_FILE" "$TEMP_FILE"
for key in "${TOKEN_KEYS[@]}"; do
    value="$(dotenv_value "$SNIPPET_FILE" "$key")"
    replace_or_append_env "$TEMP_FILE" "$NEXT_FILE" "$key" "$value"
    mv "$NEXT_FILE" "$TEMP_FILE"
done
mv "$TEMP_FILE" "$CANDIDATE_FILE"
chmod 600 "$CANDIDATE_FILE"

printf 'Prepared RS256 cutover candidate env:\n'
printf '  base env:      %s\n' "$ENV_FILE"
printf '  snippet:       %s\n' "$SNIPPET_FILE"
printf '  candidate env: %s\n' "$CANDIDATE_FILE"
printf '  backup path:   %s\n' "$BACKUP_FILE"
printf '  alg/kid:       %s/%s\n' "$RS256_ALGORITHM" "$RS256_KEY_ID"
printf '\nSensitive key values are written only to the candidate file and are not printed here.\n'
printf '\nWhen you are ready to cut over, run:\n'
printf '  cp %s %s\n' "$ENV_FILE" "$BACKUP_FILE"
printf '  cp %s %s\n' "$CANDIDATE_FILE" "$ENV_FILE"
printf '  sudo docker compose'
for file in $COMPOSE_FILES; do
    printf ' -f %s' "$file"
done
printf ' up -d --no-build identity\n'
printf '  ALICIA_VERIFY_TOKEN_ALGORITHM=RS256 ALICIA_VERIFY_TOKEN_KEY_ID=%s bash deploy/scripts/verify-identity-cloud-routes.sh\n' "$RS256_KEY_ID"
printf '\nRollback command if verification fails:\n'
printf '  cp %s %s\n' "$BACKUP_FILE" "$ENV_FILE"
printf '  sudo docker compose'
for file in $COMPOSE_FILES; do
    printf ' -f %s' "$file"
done
printf ' up -d --no-build identity\n'
printf '  bash deploy/scripts/verify-identity-cloud-routes.sh\n'
