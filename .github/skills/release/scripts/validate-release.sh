#!/usr/bin/env bash
# validate-release.sh — Phase-aware pre-release validation.
#
# Usage:
#   validate-release.sh --phase preflight --module <m> --version <v>
#   validate-release.sh --phase prepare   --module <m> --version <v> [--skip-mvn]
#   validate-release.sh --phase tag       --module <m> --version <v>
#
# Phases:
#   preflight — on `main`, before any release work. Verifies repo state,
#               origin URL, branch, working tree, sync with origin/main,
#               and CHANGELOG seed. Requires network (uses `git fetch` and
#               `git ls-remote`).
#   prepare   — on a `release/...` branch after edits. Verifies pom version,
#               CHANGELOG entries, and (unless --skip-mvn) runs
#               `mvn dependency:list` to catch internal SNAPSHOT deps.
#               Requires JDK + Maven + network.
#   tag       — on `main` after PR merge, immediately before pushing the tag.
#               Re-verifies origin URL, branch, working tree, HEAD==origin/main,
#               and pom version. Requires network.
set -euo pipefail

PHASE=""
MODULE=""
VERSION=""
SKIP_MVN="false"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --phase)    PHASE="$2"; shift 2 ;;
        --module)   MODULE="$2"; shift 2 ;;
        --version)  VERSION="$2"; shift 2 ;;
        --skip-mvn) SKIP_MVN="true"; shift ;;
        *) echo "Unknown argument: $1" >&2; exit 1 ;;
    esac
done

if [[ -z "$PHASE" || -z "$MODULE" || -z "$VERSION" ]]; then
    cat >&2 <<EOF
Usage: validate-release.sh --phase <preflight|prepare|tag> --module <name> --version <version>
EOF
    exit 1
fi

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
ERRORS=0

pass() { echo "  ✅ $1"; }
fail() { echo "  ❌ $1"; ERRORS=$((ERRORS + 1)); }
note() { echo "  ℹ️  $1"; }

VALID_MODULES=(
    "spring-ai-azure-cosmos-db-store"
    "spring-ai-autoconfigure-vector-store-azure-cosmos-db"
    "spring-ai-model-chat-memory-repository-cosmos-db"
    "spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db"
)

is_valid_module() {
    local m="$1"
    for v in "${VALID_MODULES[@]}"; do
        [[ "$v" == "$m" ]] && return 0
    done
    return 1
}

read_module_version() {
    local pom="$1"
    awk '
        /<\/project>/ { exit }
        in_skip {
            if (/<\/(parent|dependency|dependencyManagement|build|plugin)>/) {
                in_skip = 0
            }
            next
        }
        /<(parent|dependency|dependencyManagement|build|plugin)>/ {
            in_skip = 1
            next
        }
        /<version>[^<]+<\/version>/ {
            line = $0
            sub(/^.*<version>/, "", line)
            sub(/<\/version>.*$/, "", line)
            print line
            exit
        }
    ' "$pom"
}

read_property() {
    local pom="$1" prop="$2"
    sed -n "s/.*<${prop}>\([^<]*\)<\/${prop}>.*/\1/p" "$pom" | head -1
}

# Strict origin URL check. Accepts only the canonical AzureCosmosDB/spring-ai
# repository — not forks, mirrors, or look-alike names like
# `AzureCosmosDB/spring-ai-test`. Supports both SSH and HTTPS clone forms with
# an optional `.git` suffix and an optional trailing slash.
check_origin_url() {
    local url
    url=$(git remote get-url origin 2>/dev/null || echo "")
    local canonical_re='^(git@github\.com:|https://github\.com/)AzureCosmosDB/spring-ai(\.git)?/?$'
    if [[ "$url" =~ $canonical_re ]]; then
        pass "origin points at AzureCosmosDB/spring-ai ($url)"
    else
        fail "origin URL '$url' is not the canonical AzureCosmosDB/spring-ai repo. Expected one of: git@github.com:AzureCosmosDB/spring-ai(.git) or https://github.com/AzureCosmosDB/spring-ai(.git)."
    fi
}

SEMVER_REGEX='^[0-9]+\.[0-9]+\.[0-9]+(-beta\.[0-9]+)?$'
TAG="${MODULE}-v${VERSION}"
POM="$REPO_ROOT/$MODULE/pom.xml"
CHANGELOG="$REPO_ROOT/$MODULE/CHANGELOG.md"

