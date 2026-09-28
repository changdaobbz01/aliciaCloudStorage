#!/usr/bin/env bash
set -euo pipefail

database="${ALICIA_RAG_EXECUTION_MYSQL_DATABASE:-}"
username="${ALICIA_RAG_EXECUTION_DB_USERNAME:-}"
password="${ALICIA_RAG_EXECUTION_DB_PASSWORD:-}"

if [[ ! "${database}" =~ ^[A-Za-z0-9_]{1,64}$ ]]; then
    echo "[ERROR] ALICIA_RAG_EXECUTION_MYSQL_DATABASE must be 1-64 letters, numbers, or underscores." >&2
    exit 1
fi

if [[ "${database}" == "${MYSQL_DATABASE:-}" ]] \
        || [[ -n "${ALICIA_IDENTITY_MYSQL_DATABASE:-}" \
        && "${database}" == "${ALICIA_IDENTITY_MYSQL_DATABASE}" ]]; then
    echo "[ERROR] RAG execution must use a database separate from CloudStorageApi and Identity." >&2
    exit 1
fi

if [[ ! "${username}" =~ ^[A-Za-z0-9_]{1,32}$ ]]; then
    echo "[ERROR] ALICIA_RAG_EXECUTION_DB_USERNAME must be 1-32 letters, numbers, or underscores." >&2
    exit 1
fi

if [[ ! "${password}" =~ ^[A-Za-z0-9_@%+=.,:-]{16,128}$ ]]; then
    echo "[ERROR] ALICIA_RAG_EXECUTION_DB_PASSWORD must be 16-128 safe ASCII characters." >&2
    exit 1
fi

MYSQL_PWD="${MYSQL_ROOT_PASSWORD}" mysql --host=db --user=root <<-EOSQL
CREATE DATABASE IF NOT EXISTS \`${database}\`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${username}'@'%'
    IDENTIFIED BY '${password}';
ALTER USER '${username}'@'%'
    IDENTIFIED BY '${password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
    ON \`${database}\`.*
    TO '${username}'@'%';
FLUSH PRIVILEGES;
EOSQL

echo "[OK] RAG execution database and least-privilege account are ready."
