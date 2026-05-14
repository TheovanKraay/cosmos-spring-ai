#!/usr/bin/env bash
# list-modules.sh — List publishable modules with their current pom.xml versions.
# Run from the repository root.
set -euo pipefail

REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"

VALID_MODULES=(
    "spring-ai-azure-cosmos-db-store"
    "spring-ai-autoconfigure-vector-store-azure-cosmos-db"
    "spring-ai-model-chat-memory-repository-cosmos-db"
    "spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db"
)

# Extract the project's own <version> from a module's pom.xml.
# Strategy: find the first <version> inside the top-level <project> block,
# skipping <parent> blocks (this repo has no <parent>, but be defensive) and
# any <version> inside <dependency>, <dependencyManagement>, <plugin>, etc.
# We rely on the convention that the module's own <version> is among the
# first 30 lines of its pom.xml — true for every module here.
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

# Read the inter-module dep property (autoconfigure → store) so the user
# can spot drift between the property and the store module's actual version.
read_property() {
    local pom="$1" prop="$2"
    sed -n "s/.*<${prop}>\([^<]*\)<\/${prop}>.*/\1/p" "$pom" | head -1
}

echo "=== Publishable Modules ==="
echo ""
printf "%-55s %-20s %s\n" "Module" "pom.xml <version>" "Suggested release tag"
printf "%-55s %-20s %s\n" "------" "-----------------" "---------------------"

for MODULE in "${VALID_MODULES[@]}"; do
    POM="$REPO_ROOT/$MODULE/pom.xml"
    if [[ ! -f "$POM" ]]; then
        printf "%-55s %-20s %s\n" "$MODULE" "POM NOT FOUND" "n/a"
        continue
    fi
    VERSION=$(read_module_version "$POM")
    VERSION="${VERSION:-?}"
    # Strip -SNAPSHOT for the suggested tag (the release version is the
    # SNAPSHOT base).
    SUGGESTED="${VERSION%-SNAPSHOT}"
    if [[ "$VERSION" == *"-SNAPSHOT" ]]; then
        TAG_HINT="${MODULE}-v${SUGGESTED}   (drop -SNAPSHOT to release)"
    else
        TAG_HINT="${MODULE}-v${VERSION}"
    fi
    printf "%-55s %-20s %s\n" "$MODULE" "$VERSION" "$TAG_HINT"
done

# Inter-module dependency properties (autoconfigure → core)
echo ""
echo "=== Inter-module dependencies (autoconfigure → core) ==="

VECTOR_AUTOCONF_POM="$REPO_ROOT/spring-ai-autoconfigure-vector-store-azure-cosmos-db/pom.xml"
if [[ -f "$VECTOR_AUTOCONF_POM" ]]; then
    STORE_DEP=$(read_property "$VECTOR_AUTOCONF_POM" "spring-ai-cosmos-db-store.version")
    echo "  spring-ai-autoconfigure-vector-store-azure-cosmos-db expects"
    echo "    spring-ai-azure-cosmos-db-store version: ${STORE_DEP:-NOT FOUND}"
fi

CHAT_AUTOCONF_POM="$REPO_ROOT/spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db/pom.xml"
if [[ -f "$CHAT_AUTOCONF_POM" ]]; then
    CHAT_DEP=$(read_property "$CHAT_AUTOCONF_POM" "spring-ai-cosmos-chat-memory.version")
    echo "  spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db expects"
    echo "    spring-ai-model-chat-memory-repository-cosmos-db version: ${CHAT_DEP:-NOT FOUND}"
fi

echo ""
echo "=== Dependency / release order ==="
echo "  Vector-store pair (release in order if both in wave):"
echo "    1. spring-ai-azure-cosmos-db-store"
echo "    2. spring-ai-autoconfigure-vector-store-azure-cosmos-db"
echo "  Chat-memory pair (release in order if both in wave):"
echo "    1. spring-ai-model-chat-memory-repository-cosmos-db"
echo "    2. spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db"
echo "  Cross-pair ordering does not matter."
