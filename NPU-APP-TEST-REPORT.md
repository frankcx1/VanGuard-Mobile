# NPU-first app integration: 0.4.0

Installed on the owner's Pixel 10 Pro XL (Tensor G5), with NPU preferred and
CPU/GPU fallback available. Final APK SHA-256:
`8ca13b53d6abee413bb9c22e091f3f3030ed0e1b5d46b38a5f88442f0ffa71db`.

## Result and data preservation

- Main app includes the tested Tensor dispatch library, excludes its on-device
  compiler plugin, and verifies the pinned NPU model before use.
- NPU and CPU/GPU models remain separate private files. Every query uses the index
  fingerprint belonging to the selected model/backend. No cross-model vectors are
  silently combined.
- SQLite version 1 to 2 adds NPU vector/fingerprint columns while preserving CPU/GPU
  entries. Original PDF bytes and the selected photo library remain unchanged.
- On the Pixel: 100 extracted manual pages, 177/177 manual passages for each backend,
  and 48/48 selected photos for each backend. No unreadable photos.
- Initial NPU preparation reported 11 seconds for manual passages and 31 seconds
  for the 48 photos, retaining already-complete CPU/GPU fallback indexes. These were
  development observations, not controlled performance benchmarks.
- Existing private databases were backed up on the device as `binder-pre-npu.sqlite`
  and `photos-pre-npu.sqlite` before migration. They are backup-excluded app data.

## Runtime behavior

Search prefers the NPU on Tensor G5 when its model exists and its index is at least
as complete as the CPU/GPU index. It retries catchable model verification, native
initialization, inference, or library failures with the CPU/GPU model and matching
index. After a failure, the NPU is skipped for the current process until Retry NPU
or selecting NPU preferred again. A restart also permits a fresh attempt.

Cancellation and storage/data failures do not trigger acceleration fallback. A
fatal native crash cannot be caught by this Kotlin mechanism; Setup provides an
explicit CPU/GPU mode. If both models fail, manual keyword search remains available;
photo search reports an error instead of pretending to run semantic retrieval.

Index preparation saves both backends separately, skipping completed entries and
retaining partial work. Source changes invalidate both sets of vectors. Removed or
revoked photos are removed from both sets on library reconciliation. Missing fallback
models and incomplete coverage are visible in Setup/Photos.

## Verification

- Build, lint, and all 15 unit tests passed. Four new tests cover primary selection,
  retry on native failure, cancellation/data-error propagation, and both backends
  failing. The existing retrieval and photo-ranking tests remain passing.
- Isolated on-device database checks passed version-1 migration preserving old
  vectors, backend isolation, fingerprint mismatch, invalidation after source edits,
  resume, removal, and retry. Test databases were deleted afterward.
- Explicit app integration checks prepared the actual pack and ran photo requests
  `van pictures`, `inside the camper`, and `electrical wiring` on both backends.
  Each returned 24 results. Initial top-three set overlap was 3/3, 2/3, and 3/3
  respectively; rankings are close but not identical and are not held-out scores.
- Manual request `inverter operating instructions` returned the same top three
  physical pages (40, 80, 32) on both backends. These are development queries, not
  certified relevance judgments.
- A controlled NPU verification failure preserved the real model under a temporary
  name, substituted an invalid-size placeholder, and confirmed successful **CPU/GPU
  fallback** using the CPU/GPU index. The model was restored, reverified, and another
  search confirmed **Pixel NPU** recovery. The test finally restores NPU preferred.
- Final integration run passed after the final production changes. Example photo
  search totals ranged from 541–1125 ms on NPU and 411–1277 ms on CPU/GPU; initialization,
  cache state, and device scheduling make these unsuitable for a speedup claim.
- Final UI was inspected on the unlocked phone: `Last run: Pixel NPU`, matching van
  thumbnails, automatic scrolling to results, 48/48 counts for both backends, and
  Setup's NPU/CPU controls and model-availability labels. NPU preferred remains saved.
- APK permission audit still shows no Internet permission. No photos/documents were
  uploaded. Reports, UI captures, and raw test results remain in ignored `.tools`.

## Remaining limits

Repeat the new NPU build's spoken search, photo search, original viewing, and new
import/index flow after a disconnected restart before the podcast. Older offline
test reports do not substitute for this. Long-session memory/thermal behavior,
native driver-crash recovery, and held-out relevance evaluation remain unmeasured.

The earlier model-only NPU benchmark is documented separately in
[NPU-TEST-REPORT.md](NPU-TEST-REPORT.md). The current app loads/closes the selected
model per search, so the benchmark's ~3x inference gain is not a claim about total
search latency.
