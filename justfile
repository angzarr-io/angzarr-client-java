# Java client library commands
#
# Container Overlay Pattern:
# --------------------------
# This justfile uses an overlay pattern for container execution:
#
# 1. `justfile` (this file) - runs on the host, delegates to container
# 2. `justfile.container` - mounted over this file inside the container
#
# When running outside a devcontainer:
#   - Uses pre-built angzarr-java image from ghcr.io/angzarr
#   - Docker mounts justfile.container as /workspace/justfile
#
# When running inside a devcontainer (DEVCONTAINER=true):
#   - Commands execute directly via `just <target>`
#   - No container nesting

set shell := ["bash", "-c"]

ROOT := `git rev-parse --show-toplevel`
IMAGE := "ghcr.io/angzarr-io/angzarr-java:latest"

# Run just target in container (or directly if already in devcontainer)
[private]
_container +ARGS:
    #!/usr/bin/env bash
    if [ "${DEVCONTAINER:-}" = "true" ]; then
        just {{ARGS}}
    else
        # INFRA-2: rootless docker maps in-container uid 0 to host user via
        # subuid; the default container user (uid 1000) maps to a different
        # subuid that cannot write to the bind-mounted /workspace/.gradle/.
        # Rootful uses the direct uid match. See feedback_docker_rootless.
        if docker info --format '{{{{.SecurityOptions}}}}' 2>/dev/null | grep -q rootless; then
            USER_FLAG="-u 0:0"
        else
            USER_FLAG="-u $(id -u):$(id -g)"
        fi
        docker run --rm --network=host \
            $USER_FLAG \
            -v "{{ROOT}}:/workspace" \
            -v "{{ROOT}}/justfile.container:/workspace/justfile:ro" \
            -w /workspace \
            -e DEVCONTAINER=true \
            {{IMAGE}} just {{ARGS}}
    fi

