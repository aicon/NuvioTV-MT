# NuvioTV-Scrobbler (product fork)

Personal product fork of [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV).  
**Not an official Nuvio build.**

The localization-only fork [`aicon/NuvioTV`](https://github.com/aicon/NuvioTV) stays separate for upstream Hungarian PRs.

## Side-by-side install

| Build | `applicationId` |
|-------|-----------------|
| Official NuvioTV (`full`) | `com.nuvio.tv` |
| This fork (`full`) | `com.aicon.nuviotv` |
| This fork debug | `com.aicon.nuviotv.debug` |

Because the application id differs, this APK installs **next to** the official app and does not replace it. Data stores are separate.

Kotlin/Java `namespace` remains `com.nuvio.tv` (R/BuildConfig package). Only the Android package name / signing / release channel are forked.

## In-app updates

`BuildConfig.GITHUB_OWNER` / `GITHUB_REPO` point at:

- Owner: `aicon`
- Repo: `NuvioTV-Scrobbler`

Release assets must be built with `applicationId=com.aicon.nuviotv` so the PackageInstaller self-update path matches.

Do **not** publish a public GitHub Release until signing and a local update dry-run are verified. Prefer private repo / draft releases during development.

## Signing

Use your own release keystore (never the official Nuvio key, never commit secrets).

Gradle reads (env or `local.properties`):

- `NUVIO_RELEASE_STORE_FILE` — path to `.jks` / `.keystore`
- `NUVIO_RELEASE_KEY_ALIAS`
- `NUVIO_RELEASE_KEY_PASSWORD`
- `NUVIO_RELEASE_STORE_PASSWORD`

Default fallback path in Gradle is still `../nuviotv.jks` relative to `app/` — replace with your own file for real builds. CI uses `RELEASE_KEYSTORE_BASE64` + the same property names (see `.github/workflows/android-release.yml`).

Generate a dedicated key for this product fork and keep backups offline.

## Remotes

```
origin    https://github.com/aicon/NuvioTV-Scrobbler.git
upstream  https://github.com/NuvioMedia/NuvioTV.git
```

Sync from upstream when needed:

```bash
git fetch upstream
git merge upstream/dev
```

## Related services

- Live scrobble Docker addon: [MediaTrail-Scrobbler](https://github.com/aicon/MediaTrail-Scrobbler)
- Hourly history poll: [Nuvio-Yamtrack-Bridge](https://github.com/aicon/Nuvio-Yamtrack-Bridge)
- Mobile counterpart (planned): `aicon/NuvioMobile-Scrobbler` / `com.aicon.nuviomobile`
