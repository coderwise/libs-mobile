# libs-mobile

Shared Kotlin Multiplatform libraries for Coderwise apps, published to **Maven Central**
under the `com.coderwise.libs` group. Consumers need only `mavenCentral()` — no
credentials.

## Modules

| Module | Coordinates | Targets |
|---|---|---|
| [`:utils`](utils) | `com.coderwise.libs:utils` | Android · iOS (arm64, sim-arm64) · JS · Desktop |

## Publishing

Via the [vanniktech maven-publish](https://vanniktech.github.io/gradle-maven-publish-plugin/)
plugin (Central Portal upload, GPG signing, sources/javadoc jars).

```bash
# bump the module's coordinates(...) version, then tag:
git tag utils-v0.3.0 && git push origin utils-v0.3.0
```

The "Publish utils to Maven Central" workflow runs on `utils-v*` tags (or manual
dispatch) and needs these repo secrets: `MAVEN_CENTRAL_USERNAME`,
`MAVEN_CENTRAL_PASSWORD`, `SIGNING_KEY_ID`, `SIGNING_PASSWORD`, `GPG_KEY_CONTENTS`
(same values as the other coderwise repos — `com.coderwise` namespace is already verified).

Local, keyless: `./gradlew :utils:publishToMavenLocal`.

## History

`:utils` originated in `coderwise/maps-mobile` (published through `0.2.0`) and was
extracted here; `com.coderwise.libs:utils` coordinates are unchanged, so consumers
required no edits beyond the version bump.
