---
name: release
description: >
  Manages releases for the AzureCosmosDB/spring-ai modules. Phase-aware
  validation, prepares a release branch + PR with POM and changelog updates,
  then after merge creates and pushes per-module version tags that trigger
  the automated release pipeline. Handles single-module and multi-module
  release waves with the correct dependency ordering.
allowed-tools: bash git gh read_file edit
arguments:
  module:
    type: string
    required: true
    description: >
      Module to release. One of: spring-ai-azure-cosmos-db-store,
      spring-ai-autoconfigure-vector-store-azure-cosmos-db,
      spring-ai-model-chat-memory-repository-cosmos-db,
      spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db. For a
      multi-module wave, specify the first module here and supply the others
      when prompted.
  version:
    type: string
    required: true
    description: >
      Version to release following semver (X.Y.Z, X.Y.Z-beta.N, or X.Y.Z-MN), e.g.
      1.0.0-beta.1, 1.0.0-M1, 1.0.0.
  date:
    type: string
    required: false
    default: today
    description: >
      Release date in YYYY-MM-DD format. Defaults to today.
argument-hints:
  module:
    - spring-ai-azure-cosmos-db-store
    - spring-ai-autoconfigure-vector-store-azure-cosmos-db
    - spring-ai-model-chat-memory-repository-cosmos-db
    - spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db
  version:
    - 1.0.0-beta.1
    - 1.0.0
  date:
    - "2026-05-13"
---

# Release

Orchestrates the release of `AzureCosmosDB/spring-ai` modules through a
PR-based workflow on the canonical repo (no fork involved — this skill
assumes the user has push access to `origin`):

1. preflight validation,
2. prepare a release branch with version + changelog updates,
3. merge via PR,
4. tag,
5. monitor the release pipeline,
6. post-release SNAPSHOT bump PR,
7. (optional, future) Maven Central handoff via the Azure partner pipeline.

Core scripts are in `<THIS_SKILL_DIRECTORY>/scripts/`. Reference material is
in `<THIS_SKILL_DIRECTORY>/references/release-process.md`.

Path note: All script paths are relative to this skill directory (this
SKILL.md file), not the repository root.

## Prerequisites

- `git` configured with push access to `origin` (the canonical
  AzureCosmosDB/spring-ai repo). Verified by `validate-release.sh`.
- `gh` authenticated and able to open PRs against `AzureCosmosDB/spring-ai`.
- Working directory is the repository root.
- For the `prepare`-phase Maven check: JDK 17 + Maven (or `./mvnw`) + network
  access. Pass `--skip-mvn` to bypass; the GitHub Actions workflow will
  enforce the same check on tag push.

## Publishable modules and dependency rules

| Module | Inter-module deps |
|---|---|
| `spring-ai-azure-cosmos-db-store` | none |
| `spring-ai-autoconfigure-vector-store-azure-cosmos-db` | depends on `spring-ai-azure-cosmos-db-store` via the `<spring-ai-cosmos-db-store.version>` property in its own `pom.xml` |
| `spring-ai-model-chat-memory-repository-cosmos-db` | none |
| `spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db` | depends on `spring-ai-model-chat-memory-repository-cosmos-db` via the `<spring-ai-cosmos-chat-memory.version>` property in its own `pom.xml` |

**Release ordering rules:**

There are two **core → autoconfigure** pairs. Within each pair, if both
members are in the same release wave, the core module must be tagged first
and the autoconfigure module's release PR must update its inter-module
version property to the **same** released version of the core:

| Core (release first) | Autoconfigure (release second) | Property to bump |
|---|---|---|
| `spring-ai-azure-cosmos-db-store` | `spring-ai-autoconfigure-vector-store-azure-cosmos-db` | `<spring-ai-cosmos-db-store.version>` |
| `spring-ai-model-chat-memory-repository-cosmos-db` | `spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db` | `<spring-ai-cosmos-chat-memory.version>` |

The release pipeline blocks any artifact whose runtime classpath contains
a `com.azure.spring.ai:*-SNAPSHOT` dependency, so this ordering is
non-negotiable. Cross-pair ordering doesn't matter; a core module released
without its autoconfigure (or vice-versa, when the property already pins
a non-SNAPSHOT released core) may be released alone.

## Workflow

Execute these phases in order. Stop and report if any phase fails.

