# Release Instructions

This document describes how to release a module (or a wave of modules) of
**`AzureCosmosDB/spring-ai`**. It is the human-facing companion to the
release agent at `.github/agents/release.agent.md`.

## Overview

Each publishable module has its own version and release cadence. Releases
are automated via the **Release** GitHub Actions workflow
(`.github/workflows/release.yml`), triggered by pushing per-module version
tags.

The repository has **no parent POM**. Each module is self-contained and its
release version lives in its own `pom.xml <version>` element. The root
`pom.xml` is a thin aggregator (`packaging=pom`) for `mvn verify` from the
root.

### Publishable modules

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| Vector Store | `spring-ai-azure-cosmos-db-store` | Spring AI vector store backed by Azure Cosmos DB |
| Autoconfigure | `spring-ai-autoconfigure-vector-store-azure-cosmos-db` | Spring Boot autoconfiguration for the vector store |
| Chat Memory | `spring-ai-model-chat-memory-repository-cosmos-db` | Spring AI chat memory repository backed by Azure Cosmos DB |

### Dependency order

```
spring-ai-azure-cosmos-db-store              ← release FIRST if autoconfigure
        ↑                                      is in the wave
        │
        └── spring-ai-autoconfigure-vector-store-azure-cosmos-db
                                            ← pinned via the
                                              <spring-ai-cosmos-db-store.version>
                                              property in autoconfigure's
                                              own pom.xml

spring-ai-model-chat-memory-repository-cosmos-db
                                            ← independent, release any time
```

The autoconfigure module pins the store via a property in its own
`pom.xml`. If you change the store, release it first (or include both in
the same wave with the property bumped to the new released version), then
release the autoconfigure module.

### Development cycle

During development, all modules live in a single Maven reactor and
inter-module dependencies resolve from **source** (the autoconfigure
module's `<spring-ai-cosmos-db-store.version>` is always
`X.Y.Z-SNAPSHOT`, matching the local store module). This means:

- Changes to `spring-ai-azure-cosmos-db-store` are immediately visible to
  the autoconfigure module in the same build.
- Each module's `pom.xml <version>` reflects the **next release version**
  with `-SNAPSHOT` appended.

At **release time**, the dependency ordering matters: if the store is
changing, the wave PR must bump both the store version and autoconfigure's
`<spring-ai-cosmos-db-store.version>` property to a release coordinate.
After release, the post-release PR puts everything back to `-SNAPSHOT` so
the reactor build resolves from source again.

## Release pipeline

```
Push <module>-v<version> tag (or <module>-v<version>-beta.N)
        │
        ▼
┌─────────────────────────┐
│  parse-tag              │  validates the tag against the per-module
│                         │  allowlist + semver regex
└───────────┬─────────────┘
            │
┌───────────▼─────────────┐
│  unit-tests             │  mvn -pl <module> -am verify -DskipITs
│                         │  on the tagged commit, must be reachable
│                         │  from origin/main
└───────────┬─────────────┘
            │
      All gates pass?
            │
┌───────────▼─────────────┐
│   Approval gate         │  manual — production environment reviewers
└───────────┬─────────────┘
            │
┌───────────▼─────────────┐
│   publish               │  • verify pom.xml <version> matches tag
│                         │  • reject com.azure.spring.ai SNAPSHOT deps
│                         │  • warn on external SNAPSHOTs
│                         │  • mvn deploy → local staging
│                         │  • verify pom/jar/sources/javadoc artifacts
│                         │  • create GitHub Release
└─────────────────────────┘
```

