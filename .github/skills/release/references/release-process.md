# Release process — extended reference

This document is the long-form companion to `SKILL.md`. It covers
troubleshooting, the Azure partner-drops handoff, and a few engineering
rationales that the agent doesn't repeat at every invocation.

## Troubleshooting

### "Version mismatch" in the publish job

The module's `pom.xml <version>` doesn't match the tag's version. The
release.yml workflow runs:

```bash
ACTUAL=$(mvn help:evaluate -pl "$MODULE" -Dexpression=project.version -q -DforceStdout)
```

If `ACTUAL != EXPECTED`, the job fails. Causes and fixes:

- **The release PR wasn't merged before tagging.** Check that
  `git rev-parse HEAD` on `main` equals `git rev-parse origin/main` and that
  the merge commit changed `<MODULE>/pom.xml`. Re-run the prepare phase if
  not.
- **The tag points at a stale commit.** Delete and recreate:

  ```bash
  git tag -d <tag>
  git push origin :refs/tags/<tag>
  git checkout main && git pull origin main
  .github/skills/release/scripts/create-release-tag.sh \
      --module <m> --version <v>
  ```

### "Reject internal SNAPSHOT dependencies" in the publish job

An autoconfigure module was tagged while its inter-module version property
in `pom.xml` was still a SNAPSHOT. The two failure modes are:

- `spring-ai-autoconfigure-vector-store-azure-cosmos-db` with
  `<spring-ai-cosmos-db-store.version>` set to a SNAPSHOT.
- `spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db` with
  `<spring-ai-cosmos-chat-memory.version>` set to a SNAPSHOT.

Fix (vector-store autoconfigure shown; same procedure applies to
chat-memory autoconfigure with its corresponding property and module name):

1. Open a follow-up PR that bumps the property to the released core
   version (`X.Y.Z`, `X.Y.Z-beta.N`, or `X.Y.Z-MN`).
2. Merge.
3. Delete the failed tag, re-tag from the new merge commit:

   ```bash
   git tag -d spring-ai-autoconfigure-vector-store-azure-cosmos-db-v<v>
   git push origin :refs/tags/spring-ai-autoconfigure-vector-store-azure-cosmos-db-v<v>
   git checkout main && git pull origin main
   .github/skills/release/scripts/create-release-tag.sh \
       --module spring-ai-autoconfigure-vector-store-azure-cosmos-db \
       --version <v>
   ```

### External SNAPSHOT warnings (e.g. Spring AI 2.0.0-SNAPSHOT)

These are **warnings**, not errors. The GitHub Release will still publish.
However:

- Consumers may not be able to resolve the artifact once the upstream
  snapshot rolls over (snapshots are mutable).
- Maven Central publishing (when enabled, issue #18) **will reject** POMs
  that reference SNAPSHOTs. Resolve external snapshots to GA / milestone
  versions before triggering the partner pipeline.

### "Tag is not reachable from origin/main"

The tag points at a commit that hasn't been merged to `main`.

Most common cause: the release prep PR wasn't merged before tagging, or
the user tagged from the release branch by accident. Always run the agent's
Phase 5 (`git checkout main && git pull origin main`) before tagging, and
never tag from a feature branch.

### Pipeline didn't trigger

- Verify the tag matches one of the explicit allowlisted patterns in
  `.github/workflows/release.yml`. The workflow does **not** match
  `spring-ai-*-v…` wildcards — each module has its own line in the `on:
  push: tags:` list (3 stable + 3 beta).
- Check that the tag points to a commit on `main`.
- Check the Actions tab — the workflow may be queued behind another run.
- Pushed multiple tags in one `git push`? GitHub silently delivers only
  one trigger event in that case; re-push each tag with a separate command.

### Approval is stuck on the publish job

Go to **Actions → Release → (your run)**. If the publish job shows
"Waiting for review", confirm that you're listed as a reviewer in
**Settings → Environments → production**. If not, ask a maintainer to add
you, then click **Review deployments → production → Approve**.

### Maven validation failures during local prepare-phase check

The `--phase prepare` validation runs `mvn dependency:list` to mirror the
workflow's internal-SNAPSHOT check. This requires JDK 17, Maven (or
`./mvnw`), and network access to the Spring snapshot repository.

If local Maven is unavailable or flaky:

```bash
.github/skills/release/scripts/validate-release.sh \
    --phase prepare --module <m> --version <v> --skip-mvn
```

The release.yml workflow will enforce the same check on tag push, so
skipping locally is safe for fast iteration.

## Version policy

Follows [Semantic Versioning](https://semver.org/):

- **Major** (`X.0.0`) — breaking API changes
- **Minor** (`0.Y.0`) — new features, backward compatible
- **Patch** (`0.0.Z`) — bug fixes, backward compatible
- **Beta** (`X.Y.Z-beta.N`) — pre-release, may have breaking changes
- **Milestone** (`X.Y.Z-MN`) — pre-release following Spring convention (no dot before N)

Release-candidate versions (`-rc.N`) are **not supported** by the workflow's
tag regex. Use `-beta.N` or `-MN` for any pre-release.

## Tag grammar (authoritative)

```
<module>-v<MAJOR>.<MINOR>.<PATCH>             # stable
<module>-v<MAJOR>.<MINOR>.<PATCH>-beta.<N>    # beta
<module>-v<MAJOR>.<MINOR>.<PATCH>-M<N>        # milestone (Spring convention)
```

Where `<module>` is exactly one of:

- `spring-ai-azure-cosmos-db-store`
- `spring-ai-autoconfigure-vector-store-azure-cosmos-db`
- `spring-ai-model-chat-memory-repository-cosmos-db`
- `spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db`

The workflow uses an explicit per-module allowlist (not a wildcard); any
tag outside this grammar is silently ignored. The `parse-tag` job validates
the version regex `^[0-9]+\.[0-9]+\.[0-9]+(-beta\.[0-9]+|-M[0-9]+|-RC[0-9]+)?$`.

## Why no parent POM and no root `<module-name>.version` properties?

Each module is independently versioned and self-contained. The root
`pom.xml` is a thin **aggregator** (`packaging=pom`) so contributors can run
`mvn verify` from the root and exercise every module in one reactor build.
Modules do not declare a `<parent>` and do not inherit anything from the
root.

This means each module's release version lives in its own `pom.xml
<version>` element, which the release workflow reads via:

```bash
mvn help:evaluate -pl <module> -Dexpression=project.version -q -DforceStdout
```

The `validate-release.sh` script reads the same value with a portable
awk-based parser for fast offline validation. The two approaches are
required to agree before a release can succeed.

## Why "tag core first, then autoconfigure"?

There are two **core → autoconfigure** pairs:

| Core | Autoconfigure | Inter-module property |
|---|---|---|
| `spring-ai-azure-cosmos-db-store` | `spring-ai-autoconfigure-vector-store-azure-cosmos-db` | `<spring-ai-cosmos-db-store.version>` |
| `spring-ai-model-chat-memory-repository-cosmos-db` | `spring-ai-autoconfigure-model-chat-memory-repository-cosmos-db` | `<spring-ai-cosmos-chat-memory.version>` |

Each autoconfigure module declares a dependency on its matching core via
the property listed above. The release workflow **rejects** any artifact
whose runtime classpath contains a `com.azure.spring.ai:*-SNAPSHOT`
dependency.

If the core hasn't been released yet, the autoconfigure release will
either:

- pin a SNAPSHOT (rejected by the workflow), or
- pin a non-existent version (Maven will fail to resolve in CI).

So a wave PR that includes both members of a pair must:

1. Bump the core's `pom.xml <version>` to the release version.
2. Bump the autoconfigure's `pom.xml <version>` to its release version.
3. Bump the autoconfigure's inter-module property to the same core release
   version as (1).

After merge, the agent tags the core first, waits for the core's GitHub
Release run to complete, then tags the autoconfigure. The autoconfigure
run verifies the dep is resolvable as a release.

## Azure Partner Release Pipeline (Maven Central — future)

> **Status:** Maven Central publishing is currently **deferred** (tracked
> in issue #18). The `release.yml` workflow stops after creating the
> GitHub Release. This section documents the future-state procedure so
> the on-ramp is ready when #18 lands.

The `release.yml` workflow attaches the **unsigned** artifacts (`.jar`,
`-sources.jar`, `-javadoc.jar`, `.pom`, `SHA512SUMS`) to a GitHub Release.
Publishing to **Maven Central** is performed by the Azure SDK
**Partner Release Pipeline** in the `azure-sdk/internal` ADO project,
which handles ESRP signing, OSSRH staging, and the Maven Central push.
This repo's only responsibility is to hand the unsigned artifacts to that
pipeline.

Reference (Microsoft-internal):
[Azure Partner Release Pipeline wiki](https://aka.ms/azsdk/partner-release-pipeline).

### Prerequisites (one-time)

- Membership in the **Azure SDK Partners** security group
  (request via <https://aka.ms/azsdk/join/azuresdkpartners> — manager
  approval required). Grants both blob upload rights and ADO pipeline run
  rights.
- For a brand-new package (first ever release of new Maven coordinates),
  the releaser must first chat with an Azure SDK architect to confirm the
  package conforms to the Azure SDK Design Guidelines (per the wiki).

### Per-release steps

1. **Confirm no external SNAPSHOT dependencies.** The GitHub Release may
   carry external SNAPSHOTs (warned, not blocked), but **Maven Central
   rejects POMs that reference SNAPSHOTs.** Resolve any
   `*-SNAPSHOT` dep to a GA or milestone version, ship a follow-up
   release, and only then proceed.

2. **Download the four files per module from the GitHub Release.** Each
   module release attaches:

   - `<artifactId>-<version>.jar`
   - `<artifactId>-<version>-sources.jar`
   - `<artifactId>-<version>-javadoc.jar`
   - `<artifactId>-<version>.pom`

   If the GitHub Release is missing the `.pom`, grab it from the
   `maven-staging-<MODULE>-<VERSION>` GitHub Actions artifact (Maven
   directory tree under `com/azure/spring/ai/<artifactId>/<version>/`).

   All four files must be **unsigned** (the partner pipeline signs them).

3. **Upload (flat) to the partner blob container.**

   - Container:
     `https://azuresdkpartnerdrops.blob.core.windows.net/drops`
   - Path convention: `<team>/java/<version>/`
     (the team prefix for Spring AI Cosmos DB has not yet been assigned;
     ask in the
     [Partner Release Pipelines Teams channel](https://teams.microsoft.com/l/channel/19%3ac89f67e32f5941d78a3710a692cf7717%40thread.skype/Partner%2520Release%2520Pipelines?groupId=3e17dcb0-4257-4a30-b843-77f47f1d4121&tenantId=72f988bf-86f1-41af-91ab-2d7cd011db47)
     before the first run).
   - Upload all four files **directly** under that path — do **not**
     preserve the Maven directory tree from the staging artifact.
   - Important: put **only** the artifacts for this version under that
     path. The pipeline publishes _everything_ in that folder.

4. **Trigger the
   [`java - partner-release`](https://dev.azure.com/azure-sdk/internal/_build?definitionId=1809&_a=summary)
   pipeline** in `azure-sdk/internal`. Click **Run pipeline** and set:

   - `BlobPath` → the relative path you used in step 3
     (e.g. `springai/java/1.0.3`).
   - `StageOnly` → leave at `false` for a normal GA / beta release. Set
     to `true` only if you want to stage in OSSRH and inspect / promote
     manually.

   The pipeline will sign the jars, stage to `oss.sonatype.org` under the
   `azuresdk` account, and release to **Maven Central**.

5. **Confirm publish to Maven Central** once the partner pipeline
   finishes:
   `https://repo.maven.apache.org/maven2/com/azure/spring/ai/<artifactId>/<version>/`.

> **First time?** It is highly recommended to coordinate the first
> release through the Partner Release Pipelines Teams channel so the
> Azure SDK release management team can shadow the run.

## Why a post-release SNAPSHOT bump?

After the release PR merges, `main` is at e.g. `1.0.0` (no SNAPSHOT). If
that's left untouched, the next dev cycle keeps claiming the just-released
version as "the current development version", which:

- breaks Maven's mental model of SNAPSHOT vs release coordinates,
- means a re-tag of the same `<module>-v1.0.0` would silently re-publish a
  different artifact (the GitHub Release exists, but the workflow re-runs
  on push and would attempt to re-upload),
- confuses downstream consumers reading `mvn help:evaluate` output.

The agent always opens a follow-up `chore/post-release-<wave-id>` PR to
bump each released module's `pom.xml <version>` to the next dev SNAPSHOT
(and bumps autoconfigure's `<spring-ai-cosmos-db-store.version>` property
back to the next store SNAPSHOT). This is non-optional.
