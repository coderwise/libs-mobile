# libs-mobile

Shared Kotlin Multiplatform libraries for Coderwise apps, published to **Maven Central**
under the `com.coderwise.libs` group. Consumers need only `mavenCentral()` — no
credentials.

## Modules

| Module | Coordinates | Targets |
|---|---|---|
| [`:utils`](utils) | `com.coderwise.libs:utils` | Android · iOS (arm64, sim-arm64) · JS · Desktop |

## Publishing

Each module is versioned and released **independently**, via the
[vanniktech maven-publish](https://vanniktech.github.io/gradle-maven-publish-plugin/)
plugin (Central Portal upload, GPG signing, sources/javadoc jars).

**The release tag is the single source of truth for the version.** Tag a module
release as `<module>-v<version>`; the [`publish`](.github/workflows/publish.yml)
workflow parses it, passes `-PlibVersion`, and publishes **only that module**:

```bash
git tag utils-v0.3.0       && git push origin utils-v0.3.0        # → com.coderwise.libs:utils:0.3.0
git tag permissions-v1.0.0 && git push origin permissions-v1.0.0  # → com.coderwise.libs:permissions:1.0.0
```

The same workflow also accepts a manual `workflow_dispatch` (module + version inputs).
It needs these repo secrets (same values as the other coderwise repos —
`com.coderwise` namespace is already verified): `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`, `GPG_KEY_CONTENTS`.

Local, keyless (publishes as `0.0.0-LOCAL` unless `-PlibVersion` is given):

```bash
./gradlew :utils:publishToMavenLocal
```

### Adding a new module

Drop it in as a top-level folder with a `build.gradle.kts` (settings.gradle.kts
auto-includes it). Mirror `:utils`: apply the vanniktech plugin, read the version
from `libVersion` (default `0.0.0-LOCAL`), set `coordinates("com.coderwise.libs", "<name>", …)`.
Release it with a `<name>-v<version>` tag — no workflow changes needed.

## History

`:utils` originated in `coderwise/maps-mobile` (published through `0.2.0`) and was
extracted here; `com.coderwise.libs:utils` coordinates are unchanged, so consumers
required no edits beyond the version bump.
