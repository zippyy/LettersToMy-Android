# Letters to My — Android

Android companion app for [Letters to My](https://github.com/zippyy/LettersToMy).

## Architecture

- **UI**: Jetpack Compose with Material 3, bottom navigation (Letters, Timeline, Family, People, Settings)
- **Local storage**: Room database
- **Cross-platform sync**: Firebase / Firestore (no CloudKit on Android)
- **Backup format**: Same `.letterstomy` encrypted archive format as iOS — compatible for cross-device restore

## Status

**Scaffold** — project structure, navigation shell, and stub screens. Feature implementation pending.

### Roadmap

| Priority | Feature | Status |
|----------|---------|--------|
| P1 | Room schema (letters, children, branches, folders, members) | pending |
| P2 | Letter editor with rich text | pending |
| P3 | Attachment picker (camera, gallery, files) | pending |
| P4 | Family management (sides, folders, collaborators) | pending |
| P5 | Backup export/import (.letterstomy format) | pending |
| P6 | Firebase sync | pending |
| P7 | Unlock rules + delivery | pending |
| P8 | Notifications | pending |

## Build

```bash
./gradlew assembleDebug
```

## License

Same as LettersToMy-iOS.