> **Maven Central publishing is deferred** (tracked in issue
> [#18](https://github.com/AzureCosmosDB/spring-ai/issues/18)). The
> workflow stops after the GitHub Release. See the
> [Azure Partner Release Pipeline](#azure-partner-release-pipeline-maven-central--future)
> section for the future-state handoff.

## Tag format

```
<module>-v<MAJOR>.<MINOR>.<PATCH>             # stable
<module>-v<MAJOR>.<MINOR>.<PATCH>-beta.<N>    # beta
```

| Pattern | Example |
|---|---|
| Stable | `spring-ai-azure-cosmos-db-store-v1.0.0` |
| Beta | `spring-ai-autoconfigure-vector-store-azure-cosmos-db-v1.0.0-beta.1` |

Valid module names in tags:

- `spring-ai-azure-cosmos-db-store`
- `spring-ai-autoconfigure-vector-store-azure-cosmos-db`
- `spring-ai-model-chat-memory-repository-cosmos-db`

Tags that do **not** match (and will be silently ignored):

- `v1.0.0` — missing module prefix
- `spring-ai-azure-cosmos-db-store-v1.0.0-rc1` — release candidates not
  supported (use `-beta.N`)
- Any module name not in the allowlist above

## The interactive way (recommended): use the release agent

```text
/release module:spring-ai-azure-cosmos-db-store version:1.0.0-beta.1
```

The agent at `.github/agents/release.agent.md` walks you through every
phase: list → preflight → prepare PR → wait for merge → tag → monitor →
post-release SNAPSHOT bump. It runs the skill scripts under
`.github/skills/release/scripts/` and shows you a diff before any commit
or tag push.

## The manual way: step-by-step

If you'd rather drive the release yourself, the steps below mirror what
the agent does.

### 1. Preflight

```bash
git checkout main && git pull origin main

.github/skills/release/scripts/list-modules.sh

.github/skills/release/scripts/validate-release.sh \
    --phase preflight --module <MODULE> --version <VERSION>
```

Fix any failures before continuing. Common ones:

- **Working tree dirty** — commit or stash.
- **Local main behind origin** — `git pull`.
- **Tag already exists** — verify whether a release was already published;
  if not, delete it (`git tag -d <tag> && git push origin :refs/tags/<tag>`).
- **Missing CHANGELOG.md `[Unreleased]`** — add one.

### 2. Prepare the release branch

Single module:

```bash
git checkout -b release/<MODULE>-<VERSION>
```

Multi-module wave:

```bash
git checkout -b release/wave-$(date -u +%Y%m%d)
```

For each module being released:

1. Edit `<MODULE>/pom.xml` — drop `-SNAPSHOT` from `<version>`.
2. **For `spring-ai-autoconfigure-vector-store-azure-cosmos-db` only:** if
   `spring-ai-azure-cosmos-db-store` is in the wave, set
   `<spring-ai-cosmos-db-store.version>` in autoconfigure's `pom.xml` to
   the wave's store release version. Otherwise, verify the existing value
   is a non-SNAPSHOT release version.
3. Edit `<MODULE>/CHANGELOG.md`:
   - Replace `## [Unreleased]` with `## [<VERSION>] — <DATE>`.
   - Insert a new `## [Unreleased]` block above with empty Keep-a-Changelog
     subsections.
4. Stage and commit:

   ```bash
   git add <MODULE>/pom.xml <MODULE>/CHANGELOG.md
   git commit -m "release: prepare <MODULE> v<VERSION>"
   ```

Run prepare-phase validation per module:

```bash
.github/skills/release/scripts/validate-release.sh \
    --phase prepare --module <MODULE> --version <VERSION>
# Pass --skip-mvn for fast iteration; CI enforces the same check.
```

Push the branch and open a PR:

```bash
git push -u origin <BRANCH>
gh pr create --base main --head <BRANCH> \
    --title "release: <SUMMARY>" \
    --body "..."
```

The PR body should list every module being released with its version, link
the CHANGELOG diff, and note the post-merge tag order (store first if in
the wave).

### 3. Wait for PR merge

Tags must point to a commit on `main` that includes the version + changelog
updates. The workflow rejects unreachable tags.

### 4. Tag

```bash
git checkout main && git pull origin main

.github/skills/release/scripts/create-release-tag.sh \
    --module <MODULE> --version <VERSION>
```

The script asserts you're on `main`, `HEAD == origin/main`, the working
tree is clean, the module's `pom.xml <version>` matches the requested
version, and the tag doesn't already exist. It then pushes a single tag
to `origin`.

> **Important:** Push tags **one at a time**. `git push origin <tag1>
> <tag2>` silently delivers only one trigger event to GitHub Actions.

For multi-module waves: tag `spring-ai-azure-cosmos-db-store` first.
**Wait for its release run to complete (and the GitHub Release to appear)
before tagging `spring-ai-autoconfigure-vector-store-azure-cosmos-db`** —
the autoconfigure release will fail otherwise.

### 5. Monitor and approve

```bash
gh run list --workflow=release.yml --limit=5
```

Watch the run. The publish job pauses on the **production** environment;
approve at **Actions → Release → (your run) → Review deployments → Approve**.

After approval, the publish job:

1. Verifies the module's `pom.xml <version>` matches the tag version.
2. Rejects internal `com.azure.spring.ai` SNAPSHOT dependencies.
3. Warns on external SNAPSHOT dependencies (Spring AI itself is on
   `2.0.0-SNAPSHOT` at time of writing).
4. Builds, deploys to a local staging directory, verifies pom + jar +
   sources + javadoc.
5. Uploads `maven-staging-<MODULE>-<VERSION>` as a workflow artifact
   (90-day retention).
6. Creates a **GitHub Release** named `<MODULE> <VERSION>`, marks it
   prerelease for beta versions.

### 6. Post-release SNAPSHOT bump

After every module in the wave has a successful GitHub Release, open a
follow-up PR `chore/post-release-<wave-id>`:

1. Bump each released module's `pom.xml <version>` to the next dev
   SNAPSHOT (typically `X.Y.(Z+1)-SNAPSHOT` after a stable release, or
   `X.Y.Z-beta.(N+1)-SNAPSHOT` after a beta).
2. If `spring-ai-azure-cosmos-db-store` was released in this wave, bump
   `<spring-ai-cosmos-db-store.version>` in autoconfigure's `pom.xml`
   back to the next store SNAPSHOT, so the reactor build resolves from
   source again during ongoing development.

This keeps `main` honest about what version is currently in development
and prevents accidental re-tagging of an already-published version.

## Version policy

Follows [Semantic Versioning](https://semver.org/):

- **Major** (`X.0.0`) — breaking API changes
- **Minor** (`0.Y.0`) — new features, backward compatible
- **Patch** (`0.0.Z`) — bug fixes, backward compatible
- **Beta** (`X.Y.Z-beta.N`) — pre-release, may have breaking changes

Release-candidate versions (`-rc.N`) are **not supported** by the workflow.

## Example: full multi-module release sequence

Releasing the store and the autoconfigure module together at `1.0.0`:

```bash
# 1. Preflight
git checkout main && git pull origin main
.github/skills/release/scripts/list-modules.sh
.github/skills/release/scripts/validate-release.sh \
    --phase preflight --module spring-ai-azure-cosmos-db-store --version 1.0.0
.github/skills/release/scripts/validate-release.sh \
    --phase preflight \
    --module spring-ai-autoconfigure-vector-store-azure-cosmos-db --version 1.0.0

# 2. Prepare wave PR
git checkout -b release/wave-$(date -u +%Y%m%d)
# Edit pom.xml + CHANGELOG.md for both modules.
# In the autoconfigure pom.xml, also set
# <spring-ai-cosmos-db-store.version>1.0.0</spring-ai-cosmos-db-store.version>
git commit -am "release: prepare wave 1.0.0 (store + autoconfigure)"
git push -u origin HEAD
gh pr create --base main --title "release: wave 1.0.0" --body "..."

# 3. (Wait for PR review and merge.)

# 4. Tag store first
git checkout main && git pull origin main
.github/skills/release/scripts/create-release-tag.sh \
    --module spring-ai-azure-cosmos-db-store --version 1.0.0
# (Watch the run; approve in production env; wait for the GitHub Release.)

# 5. Tag autoconfigure
.github/skills/release/scripts/create-release-tag.sh \
    --module spring-ai-autoconfigure-vector-store-azure-cosmos-db --version 1.0.0
# (Watch, approve, GitHub Release.)

# 6. Post-release SNAPSHOT bump PR
git checkout -b chore/post-release-wave-$(date -u +%Y%m%d)
# Bump both pom.xml <version> elements to 1.0.1-SNAPSHOT
# Bump <spring-ai-cosmos-db-store.version> in autoconfigure back to 1.0.1-SNAPSHOT
git commit -am "chore: post-release SNAPSHOT bump"
git push -u origin HEAD
gh pr create --base main --title "chore: post-release SNAPSHOT bump (wave 1.0.0)" --body "..."
```

## Troubleshooting

### "Version mismatch" in the publish job

The module's `pom.xml <version>` doesn't match the tag. Update the
`<version>` element in `<MODULE>/pom.xml`, merge to `main`, delete the old
tag, and re-tag.

### "Reject internal SNAPSHOT dependencies"

The autoconfigure module was tagged while
`<spring-ai-cosmos-db-store.version>` was still a SNAPSHOT. Open a
follow-up PR that bumps the property to the released store version, merge,
delete the failed tag, re-tag.

### External SNAPSHOT warnings

Allowed for GitHub Releases (consumers must be aware that snapshots are
mutable). **Will block Maven Central publishing** when issue #18 lands —
resolve external SNAPSHOTs to GA / milestone versions before triggering
the partner pipeline.

### Pipeline didn't trigger

- Verify the tag matches one of the explicit allowlisted patterns in
  `.github/workflows/release.yml`. The workflow does **not** match
  wildcards — each module has its own line in the `on: push: tags:` list.
- Check that the tag points to a commit on `main`.
- If you pushed multiple tags in one `git push`, GitHub silently delivers
  only one trigger event. Re-push each tag with a separate command.

### Approval is stuck

Go to **Actions → Release → (your run)**. If the publish job is
"Waiting for review", confirm you're listed as a reviewer in
**Settings → Environments → production**.

### Maven validation failures during local prepare-phase check

The `--phase prepare` validation runs `mvn dependency:list` to mirror the
workflow's internal-SNAPSHOT check. It requires JDK 17, Maven (or
`./mvnw`), and network. If unavailable locally, pass `--skip-mvn`; the
workflow enforces the same check on tag push.

## Published artifacts

Each module release produces a GitHub Release with:

| File | Contents |
|------|----------|
| `<artifactId>-<version>.jar` | Compiled classes |
| `<artifactId>-<version>-sources.jar` | Source code for IDE navigation |
| `<artifactId>-<version>-javadoc.jar` | Generated API documentation |
| `<artifactId>-<version>.pom` | Maven project descriptor |
| `SHA512SUMS` | SHA-512 checksums for the four files above |

## Azure Partner Release Pipeline (Maven Central — future)

> **Status:** Maven Central publishing is **deferred** (tracked in
> [#18](https://github.com/AzureCosmosDB/spring-ai/issues/18)). The
> `release.yml` workflow stops after the GitHub Release. This section
> documents the future-state procedure.

Publishing to Maven Central is performed by the Azure SDK
**Partner Release Pipeline** in the `azure-sdk/internal` ADO project,
which handles ESRP signing, OSSRH staging, and the Maven Central push.
This repo's responsibility is limited to handing the unsigned artifacts
to that pipeline.

Reference (Microsoft-internal): [Azure Partner Release Pipeline wiki](https://aka.ms/azsdk/partner-release-pipeline).

### Prerequisites (one-time)

- Membership in the **Azure SDK Partners** security group
  (request via <https://aka.ms/azsdk/join/azuresdkpartners>).
- For a brand-new package (first ever release of new Maven coordinates),
  coordinate first with an Azure SDK architect to confirm conformance to
  the Azure SDK Design Guidelines.

### Per-release steps

1. **Confirm no external SNAPSHOT dependencies.** Maven Central rejects
   POMs that reference SNAPSHOTs. Resolve them first.
2. **Download the four files per module from the GitHub Release.** If the
   `.pom` isn't attached, grab it from the
   `maven-staging-<MODULE>-<VERSION>` workflow artifact under
   `com/azure/spring/ai/<artifactId>/<version>/`. Files must be
   **unsigned** — the partner pipeline signs them.
3. **Upload (flat, not as a Maven directory tree)** to
   `https://azuresdkpartnerdrops.blob.core.windows.net/drops/<team>/java/<version>/`
   under your Azure SDK Partners credentials. The team prefix for Spring
   AI Cosmos DB has not yet been assigned — ask in the
   [Partner Release Pipelines Teams channel](https://teams.microsoft.com/l/channel/19%3ac89f67e32f5941d78a3710a692cf7717%40thread.skype/Partner%2520Release%2520Pipelines?groupId=3e17dcb0-4257-4a30-b843-77f47f1d4121&tenantId=72f988bf-86f1-41af-91ab-2d7cd011db47)
   before the first release.
4. **Trigger the
   [`java - partner-release`](https://dev.azure.com/azure-sdk/internal/_build?definitionId=1809&_a=summary)
   pipeline** with `BlobPath = <team>/java/<version>` and
   `StageOnly = false`.
5. **Confirm publish to Maven Central:**
   `https://repo.maven.apache.org/maven2/com/azure/spring/ai/<artifactId>/<version>/`.

> **First time?** Coordinate the first release through the Partner
> Release Pipelines Teams channel so the Azure SDK release management team
> can shadow the run.

For more detail, see `.github/skills/release/references/release-process.md`.
