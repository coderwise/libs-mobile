# :libs:utils

Cross-platform utilities for Compose Multiplatform, published to **Maven Central**.

**Targets:** Android · iOS (arm64, simulator-arm64) · JS (browser) · Desktop (JVM)

## API

- `shareFile(fileName, content, mimeType)` — share a generated file via the platform share sheet.
- `@Composable rememberShareTextLauncher()` — share plain text from Compose code:
  share sheet on Android/iOS, clipboard on desktop/web. Takes the Android
  context from the composition and carries a chooser title. Prefer this.
- `shareText(text)` — the same, for callers outside composition (view models,
  services). On Android it resolves the context from Koin's `GlobalContext`.
- `LruCache<K, V>` — generic size-bounded cache.
- `@Composable PlatformColors(darkTheme)` — apply edge-to-edge / system-bar styling per platform.
- `@Composable KeepScreenOn(enabled)` — hold a wake lock while the screen is shown.

## Coordinates

```kotlin
implementation("com.coderwise.libs:utils:0.5.0")
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

**Automated (preferred):** push a tag matching `utils-v*` (or run the
[`publish`](../.github/workflows/publish.yml) workflow manually). The tag is the
version — nothing to bump in `build.gradle.kts`.

```bash
git tag utils-v0.5.0 && git push origin utils-v0.5.0
```

**Manual / local:**

```bash
./gradlew -Psign=true -PlibVersion=0.5.0 :utils:publishAndReleaseToMavenCentral --no-configuration-cache
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

`./gradlew :utils:publishToMavenLocal` still works for local testing (it skips
the Central upload and publishes as `0.0.0-LOCAL` unless `-PlibVersion` is given).

## Bumping the version

Tag `utils-v<version>` — the workflow passes it through as `-PlibVersion`.
Central releases are immutable, so every publish needs a new version.
