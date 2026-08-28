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

resolve_maven_command

run_step "CloudStorageApi legacy and route ownership boundaries" \
    run_maven_boundary_tests "CloudStorageApi" "IdentityRouteBoundaryTest,CloudApiRouteOwnershipTest,CurrentPrincipalTest"

run_step "identityApi source, route, and admin access boundaries" \
    run_maven_boundary_tests "identityApi" "IdentitySourceBoundaryTest,IdentityApiRouteOwnershipTest"

ok "backend API boundary verification complete"
