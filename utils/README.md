# :libs:utils

Cross-platform utilities for Compose Multiplatform, published to **Maven Central**.

**Targets:** Android · iOS (arm64, simulator-arm64) · JS (browser) · Desktop (JVM)

## API

- `shareFile(fileName, content, mimeType)` — share a generated file via the platform share sheet.
- `@Composable PlatformColors(darkTheme)` — apply edge-to-edge / system-bar styling per platform.

## Coordinates

```kotlin
implementation("com.coderwise.libs:utils:0.2.0")
```

It's on Maven Central, so consumers need **no extra repository or credentials** —
`mavenCentral()` is already in every standard Gradle build. The Kotlin
Multiplatform plugin publishes a root artifact (`utils`) plus a per-target
variant (`utils-android`, `utils-iosarm64`, …); depend on the root coordinate
and Gradle resolves the right variant per target.

## Publishing

Publishing uses the [vanniktech maven-publish](https://vanniktech.github.io/gradle-maven-publish-plugin/)
plugin, which uploads to the Sonatype **Central Portal**, generates the
sources/javadoc jars, and GPG-signs every publication.

**Automated (preferred):** push a tag matching `libs-v*` (or run the
"Publish libs:utils to Maven Central" workflow manually). See
[`.github/workflows/publish-libs-utils.yml`](../../.github/workflows/publish-libs-utils.yml).

```bash
# bump `version` in build.gradle.kts first, then:
git tag libs-v0.2.0 && git push origin libs-v0.2.0
```

**Manual / local:**

```bash
./gradlew -Psign=true :libs:utils:publishAndReleaseToMavenCentral --no-configuration-cache
```

`-Psign=true` enables GPG signing (required by Central); omit it for keyless
local-only runs. Requires these Gradle properties (in `~/.gradle/gradle.properties`,
never committed) or the matching `ORG_GRADLE_PROJECT_*` env vars used in CI.
Same credentials as the sibling `core.mobile` repo:

```properties
mavenCentralUsername=<central portal token username>
mavenCentralPassword=<central portal token password>
signingInMemoryKeyId=<short GPG key id>
signingInMemoryKeyPassword=<GPG key passphrase>
signingInMemoryKey=<ASCII-armored GPG private key>
```

`./gradlew :libs:utils:publishToMavenLocal` still works for local testing
(it skips the Central upload).

## Bumping the version

Edit `version` in [`build.gradle.kts`](build.gradle.kts) (the `coordinates(...)`
call), then tag `libs-v<version>`. Central releases are immutable — every
publish needs a new version.