echo "=== Validating release ($PHASE phase): $MODULE v$VERSION ==="
echo ""

# ── Common checks (every phase) ──────────────────────────────────────────────

if is_valid_module "$MODULE"; then
    pass "Module '$MODULE' is in the publishable allowlist"
else
    fail "Module '$MODULE' is NOT in the allowlist. Valid: ${VALID_MODULES[*]}"
fi

if [[ "$VERSION" =~ $SEMVER_REGEX ]]; then
    pass "Version '$VERSION' follows X.Y.Z[-beta.N]"
else
    fail "Version '$VERSION' does not match X.Y.Z[-beta.N]"
fi

# Tag must not exist (preflight + tag); for prepare it's still useful info.
# `git ls-remote` failure is treated as a hard error, NOT silently as
# "tag absent" — see review on PR #37.
LOCAL_TAG=$(git tag -l "$TAG" 2>/dev/null || true)
if [[ -n "$LOCAL_TAG" ]]; then
    fail "Tag '$TAG' already exists locally. Delete with: git tag -d $TAG"
else
    if REMOTE_LS_OUT=$(git ls-remote --tags origin "refs/tags/$TAG" 2>&1); then
        REMOTE_TAG=$(printf '%s\n' "$REMOTE_LS_OUT" | head -1)
        if [[ -n "$REMOTE_TAG" ]]; then
            fail "Tag '$TAG' already exists on remote. Delete with: git push origin :refs/tags/$TAG"
        else
            pass "Tag '$TAG' does not exist locally or on remote"
        fi
    else
        fail "git ls-remote against origin failed; cannot verify whether tag '$TAG' already exists. Check network/credentials. Output: $REMOTE_LS_OUT"
    fi
fi

