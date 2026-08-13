#!/usr/bin/env bash
set -Eeuo pipefail

PROJECT_DIR="${1:-$HOME/aliciaCloudStorage}"
COMPOSE=(sudo docker compose -f compose.yaml -f compose.https.yaml)
PUBLIC_RAG_HEALTH_URL="${ALICIA_PUBLIC_RAG_HEALTH_URL:-https://windwindwind-alicia.cn/rag/api/health}"

rag_is_ready() {
    local health_url="$1"
    local payload
    payload="$(curl -fsS --max-time 10 "$health_url")" || return 1
    grep -Eq '"status"[[:space:]]*:[[:space:]]*"ok"' <<<"$payload" &&
        grep -Eq '"deepseekConfigured"[[:space:]]*:[[:space:]]*true' <<<"$payload" &&
        grep -Eq '"storageApiConfigured"[[:space:]]*:[[:space:]]*true' <<<"$payload"
}

cd "$PROJECT_DIR"

if [[ ! -f .env ]]; then
    echo "Missing $PROJECT_DIR/.env" >&2
    exit 1
fi

if ! grep -Eq '^DEEPSEEK_API_KEY=.+$' .env; then
    echo "DEEPSEEK_API_KEY is missing from $PROJECT_DIR/.env" >&2
    exit 1
fi

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    echo "Tracked server files have local changes; refusing to overwrite them." >&2
    git status --short --untracked-files=no
    exit 1
fi

git pull --ff-only origin main
"${COMPOSE[@]}" up -d --build rag frontend

for attempt in {1..30}; do
    if rag_is_ready http://127.0.0.1:8091/api/health; then
        break
    fi
    if [[ "$attempt" -eq 30 ]]; then
        "${COMPOSE[@]}" logs --tail=120 rag
        exit 1
    fi
    sleep 2
done

rag_is_ready "$PUBLIC_RAG_HEALTH_URL"
"${COMPOSE[@]}" ps

echo "RAG deployment verified: $(git rev-parse --short HEAD)"
echo "Public health: $PUBLIC_RAG_HEALTH_URL"
