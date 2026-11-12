# Photo search development check

Build: `0.3.0-photo-search` (versionCode 5), installed on the Pixel 10 Pro XL.
Final APK SHA-256:
`6be3bd96d640630d24cc5a5ab2b19392a15a79b2178df7a7fedf0d38d25ae5ae`.

## Observed on the phone

- Android selected-photo access was exercised. The owner selected 48 photos.
- Native Gemma image indexing completed 48/48 in the app-reported 43 seconds,
  with zero unreadable images. The image index uses the previously imported model.
- After an APK replacement/relaunch, Photos reported 48/48 indexed; the manual
  still reported 100 extracted pages and 177/177 Gemma passages.
- Typed development query `van pictures` returned 24 ranked photos from the 48
  indexed sources in an app-reported 563 ms. The first visible results showed van
  exteriors and interiors. Filenames were ordinary camera names/UUIDs, and the
  ranking path uses image embeddings only (no filename or label ranking).
- The first result opened in the app's preview viewer. The full original URI also
  opened in Files by Google using the Android chooser's Just once action.
- Final UI polish adds automatic scrolling to the photo results and a stored
  result-query label. This final APK was built, linted, unit-tested, and installed.
  The phone locked before a final visual check of that scroll adjustment.

This is one connected-device development example, not held-out retrieval accuracy,
an offline photo-search test, or a latency percentile. The owner's earlier offline
model/speech tests are separate from the new photo-library workflow.

## Automated validation

- `:app:assembleDebug`, `:app:lintDebug`, `:app:testDebugUnitTest` succeeded.
- 11 unit tests passed: 8 existing manual-retrieval checks plus 3 photo-ranking
  checks (bounded top-k similarity independent of filenames; rejection of invalid
  embeddings; stable tie ordering).
- `PhotoStoreDeviceTest` ran on the Pixel using an isolated temporary database.
  It passed persisted embeddings across reopen, resume excluding completed photos,
  invalidation after source edits, incompatible-fingerprint exclusion, removal of
  sources no longer present/authorized, retry clearing errors, and index removal.
  The temporary database was removed and the owner's database was not modified.
- APK permission inspection: microphone, image access, selected visual media
  access, and AndroidX's app-specific signature permission. No Internet permission.
- Lint: zero errors; nonblocking warnings include pinned dependency updates,
  arm64-only support, missing launcher icon, KTX suggestions, and cursor/stream
  warnings on resources wrapped in Kotlin `use`.

## Boundaries and next checks

The current library scope is the 48 selected local photos, not the entire camera
roll or cloud library. Originals remain in MediaStore, not copied into app-private
storage. Results refresh against current photo access and source versions before
searching/indexing. The app reads local photos and stores metadata/embeddings; it
does not edit, delete, or upload originals.

Still to verify: disconnected restart + new spoken photo search; on-device pause
mid-index and resume; full-library scale; denied/revoked permission UI paths; held-out
positive and negative queries; photo-pack import preserving app-private originals.
Selected-library permission and real image decoding/inference have been exercised;
database-level revocation tests do not replace the remaining Android permission UI
checks. Screenshots/XML used during development are local ignored `.tools` artifacts.
