#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="${ALICIA_BACKEND_BOUNDARY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
DOCKER_IMAGE="${ALICIA_BACKEND_BOUNDARY_DOCKER_IMAGE:-eclipse-temurin:21-jdk}"
USE_DOCKER="${ALICIA_BACKEND_BOUNDARY_USE_DOCKER:-auto}"
MAVEN_MODE=""
MAVEN_COMMAND=()

fail() {
    printf '[FAIL] %s\n' "$1" >&2
    exit 1
}

ok() {
    printf '[OK] %s\n' "$1"
}

docker_cmd() {
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

java_toolchain_available() {
    if [[ -n "${JAVA_HOME:-}" ]]; then
        [[ -x "$JAVA_HOME/bin/java" && -x "$JAVA_HOME/bin/javac" ]]
        return
    fi

    command -v java >/dev/null 2>&1 && command -v javac >/dev/null 2>&1
}

resolve_host_maven_command() {
    if [[ -f "$ROOT_DIR/mvnw" ]]; then
        MAVEN_COMMAND=(bash "$ROOT_DIR/mvnw")
        return 0
    fi

    if command -v mvn >/dev/null 2>&1; then
        MAVEN_COMMAND=(mvn)
        return 0
    fi

    return 1
}

resolve_maven_command() {
    case "$USE_DOCKER" in
        true)
            MAVEN_MODE="docker"
            return
            ;;
        false)
            ;;
        auto|"")
            if ! java_toolchain_available; then
                command -v docker >/dev/null 2>&1 || fail "Java toolchain is unavailable and Docker is not on PATH. Set JAVA_HOME or run with ALICIA_BACKEND_BOUNDARY_USE_DOCKER=true on a Docker host."
                MAVEN_MODE="docker"
                printf '[INFO] Host Java toolchain unavailable; running backend boundary tests in Docker image %s\n' "$DOCKER_IMAGE"
                return
            fi
            ;;
        *)
            fail "Unsupported ALICIA_BACKEND_BOUNDARY_USE_DOCKER value: $USE_DOCKER"
            ;;
    esac

    java_toolchain_available || fail "Java toolchain is unavailable. Fix JAVA_HOME or set ALICIA_BACKEND_BOUNDARY_USE_DOCKER=true."
    resolve_host_maven_command || fail "Maven is required. Expected mvnw in the repository root or mvn on PATH."

    MAVEN_MODE="host"
}

run_step() {
    local label="$1"
    shift

    printf '[RUN] %s\n' "$label"
    "$@"
    ok "$label"
}

first_matching_line() {
    local target="$1"
    local pattern="$2"

    if command -v rg >/dev/null 2>&1; then
        rg -n -m 1 -- "$pattern" "$target" | cut -d: -f1 || true
        return
    fi

    command -v grep >/dev/null 2>&1 || fail "Either ripgrep (rg) or grep is required."
    grep -nEm 1 -- "$pattern" "$target" | cut -d: -f1 || true
}

require_source_match() {
    local target="$1"
    local pattern="$2"
    local message="$3"

    [[ -n "$(first_matching_line "$target" "$pattern")" ]] || fail "$message"
}

require_source_no_match() {
    local target="$1"
    local pattern="$2"
    local message="$3"

    [[ -z "$(first_matching_line "$target" "$pattern")" ]] || fail "$message"
}

require_source_order() {
    local target="$1"
    local before_pattern="$2"
    local after_pattern="$3"
    local message="$4"
    local before_line
    local after_line

    before_line="$(first_matching_line "$target" "$before_pattern")"
    after_line="$(first_matching_line "$target" "$after_pattern")"
    [[ -n "$before_line" && -n "$after_line" && "$before_line" -lt "$after_line" ]] || fail "$message"
}

