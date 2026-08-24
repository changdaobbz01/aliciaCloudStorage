# This file is intentionally non-executable so the MySQL image sources it
# during first-time volume initialization and exposes docker_process_sql.
if [[ -z "${ALICIA_IDENTITY_MYSQL_DATABASE:-}" ]]; then
    return 0
fi

if [[ "${ALICIA_IDENTITY_MYSQL_DATABASE}" == "${MYSQL_DATABASE:-}" ]]; then
    return 0
fi

if [[ ! "${ALICIA_IDENTITY_MYSQL_DATABASE}" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "[ERROR] ALICIA_IDENTITY_MYSQL_DATABASE must contain only letters, numbers, and underscores." >&2
    exit 1
fi

docker_process_sql <<-EOSQL
CREATE DATABASE IF NOT EXISTS \`${ALICIA_IDENTITY_MYSQL_DATABASE}\`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
EOSQL