# Run a mutation-testing target with the workspace mounted READ-ONLY.
#
# WHY:
#   pitest mutates compiled .class files but the surrounding gradle build can
#   touch source-adjacent paths (build/, .gradle/, generated proto). If the
#   workspace is bind-mounted RW (as `_container` does) and the container dies
#   mid-run, any leaked artifacts persist on the host. This helper closes that
#   hole: source is mounted at /src:ro, a copy lands in /work inside the
#   container's WRITABLE OVERLAY LAYER, and `--rm` destroys the overlay (and
#   the copy) on every exit.
#
# WHAT TOUCHES THE HOST:
#   - {{ROOT}}/.mutants-cache/gradle — Gradle user home (dep cache, wrapper
#     dists). NEVER contains mutated source. Gitignored.
#   - {{ROOT}}/mutation-reports/ — pitest HTML/XML reports copied out at end
#     of a successful run.
#
# WHAT NEVER TOUCHES THE HOST:
#   - The build/ tree under any module (lives in /work overlay, --rm wipes).
#   - pitest's intermediate working dirs.
#
# NOTE: --no-daemon is forced. The container is ephemeral; a daemon would die
# with it (or worse, get orphaned in a long-lived dev container).
[private]
_container-ephemeral +ARGS:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ "${DEVCONTAINER:-}" = "true" ]; then
        just {{ARGS}}
        exit 0
    fi
    mkdir -p "{{ROOT}}/mutation-reports" \
             "{{ROOT}}/.mutants-cache/gradle"
    docker run --rm --network=host \
        -v "{{ROOT}}:/src:ro,Z" \
        -v "{{ROOT}}/mutation-reports:/out:Z" \
        -v "{{ROOT}}/.mutants-cache/gradle:/gradle-home:Z" \
        -v "{{ROOT}}/justfile.container:/etc/angzarr-justfile:ro" \
        -e GRADLE_USER_HOME=/gradle-home \
        -e GRADLE_OPTS=-Dorg.gradle.daemon=false \
        -e DEVCONTAINER=true \
        -w /work \
        {{IMAGE}} bash -eu -o pipefail -c '
            echo "[ephemeral] copying /src -> /work (container overlay)"
            mkdir -p /work
            tar -C /src \
                --exclude=./.gradle \
                --exclude=./build \
                --exclude=./.mutants-cache \
                --exclude=./mutation-reports \
                -cf - . \
                | tar -C /work -xf -
            # Mount container-side justfile into the copy so `just` finds it
            cp /etc/angzarr-justfile /work/justfile
            cd /work
            just {{ARGS}}
            # Persist pitest reports back to host. Multi-module safe.
            find /work -path "*/build/reports/pitest" -type d -exec sh -c "
                module=\$(echo \"\$1\" | sed -e \"s|^/work/||\" -e \"s|/build/reports/pitest$||\")
                dest=\"/out/\${module:-root}\"
                mkdir -p \"\$dest\"
                cp -r \"\$1\"/. \"\$dest\"/
                echo \"[ephemeral] copied pitest report: \$module -> /out/\${module:-root}\"
            " _ {} \;
        '

default:
    @just --list

# =============================================================================
# Proto generation — cross-language model (project_proto_generation_model)
# =============================================================================
# `.proto` sources live in the angzarr-project submodule. Generated Java
# bindings are NEVER committed (see .gitignore: proto/**/*.java and
# proto/build/**). They are regenerated:
#   1. on `post-checkout` / `post-merge` via lefthook (covers fresh clones,
#      branch switches, submodule bumps)
#   2. transparently as a recipe dependency of `build`, `test`, `fmt`, etc.
#      The recipe is idempotent — mtime guard skips when bindings are newer
#      than the newest .proto source.
#
# Runs in the same devcontainer image used for build/test/mutation so the
# protoc-gen-java + protoc-gen-grpc-java toolchain is fixed (no host fallback).
# Rootless docker requires `-u 0:0` per feedback_docker_rootless.
#
# Build-tool integration (the `com.google.protobuf` gradle plugin) is the
# EXECUTOR but NOT the trigger: this recipe explicitly invokes
# `gradle :proto:generateProto`. Plain `gradle build` consumes the pre-emitted
# proto/build/generated/source/proto/main/{java,grpc}/*.java sources. Keeping
# the regen orchestration in `just` matches the 6-lang ecosystem pattern.

PROTO_SRC_DIR := ROOT + "/angzarr-project/proto"
PROTO_OUT_DIR := ROOT + "/proto/build/generated/source/proto/main"

# Public entry point. Idempotent: returns immediately if bindings are
# fresher than the newest .proto source.
generate-proto:
    #!/usr/bin/env bash
    set -euo pipefail
    src_dir="{{PROTO_SRC_DIR}}"
    out_dir="{{PROTO_OUT_DIR}}"
    if [ ! -d "$src_dir" ]; then
        echo "[generate-proto] $src_dir missing — is the angzarr-project submodule initialized?" >&2
        exit 1
    fi
    # Staleness check: regenerate if any .proto file is newer than the
    # OLDEST generated binding, or if no bindings exist yet.
    # Catches "submodule bumped" and "fresh clone" — the hot paths driving
    # the lefthook trigger. Does NOT catch manual deletion of one binding
    # while others remain fresh; use `just generate-proto-force` for that.
    #
    # OLDEST (matches Python/Rust) — the Java generated tree lives entirely
    # under proto/build/generated/ which gradle wipes-and-regens on each
    # protoc invocation, so no orphan-stale leftovers exist. (Go's NEWEST
    # adaptation is unnecessary here.)
    newest_proto=$(find "$src_dir" -name '*.proto' -printf '%T@\n' 2>/dev/null \
                    | sort -n | tail -1)
    # Guard the find for out_dir — on clean state the gradle build dir does
    # not yet exist, and `find $missing` exits non-zero which trips pipefail.
    # Use `awk 'NR==1'` (reads all input) not `head -1` (closes pipe early):
    # with hundreds of generated .java files under `proto/build/generated/`,
    # head -1 SIGPIPEs find under `set -o pipefail`.
    if [ -d "$out_dir" ]; then
        oldest_pb=$(find "$out_dir" -name '*.java' -printf '%T@\n' 2>/dev/null \
                        | sort -n | awk 'NR==1')
    else
        oldest_pb=""
    fi
    if [ -n "$newest_proto" ] && [ -n "$oldest_pb" ] \
        && awk -v p="$newest_proto" -v b="$oldest_pb" 'BEGIN{exit !(b>p)}'; then
        echo "[generate-proto] bindings up-to-date, skipping (use 'just generate-proto-force' to override)"
        exit 0
    fi
    just generate-proto-force

# Always regenerate, ignoring mtimes. Invoked by `generate-proto` when stale
# and exposed directly for users who want to force a rebuild.
generate-proto-force:
    #!/usr/bin/env bash
    set -euo pipefail
    if [ "${DEVCONTAINER:-}" = "true" ]; then
        # Inside the devcontainer image already — run directly.
        just --justfile "{{ROOT}}/justfile.container" generate-proto-force
        exit 0
    fi
    # Rootless docker: -u 0:0 maps to host user via subuid; writes to the
    # bind-mount land owned by the host user. Rootful: direct uid match.
    # See feedback_docker_rootless.
    if docker info --format '{{{{.SecurityOptions}}}}' 2>/dev/null | grep -q rootless; then
        USER_FLAG="-u 0:0"
    else
        USER_FLAG="-u $(id -u):$(id -g)"
    fi
    docker run --rm --network=host \
        $USER_FLAG \
        -v "{{ROOT}}:/workspace" \
        -v "{{ROOT}}/justfile.container:/workspace/justfile:ro" \
        -w /workspace \
        -e DEVCONTAINER=true \
        {{IMAGE}} just generate-proto-force

# Legacy alias — kept so existing recipe-deps and muscle memory keep working.
proto: generate-proto

build: generate-proto
    just _container build

test: generate-proto
    just _container test

# Start gRPC test server for unified Rust harness testing
serve: generate-proto
    just _container serve

coverage: generate-proto
    just _container coverage

mutation-test: generate-proto
    just _container-ephemeral mutation-test

# Purge the local mutation build cache (.mutants-cache/) — Gradle deps and
# wrapper dists only; never holds mutated source.
mutants-purge-cache:
    rm -rf "{{ROOT}}/.mutants-cache"
    @echo "Removed {{ROOT}}/.mutants-cache"

publish: generate-proto
    just _container publish

# Publish to local Maven repository for testing
publish-local: generate-proto
    just _container publish-local

# Wipe all generated/build artifacts. Idempotent. Cross-language convention —
# see client-go/main/justfile clean and feedback_docker_rootless. Safe to run
# repeatedly; missing paths are not errors.
#
# What this removes:
#   - proto/build (generated Java bindings — regenerated by generate-proto)
#   - build/{tmp,libs,classes,reports,distributions,generated,resources}
#     (gradle root-module outputs; preserves build/images/Containerfile)
#   - client/build (gradle client-module outputs)
#   - .gradle, client/.gradle, .gradle.stuck.* (gradle caches)
#   - .mutants-cache, mutation-reports (PIT mutation testing artifacts)
#   - angzarr/ (stray sub-workspace clones, if any)
clean:
    @echo "[clean] wiping generated proto bindings…"
    rm -rf "{{ROOT}}/proto/build"
    find "{{ROOT}}/proto" -name '*.java' -not -path '*/build/*' -delete 2>/dev/null || true
    @echo "[clean] wiping gradle build dirs…"
    rm -rf "{{ROOT}}/build/tmp" \
           "{{ROOT}}/build/libs" \
           "{{ROOT}}/build/classes" \
           "{{ROOT}}/build/reports" \
           "{{ROOT}}/build/distributions" \
           "{{ROOT}}/build/generated" \
           "{{ROOT}}/build/resources" \
           "{{ROOT}}/client/build"
    @echo "[clean] wiping gradle caches…"
    rm -rf "{{ROOT}}/.gradle" \
           "{{ROOT}}/client/.gradle"
    find "{{ROOT}}" -maxdepth 1 -name '.gradle.stuck.*' -type d -exec rm -rf {} + 2>/dev/null || true
    @echo "[clean] wiping mutation caches + reports…"
    rm -rf "{{ROOT}}/.mutants-cache" \
           "{{ROOT}}/mutation-reports" \
           "{{ROOT}}/mutants-reports"
    @echo "[clean] wiping stray sub-workspace clones…"
    rm -rf "{{ROOT}}/angzarr"
    @echo "[clean] complete"

# Check formatting
fmt: generate-proto
    just _container fmt

# Auto-format code
fmt-fix: generate-proto
    just _container fmt-fix

# Cross-language alias — `just check` runs lint + fmt-check.
check: fmt

# Cross-language alias — `just lint` placeholder (Java uses fmt-check only).
lint: fmt