verify_java_runtime_dockerfile() {
    local module="$1"
    local dockerfile="$ROOT_DIR/$module/Dockerfile"
    local module_list='CloudStorageApi,rag,ragExecution'
    local dependency_pattern="-pl $module_list -Dmaven\\.test\\.skip=true -DincludeScope=runtime dependency:resolve"
    local plugin_pattern="-pl $module_list -Dmaven\\.test\\.skip=true -Dspring-boot\\.repackage\\.skip=true resources:resources compiler:compile jar:jar spring-boot:repackage"
    local test_runner_pattern="-pl $module_list -Dmaven\\.test\\.skip=true surefire:test"
    local source_pattern="^COPY $module/src $module/src[[:space:]]*$"
    local package_pattern="-pl $module -Dmaven\\.test\\.skip=true package"

    [[ -f "$dockerfile" ]] || fail "Missing Java runtime Dockerfile: $dockerfile"
    require_source_match \
        "$dockerfile" \
        "$dependency_pattern" \
        "$module Dockerfile must resolve runtime dependencies in a source-independent layer."
    require_source_match \
        "$dockerfile" \
        "$plugin_pattern" \
        "$module Dockerfile must pre-warm only the production lifecycle plugins."
    require_source_match \
        "$dockerfile" \
        "$test_runner_pattern" \
        "$module Dockerfile must pre-warm the skipped test-runner lifecycle before copying source."
    require_source_match \
        "$dockerfile" \
        'rm -rf CloudStorageApi/target rag/target ragExecution/target' \
        "$module Dockerfile must remove every empty pre-warm build output before copying source."
    require_source_match \
        "$dockerfile" \
        "$package_pattern" \
        "$module Dockerfile production package must skip test compilation."
    require_source_no_match \
        "$dockerfile" \
        '-DskipTests' \
        "$module Dockerfile must use maven.test.skip so production packaging does not compile test sources."
    require_source_no_match \
        "$dockerfile" \
        'dependency:go-offline' \
        "$module Dockerfile must not download unrelated Maven reporting and site plugins."
    require_source_order \
        "$dockerfile" \
        "$dependency_pattern" \
        "$source_pattern" \
        "$module Dockerfile must cache dependencies before copying source."
    require_source_order \
        "$dockerfile" \
        "$test_runner_pattern" \
        "$source_pattern" \
        "$module Dockerfile must cache the test-runner plugin before copying source."
    require_source_order \
        "$dockerfile" \
        "$source_pattern" \
        "$package_pattern" \
        "$module Dockerfile must package only after copying source."
}

verify_java_runtime_dockerfiles() {
    local dockerignore="$ROOT_DIR/.dockerignore"

    [[ -f "$dockerignore" ]] || fail "Missing Docker build context boundary: $dockerignore"
    require_source_match "$dockerignore" '^\.env\*[[:space:]]*$' \
        "Docker build context must exclude environment files and backups."
    require_source_match "$dockerignore" '^deploy/generated[[:space:]]*$' \
        "Docker build context must exclude generated secrets and production backups."
    require_source_match "$dockerignore" '^deploy/certs[[:space:]]*$' \
        "Docker build context must exclude TLS private material."
    require_source_match "$dockerignore" '^backups[[:space:]]*$' \
        "Docker build context must exclude server backup directories."
    require_source_match "$dockerignore" '^deploy/android-app-package[[:space:]]*$' \
        "Docker build context must exclude the separately published Android package."
    require_source_match "$dockerignore" '^phoneApp[[:space:]]*$' \
        "Docker build context must exclude the legacy Android source tree."
    require_source_match "$dockerignore" '^phoneAppAdd[[:space:]]*$' \
        "Docker build context must exclude the active Android source tree."

    verify_java_runtime_dockerfile CloudStorageApi
    verify_java_runtime_dockerfile rag
    verify_java_runtime_dockerfile ragExecution
}

run_maven_boundary_tests() {
    local module="$1"
    local tests="$2"
    local m2_mount=()

    if [[ "$MAVEN_MODE" == "docker" ]]; then
        [[ -f "$ROOT_DIR/mvnw" ]] || fail "Docker backend boundary mode requires mvnw in the repository root."
        if [[ -n "${HOME:-}" && -d "$HOME/.m2" ]]; then
            m2_mount=(-v "$HOME/.m2:/root/.m2")
        fi

        docker_cmd run --rm \
            -v "$ROOT_DIR:/workspace" \
            "${m2_mount[@]}" \
            -w /workspace \
            "$DOCKER_IMAGE" \
            sh -c 'chmod +x ./mvnw && ./mvnw -pl "$1" "-Dtest=$2" test' \
            sh "$module" "$tests"
        return
    fi

    (cd "$ROOT_DIR" && "${MAVEN_COMMAND[@]}" -pl "$module" "-Dtest=$tests" test)
}

run_step "Java runtime Dockerfile dependency-cache boundaries" \
    verify_java_runtime_dockerfiles

resolve_maven_command

run_step "CloudStorageApi legacy and route ownership boundaries" \
    run_maven_boundary_tests "CloudStorageApi" "IdentityRouteBoundaryTest,CloudApiRouteOwnershipTest,CurrentPrincipalTest"

ok "backend API boundary verification complete"