### Phase 1: List modules

```bash
<THIS_SKILL_DIRECTORY>/scripts/list-modules.sh
```

Show the output to the user. If the user has not specified module(s) /
version, use this to help them pick.

### Phase 2: Preflight validation

For each module being released:

```bash
<THIS_SKILL_DIRECTORY>/scripts/validate-release.sh \
    --phase preflight --module <MODULE> --version <VERSION>
```

Stop on any failure. Common fixes:

- **Not on main / out of date** — `git checkout main && git pull origin main`
- **Working tree dirty** — `git stash` or commit
- **Tag already exists** — verify whether a release was already published; if
  not, instruct the user to delete the tag manually
  (`git tag -d <tag> && git push origin :refs/tags/<tag>`). Do NOT delete tags
  yourself.
- **CHANGELOG.md missing `[Unreleased]`** — add one before continuing.

### Phase 3: Prepare the release branch and PR

Branch name:

| Wave shape | Branch name |
|---|---|
| Single module | `release/<MODULE>-<VERSION>` |
| Multi-module wave | `release/wave-<YYYYMMDD>` |

```bash
git checkout -b release/<MODULE>-<VERSION>   # or release/wave-<DATE>
```

For each module being released:

1. **Bump POM `<version>`** in `<MODULE>/pom.xml` — strip `-SNAPSHOT`
   (e.g. `1.0.0-SNAPSHOT` → `1.0.0`). The aggregator root `pom.xml` is NOT
   a parent and is not edited.
2. **For autoconfigure modules** (either
   `spring-ai-autoconfigure-vector-store-azure-cosmos-db` or
   `spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db`): if
   the matching core module is also being released in this wave, update
   the inter-module version property in the autoconfigure module's
   `pom.xml` to the wave's release version of the core (per the pairs
   table above). If the core is **not** in this wave, the property must
   already point at a previously released non-SNAPSHOT version — check and
   abort if it doesn't.
3. **Update `<MODULE>/CHANGELOG.md`:**
   - Locate `## [Unreleased]`.
   - Replace with `## [<VERSION>] — <DATE>` (provided date or today).
   - Insert a new empty `## [Unreleased]` section above with the standard
     Keep-a-Changelog subsections (`### Added`, `### Changed`, …).
4. **Show the user a diff** for review.

After all module edits are committed, run prepare-phase validation per
module:

```bash
<THIS_SKILL_DIRECTORY>/scripts/validate-release.sh \
    --phase prepare --module <MODULE> --version <VERSION>
```

The Maven dependency check is slow; pass `--skip-mvn` for fast iteration if
needed. The GitHub Actions workflow runs the authoritative version of this
check on tag push.

Push and open the PR:

```bash
git push -u origin <BRANCH>
gh pr create --base main --head <BRANCH> \
  --title "release: prepare <SUMMARY>" \
  --body "<auto-generated body>"
```

The PR body should list every module being released with its target version,
the changelog diff, and a "Release plan" footer noting the tag(s) that will
be pushed after merge and the dependency order.

### Phase 4: Wait for PR merge

Stop and prompt the user. The release tag(s) must point to a commit on
`main` that includes the version + changelog changes; the workflow rejects
tags that aren't reachable from `origin/main`.

### Phase 5: Tag and push

After the PR is merged:

```bash
git checkout main && git pull origin main
```

Run tag-phase validation, then create the tag for the **first** module in
dependency order (within each core → autoconfigure pair, the core module
goes first when both are in the wave; cross-pair ordering doesn't matter):

```bash
<THIS_SKILL_DIRECTORY>/scripts/validate-release.sh \
    --phase tag --module <MODULE> --version <VERSION>

<THIS_SKILL_DIRECTORY>/scripts/create-release-tag.sh \
    --module <MODULE> --version <VERSION>
```

The tag script enforces:

- on `main`, working tree clean,
- `HEAD == origin/main`,
- module's `pom.xml <version>` equals the requested version,
- tag does not already exist.

It then pushes the **single tag** to `origin` (pushing multiple tags in one
`git push` silently fails to trigger GitHub Actions workflows).

### Phase 6: Monitor

```bash
gh run list --workflow=release.yml --limit=5
```

Report the workflow URL and remind the user:

> The publish job pauses on the **production** environment for manual
> approval. Go to **Actions → Release → (your run) → Review deployments → Approve**.

For multi-module waves that include a core → autoconfigure pair, **wait
for the core module's workflow to complete (and the GitHub Release to
exist) before tagging the matching autoconfigure module**. The
autoconfigure release will fail otherwise — its pinned core dep would not
yet be resolvable as a release artifact. This applies to both pairs:

- `spring-ai-azure-cosmos-db-store` → `spring-ai-autoconfigure-vector-store-azure-cosmos-db`
- `spring-ai-model-chat-memory-repository-cosmos-db` → `spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db`

Then tag the next module and repeat.

### Phase 7: Post-release SNAPSHOT bump

After every module in the wave has a successful GitHub Release, open a
follow-up PR `chore/post-release-<wave-id>` that bumps each just-released
module's `pom.xml <version>` to the next dev-cycle SNAPSHOT.

Ask the user for the next dev version per module. Common defaults:

- After a stable release `X.Y.Z` → `X.Y.(Z+1)-SNAPSHOT`
- After a beta `X.Y.Z-beta.N` → `X.Y.Z-beta.(N+1)-SNAPSHOT` or
  `X.Y.Z-SNAPSHOT` (depends on whether more betas are planned)
- After a milestone `X.Y.Z-MN` → `X.Y.Z-M(N+1)-SNAPSHOT` or
  `X.Y.Z-SNAPSHOT` (depends on whether more milestones are planned)

For each core module released in this wave, bump the matching autoconfigure
module's inter-module version property
(`<spring-ai-cosmos-db-store.version>` for the vector-store pair,
`<spring-ai-cosmos-chat-memory.version>` for the chat-memory pair) back to
the next SNAPSHOT of the core, so the reactor build resolves the core from
source again during ongoing development.

Open the PR and stop. The user merges it.

### Phase 8: (Optional, future) Maven Central handoff

Maven Central publishing is currently **deferred** (tracked in issue #18).
The `release.yml` workflow stops after creating the GitHub Release.

When Maven Central is enabled, the publish step is performed by the Azure
SDK **Partner Release Pipeline**. Walk the user through:

1. Confirm the release artifacts have **no external SNAPSHOT dependencies**
   (e.g. Spring AI itself was on `2.0.0-SNAPSHOT` early on). External
   SNAPSHOT deps are allowed for GitHub Releases but block Maven Central
   publishing — Sonatype rejects POMs that reference SNAPSHOTs.
2. Download the `.jar`, `-sources.jar`, `-javadoc.jar`, and `.pom` for each
   released module (the workflow stages them in
   `maven-staging-<MODULE>-<VERSION>` artifacts).
3. Upload (flat) to
   `https://azuresdkpartnerdrops.blob.core.windows.net/drops/<team>/java/<version>/`
   under your Azure SDK Partners credentials.
4. Trigger the
   [`java - partner-release` ADO pipeline](https://dev.azure.com/azure-sdk/internal/_build?definitionId=1809&_a=summary)
   with `BlobPath = <team>/java/<version>` and `StageOnly = false`.
5. Verify on Maven Central:
   `https://repo.maven.apache.org/maven2/com/azure/spring/ai/<artifactId>/<version>/`.

See `<THIS_SKILL_DIRECTORY>/references/release-process.md` for full detail
on the partner-drops handoff and prerequisites (Azure SDK Partners group
membership, etc.).

## Key rules

- **Never skip phase validation.** Each phase has a dedicated check set.
- **Always show diffs** before committing version + changelog changes.
- **Always ask for explicit confirmation** before pushing a tag.
- **Respect dependency order.** Within each core → autoconfigure pair, the
  core module must be tagged (and have a successful release run) before
  the matching autoconfigure module when both are in the same wave. The
  pairs are: `spring-ai-azure-cosmos-db-store` →
  `spring-ai-autoconfigure-vector-store-azure-cosmos-db`, and
  `spring-ai-model-chat-memory-repository-cosmos-db` →
  `spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db`.
- **One tag at a time.** `git push origin <tag>` per tag — never combine.
- **Always open a post-release SNAPSHOT bump PR** so `main` doesn't keep
  claiming the just-released version as the dev version.

## Troubleshooting

See `<THIS_SKILL_DIRECTORY>/references/release-process.md` for detailed
troubleshooting covering version mismatches, dependency resolution failures,
workflow trigger issues, and approval problems.