case "$PHASE" in

    # ─────────────────────────────────────────────────────────────────────
    preflight)
        # Origin must point at the canonical repo (exact match, not substring).
        check_origin_url

        CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "?")
        if [[ "$CURRENT_BRANCH" == "main" ]]; then
            pass "On 'main' branch"
        else
            fail "Not on 'main' (currently '$CURRENT_BRANCH'). Run: git checkout main"
        fi

        if git diff --quiet && git diff --staged --quiet; then
            pass "Working tree is clean"
        else
            fail "Working tree has uncommitted changes. Commit or stash them first."
        fi

        if git fetch origin main --quiet 2>/dev/null; then
            if [[ "$(git rev-parse HEAD 2>/dev/null)" == "$(git rev-parse origin/main 2>/dev/null)" ]]; then
                pass "Local main is up to date with origin/main"
            else
                BEHIND=$(git rev-list --count HEAD..origin/main 2>/dev/null || echo "?")
                fail "Local main is $BEHIND commit(s) behind origin/main. Run: git pull origin main"
            fi
        else
            fail "git fetch origin main failed; cannot verify local main is up to date. Check network/credentials. (preflight phase requires network access.)"
        fi

        # CHANGELOG must at least exist with [Unreleased]
        if [[ -f "$CHANGELOG" ]]; then
            if grep -q '^## \[Unreleased\]' "$CHANGELOG"; then
                pass "CHANGELOG.md has an [Unreleased] section"
            else
                fail "CHANGELOG.md exists but has no '## [Unreleased]' section"
            fi
        else
            fail "CHANGELOG.md not found at $CHANGELOG"
        fi
        ;;

    # ─────────────────────────────────────────────────────────────────────
    prepare)
        # We expect to be on a release/... branch, NOT main.
        CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "?")
        if [[ "$CURRENT_BRANCH" == release/* ]]; then
            pass "On a release branch ($CURRENT_BRANCH)"
        elif [[ "$CURRENT_BRANCH" == "main" ]]; then
            fail "On 'main' branch — prepare-phase changes belong on a 'release/*' branch"
        else
            note "On branch '$CURRENT_BRANCH' (expected 'release/*'); continuing"
        fi

        # Module pom version equals target
        if [[ -f "$POM" ]]; then
            POM_VERSION=$(read_module_version "$POM")
            if [[ "$POM_VERSION" == "$VERSION" ]]; then
                pass "$MODULE/pom.xml <version> = '$POM_VERSION' matches target"
            else
                fail "$MODULE/pom.xml <version> = '$POM_VERSION' does NOT match '$VERSION'. Update <version> in $MODULE/pom.xml."
            fi
        else
            fail "$MODULE/pom.xml not found"
        fi

        # CHANGELOG must have a section for this version with at least one bullet
        # under a Keep-a-Changelog subsection.
        if [[ -f "$CHANGELOG" ]]; then
            if ! grep -q "^## \[${VERSION}\]" "$CHANGELOG"; then
                fail "CHANGELOG.md has no '## [$VERSION]' section. Rename '## [Unreleased]' → '## [$VERSION] — <date>'."
            else
                # Slice out from "## [VERSION]" up to (but not including) the next "## [".
                SECTION=$(awk -v v="$VERSION" '
                    BEGIN { found = 0 }
                    /^## \[/ {
                        if (found) exit
                        if ($0 ~ "^## \\[" v "\\]") { found = 1; next }
                    }
                    found { print }
                ' "$CHANGELOG")
                # At least one "### " subsection
                SUBS=$(echo "$SECTION" | grep -c '^### ' || true)
                # At least one bullet (- or *) line
                BULLETS=$(echo "$SECTION" | grep -cE '^[[:space:]]*[-*][[:space:]]' || true)
                if [[ "$SUBS" -gt 0 && "$BULLETS" -gt 0 ]]; then
                    pass "CHANGELOG.md has [$VERSION] with $SUBS subsection(s) and $BULLETS bullet(s)"
                elif [[ "$SUBS" -gt 0 ]]; then
                    fail "CHANGELOG.md [$VERSION] section has subsections but no bullet entries. Add at least one '- ...' under a subsection."
                else
                    fail "CHANGELOG.md [$VERSION] section has no '### Added/Changed/Fixed/...' subsections."
                fi
            fi
        else
            fail "CHANGELOG.md not found at $CHANGELOG"
        fi

        # Autoconfigure-specific: inter-module version property must be a
        # release version (non-SNAPSHOT, valid semver). Two autoconfigure
        # modules, each pinning a different core via a different property.
        AUTOCONF_PROP=""
        AUTOCONF_CORE=""
        case "$MODULE" in
            spring-ai-autoconfigure-vector-store-azure-cosmos-db)
                AUTOCONF_PROP="spring-ai-cosmos-db-store.version"
                AUTOCONF_CORE="spring-ai-azure-cosmos-db-store"
                ;;
            spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db)
                AUTOCONF_PROP="spring-ai-cosmos-chat-memory.version"
                AUTOCONF_CORE="spring-ai-model-chat-memory-repository-cosmos-db"
                ;;
        esac
        if [[ -n "$AUTOCONF_PROP" ]]; then
            CORE_DEP=$(read_property "$POM" "$AUTOCONF_PROP")
            if [[ -z "$CORE_DEP" ]]; then
                fail "<$AUTOCONF_PROP> property not found in $MODULE/pom.xml"
            elif [[ "$CORE_DEP" == *"-SNAPSHOT" ]]; then
                fail "Autoconfigure pins $AUTOCONF_CORE at SNAPSHOT ($CORE_DEP). Bump <$AUTOCONF_PROP> to a released version of $AUTOCONF_CORE before tagging."
            elif [[ ! "$CORE_DEP" =~ $SEMVER_REGEX ]]; then
                fail "<$AUTOCONF_PROP> ($CORE_DEP) is not a valid release version (X.Y.Z[-beta.N])"
            else
                pass "Autoconfigure pins $AUTOCONF_CORE at released version $CORE_DEP"
            fi
        fi

        # Maven dependency:list — internal SNAPSHOT check (mirrors release.yml).
        # This requires JDK + Maven + network and may be slow; allow --skip-mvn
        # to bypass for fast iteration. Final authority is release.yml itself.
        if [[ "$SKIP_MVN" == "true" ]]; then
            note "Skipping Maven dependency:list check (--skip-mvn). release.yml will enforce this on tag push."
        else
            MVN_CMD="mvn"
            if [[ -x "$REPO_ROOT/mvnw" ]]; then
                MVN_CMD="$REPO_ROOT/mvnw"
            fi
            if ! command -v "$MVN_CMD" >/dev/null 2>&1 && [[ "$MVN_CMD" == "mvn" ]]; then
                fail "Neither ./mvnw nor mvn is on PATH. Cannot run internal SNAPSHOT check. Install Maven (or rely on ./mvnw) and retry, or re-run with --skip-mvn to defer this check to release.yml."
            else
                echo "  ⏳ Running '$MVN_CMD -pl $MODULE -am install -DskipTests' to seed local repo (slow on first run)..."
                if (cd "$REPO_ROOT" && "$MVN_CMD" -pl "$MODULE" -am install -DskipTests -B -q) 2>/dev/null; then
                    if DEP_LIST_OUT=$(cd "$REPO_ROOT" && "$MVN_CMD" dependency:list -pl "$MODULE" -DincludeScope=runtime -B -q -DoutputFile=/dev/stdout 2>&1); then
                        INTERNAL_SNAPSHOTS=$(printf '%s\n' "$DEP_LIST_OUT" \
                            | grep -F 'com.azure.spring.ai' \
                            | grep -F -- '-SNAPSHOT' || true)
                        if [[ -z "$INTERNAL_SNAPSHOTS" ]]; then
                            pass "No internal com.azure.spring.ai SNAPSHOT dependencies"
                        else
                            fail "Module depends on internal SNAPSHOT artifacts:"
                            printf '%s\n' "$INTERNAL_SNAPSHOTS" | sed 's/^/      /'
                        fi
                    else
                        fail "mvn dependency:list failed; internal-SNAPSHOT check did NOT run. Fix the failure or re-run with --skip-mvn to defer this check to release.yml. Last lines of output:"
                        printf '%s\n' "$DEP_LIST_OUT" | tail -10 | sed 's/^/      /'
                    fi
                else
                    fail "Maven build (mvn -pl $MODULE -am install -DskipTests) failed; internal-SNAPSHOT check did NOT run. Fix the build or re-run with --skip-mvn to defer this check to release.yml."
                fi
            fi
        fi
        ;;

    # ─────────────────────────────────────────────────────────────────────
    tag)
        # Origin must point at the canonical repo. Otherwise the tag would be
        # pushed to a fork or unrelated repo and never trigger release.yml.
        check_origin_url

        CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "?")
        if [[ "$CURRENT_BRANCH" == "main" ]]; then
            pass "On 'main' branch"
        else
            fail "Not on 'main' (currently '$CURRENT_BRANCH'). Tag must be pushed from main."
        fi

        if git diff --quiet && git diff --staged --quiet; then
            pass "Working tree is clean"
        else
            fail "Working tree has uncommitted changes."
        fi

        if git fetch origin main --quiet 2>/dev/null; then
            LOCAL_HEAD=$(git rev-parse HEAD 2>/dev/null || echo "?")
            REMOTE_HEAD=$(git rev-parse origin/main 2>/dev/null || echo "?")
            if [[ "$LOCAL_HEAD" == "$REMOTE_HEAD" ]]; then
                pass "HEAD ($LOCAL_HEAD) equals origin/main"
            else
                fail "HEAD ($LOCAL_HEAD) does NOT equal origin/main ($REMOTE_HEAD). Run: git pull origin main"
            fi
        else
            fail "git fetch origin main failed; cannot verify HEAD matches origin/main. Check network/credentials. (tag phase requires network access — without it the wrong commit could be tagged.)"
        fi

        # Module pom version on main equals target
        if [[ -f "$POM" ]]; then
            POM_VERSION=$(read_module_version "$POM")
            if [[ "$POM_VERSION" == "$VERSION" ]]; then
                pass "$MODULE/pom.xml on main = '$POM_VERSION' matches tag version"
            else
                fail "$MODULE/pom.xml on main = '$POM_VERSION' does NOT match tag version '$VERSION'. The release PR may not be merged yet."
            fi
        else
            fail "$MODULE/pom.xml not found on main"
        fi
        ;;

    *)
        echo "Unknown phase: $PHASE (expected preflight | prepare | tag)" >&2
        exit 1
        ;;
esac

echo ""
if [[ "$ERRORS" -eq 0 ]]; then
    echo "✅ All $PHASE checks passed for $MODULE v$VERSION"
    exit 0
else
    echo "❌ $ERRORS $PHASE check(s) failed for $MODULE v$VERSION"
    exit 1
fi
