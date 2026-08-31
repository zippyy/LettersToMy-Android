# Letters to My — Android

Android companion app for [Letters to My](https://github.com/zippyy/LettersToMy).
Feature-converged against the iOS app (SwiftUI + `LettersToMyCore`) and the
self-hosted sync server (`zippyy/LettersToMy-SelfHostedSync`).

## Status

Feature-complete Android implementation. Domain parity, cross-platform
`.letterstomy` archive compatibility (bidirectional, proven against the real
Swift production codec), Room schema v2 with a tested non-destructive
migration, live SelfHostedSync API v1 client (proven end-to-end against a
real server), and a full automated test suite (101 JVM tests) + CI.

## Architecture

- **UI**: Jetpack Compose (Material 3), bottom navigation (Letters, Timeline,
  Family, People, Settings)
- **Local storage**: Room (`LettersDatabase`, schema version 2; migration
  `1→2` proven with `MigrationTestHelper` — non-destructive ALTER/CREATE,
  **no** `fallbackToDestructiveMigration`)
- **Screens**: Onboarding (first-launch archive/family setup, no duplicate
  seeding), Letters (All/Draft/Scheduled/Unlocked filters, All Children,
  search, privacy-safe sealed rows), Editor (milestone templates, unlock
  rules, PhotoPicker/OpenDocument attachments, safe save), Detail
  (privacy-gated sealed content, state-aware delete, attachment cascade),
  Family (children/branches/folders CRUD, seeded-default protection),
  Timeline (unlock ordering, explicit child filter), People (collaboration
  directory, typed API errors), Backup (create/upload/list/preview/restore/
  delete with `letter_count`)
- **Package**: `com.letters2my.app` · **Min SDK 26** · **Target SDK 35**

## Cross-Platform Archive Boundary

**The portable, cross-platform format is the encrypted `.letterstomy`
archive** — never raw SQLite:

- AES-256-GCM, key = SHA-256(passphrase), payload `nonce(12) || ciphertext ||
  tag(16)`
- Swift reference-date doubles (seconds since 2001-01-01), nulls omitted,
  `Data` as base64
- Proven **bidirectional**: iOS fixture → Android decodes field-for-field;
  Android-produced archive → real Swift production `BackupService.decryptPayload`
  decodes field-for-field (79/79 checks, incl. attachment byte identity)

Platform-native **device snapshots** (`/sync/pull|push/{platform}`) are
platform-specific only. Raw iOS SQLite is **never** loaded into Android Room,
and raw Android SQLite is **never** treated as a portable iOS backup.

## SelfHostedSync API v1

Typed `SelfHostedApiClient` (pure JVM: OkHttp + org.json): canonical base URL
normalization, Bearer auth, structured `ApiException` with `{code, message}`
envelope decoding, DTOs for Status/Backups/Attachments/Sync/Invitations/
Members/Branches/Folders. Proven live against the real Go server
(8/8 E2E checks: status, auth 401s, backup push/list/pull/delete with
`letter_count` + sha256 byte identity, attachments, snapshots, collaboration
CRUD). The old ad-hoc OkHttp layers and `SelfHostedCollaborationClient` are
gone — this is the single networking stack.

## Backup Providers

All providers transport portable encrypted `.letterstomy` archives
(`pushArchive`/`pullArchive`), never raw Room SQLite:

| Provider | Transport | Credentials |
|---|---|---|
| Google Drive | Drive REST v3 (appDataFolder) | OAuth (GoogleSignIn) |
| Dropbox | `/2/files` REST | OAuth token |
| WebDAV / Nextcloud | PROPFIND/PUT/GET/DELETE | Basic auth password |
| S3-compatible (AWS/B2/R2/MinIO) | SigV4 | Access key + secret |
| SelfHostedSync | API v1 `backup/push\|pull\|list\\|delete` | Bearer token |

Secrets (S3 secret, WebDAV password, Dropbox token, self-hosted token) live in
`SecureCredentials` (Android Keystore-backed encrypted preferences). Non-secret
settings stay in `SettingsRepository`. Nothing is stored plaintext.

Restore is safety-first: an archive is fully decrypted/validated **before**
any database mutation, and `applyRestore` is additive (skips existing IDs,
imports new rows preserving original identifiers, attachments only when their
letter exists). Wrong passphrase or corrupted archive leaves the database
untouched.

## Background Sync

**Not implemented — by design.** iOS has no automatic background backup
machinery (all backups are manual `backupNow(to:)` taps; no `BGTaskScheduler`),
so the product contract is manual backup. Android matches that contract and
does not advertise automatic sync. All backup/sync actions are explicit
user-initiated operations.

## Build

```bash
./gradlew assembleDebug          # debug APK
./gradlew test                   # 101 JVM tests (domain, migration, DAO, restore safety)
./gradlew lint                   # Android lint
```

Requires JDK 17 and an Android SDK (compileSdk 35).

### Live server E2E

`SelfHostedSyncE2ETest` runs against a real server when one is reachable and
auto-skips otherwise (CI without a server stays green):

```bash
# server: PORT=8080 DATA_DIR=/tmp/ltm-server-test/data \
#   API_KEYS_FILE=/tmp/ltm-server-test/api_keys.txt ./server
# api_keys.txt contains: android-e2e:test-token-123
./gradlew :app:testDebugUnitTest --tests '*SelfHostedSyncE2ETest'
```

## CI

`.github/workflows/android-ci.yml`: `test` (incl. Room migration + archive
compatibility), `lint`, `assembleDebug` with Gradle/dependency caching on
every push/PR to `main`; optional instrumentation job (tag/schedule only,
managed emulator, no production signing, no secrets).

## Privacy

Sealed letter content is never exposed in list previews (sealed rows render a
"Sealed" chip, no body), timeline entries (no body field), or detail until
unlocked. Logging never includes passphrases or sealed bodies.

## License

Same as LettersToMy-iOS.