# VanGuard Mobile

An offline manual finder for a camper van, running entirely on an Android phone.
Ask a question by voice or text and it returns the right page of your saved
manuals, including wiring diagrams. The model, the manuals, and the search index
all stay on the phone, and the app has no Internet permission.

Watch it work: https://youtube.com/shorts/HYHw12FegUo

This is a working prototype built for one phone (a Pixel 10 Pro XL, Tensor G5) and
one van. It is published as a blueprint to copy and adapt, not as a finished product.

Not in this repository:

- Equipment manuals and photos. Bring your own; see "Local equipment documents".
- Model weights. `scripts/Get-Model.ps1` downloads and verifies the pinned
  EmbeddingGemma files, which are covered by Google's Gemma terms, not this
  repository's license.

## Project notes

The app's on-screen name is **Van Guard**. Native Kotlin/Compose. The product scope is
[Van Field Guide Codex Build Brief](Van%20Field%20Guide%20%20Codex%20Build%20Brief.md).
The first demo system is the inverter. The owner has confirmed its installed
make/model and 12V/3000W identity; the equipment profile is recorded privately in
`local-materials/source-inventory.json`. Source revision and variant applicability
are tracked separately from equipment identity.

## Current milestone: 0.6.1 Van Guard identity

The Pixel launcher name is **Van Guard**. The adaptive launcher icon uses generated
camper-van artwork, with a separate monochrome van silhouette for Android themed
icons. The shared Compose theme uses forest green, warm cream, and copper, rounded
cards, a branded header, and bottom navigation: Find, Guides, Photos, Scan, Setup.
The header retains the actual compute-backend status. The application ID remains
`com.vanfieldguide`, preserving installed models, private manuals, and photo indexes
when updating the existing app. No retrieval or model behavior changed in this release.

Version 0.6.1 enlarges the header artwork, app title, and subtitle, and replaces the
initial manual-coverage message on Find with "Local AI App showcasing Gemma" and
the model name, EmbeddingGemma 2 Text Vision (440M parameters). Search progress and
result messages remain visible during use; detailed coverage stays in Guides/Setup.

Artwork and the exact built-in image-generation prompt are tracked in
`app/src/main/res/drawable-nodpi/van_guard_art.png` and
`artwork/van-guard-icon-prompt.txt`.

Version 0.6.0 built and passed Android lint, and was installed over the existing app
on the Pixel on October 15. APK metadata confirms the Van Guard label, adaptive icon,
and no Internet permission. The phone relocked during the build, so visual inspection
of this version's launcher and screen layouts is still pending an unlock.

## Photo to saved manual (0.5.0)

**Scan** accepts a full-resolution camera capture or a chosen local image. Bundled
ML Kit Latin text recognition reads labels without a model download. The existing
Gemma NPU-first path compares image content to equipment descriptions and to the
matching manual passage index, with CPU/GPU fallback. The screen reports inference,
label-reading, and total elapsed times separately.

An exact full model number, incomplete family number, possible visual/brand match,
conflicting identifier, and no match have distinct states. Visual matching uses an
uncalibrated retrieval heuristic and never confirms the model. Recognized text can
be corrected; editing invalidates the previous result until Check label is tapped.
Only the reviewed REGO manual is currently supported. Candidate cards open its
original, OEM wiring diagram, or existing manual-search screen. A conflicting model
does not receive a candidate card. No online manual discovery or generated repair
advice is included.

Camera output uses a narrowly scoped FileProvider cache directory and is deleted
after analysis or cancellation; the preview and results last for the current app
session and can be cleared explicitly. Selected source photos are not modified.
The app still has no Internet permission. See [SCAN-TEST-REPORT.md](SCAN-TEST-REPORT.md)
for validation and the remaining real-equipment rehearsal.

## NPU-first search with CPU/GPU fallback

On Tensor G5, the app now prefers the verified Google Tensor NPU model for text
and images. The CPU/GPU model remains available for fallback. Setup exposes
**NPU preferred** and **CPU/GPU**, imported-model availability, and Retry NPU after
a runtime failure. Search status and the header identify the backend actually used.

Version-2 databases preserve existing CPU/GPU vectors and add separate NPU vector
and fingerprint columns. Indexing prepares both available backends and saves every
completed entry. Searches pair the query model with its matching index; a less
complete NPU index does not take precedence over a more complete CPU/GPU index.
Catchable model-validation, initialization, inference, and native-library failures
retry the whole search against the CPU/GPU model and index. A failed NPU is avoided
for the rest of that process until Retry NPU. Cancellation and data/storage errors
are not treated as accelerator failures. Fatal native process crashes cannot be
caught by this Kotlin fallback; CPU/GPU can be selected explicitly in Setup.

On the Pixel, 177/177 manual passages and 48/48 selected photos are indexed for
both paths. The actual app passed NPU/CPU searches, controlled automatic fallback,
and NPU recovery. See [NPU-APP-TEST-REPORT.md](NPU-APP-TEST-REPORT.md).

Text and image inference ran on the Pixel with real Gemma vectors. The owner also
reported successful speech and offline integration tests. Full binder acceptance
is a separate test, still pending; see `TEST-REPORT.md` for evidence boundaries.

The Pixel's local database now confirms 100 extracted pages and 177/177 persisted
text embeddings. A connected-device search and original page-14 viewer have been
visually verified. This is development evidence, not a held-out retrieval score.

The app has Find, Guides, Photos, Scan, and Setup navigation. Clear clears the typed or
spoken query and result sections. Setup explicitly imports the complete
reviewed REGO manual into backup-excluded app-private storage, verifies its exact
SHA-256, extracts every physical page with Android's PdfRenderer, and records
text/empty/error coverage in local SQLite. Only that reviewed manual is accepted
in this starter build; other models and revisions are rejected. Source metadata
identifies the owner-confirmed component, OEM provenance, A0 revision, and hash.

Keyword search is available after extraction. Semantic indexing computes real
Gemma vectors for bounded passages inside page boundaries and commits each one
individually. Extraction and indexing can resume after interruption. Find supports
reviewed speech or typed queries and an explicit keyword-only baseline. Results
open actual original PDF pages; Browse exposes the complete manual and an
explicitly curated shortcut to its OEM wiring diagram. Neighboring pages, pinch
zoom/pan, and a higher-resolution detail rendering remain available in the viewer.
The detail bitmap is bounded at 2200 pixels wide / 3300 high; tiled PDF rendering
is not implemented yet.

For diagram/wiring requests, Find shows a separate Saved diagrams section before
text results, with previews rendered from the actual PDF. The reviewed catalog
currently includes the inverter diagram on physical page 14 and automatic generator
start diagram on page 44. Cards explicitly say they match saved labels; they are
not native image-retrieval results. Exact identifiers still constrain these cards,
and pages missing from the current index cannot appear. Text results initially
show three compact excerpts, with an option to display the remaining pages.

The separate speech path uses Android's explicit on-device recognizer, resource
checks, English resource preparation, Stop/Cancel, and a 20-second timeout.
Permission denial preserves typed input. Setup retains the original text/image
diagnostics. The image probe is transient; Photos now creates a separate persistent
image index using the same verified Gemma model.

### Device photo search

Photos requests Android photo access only when **Choose photo access** is tapped.
Selected and full access are supported. It enumerates accessible, non-trashed,
non-pending local MediaStore images; cloud-only Google Photos items are excluded.
**Build / resume photo index** processes one image at a time, saves each completed
768-dimensional embedding, shows coverage and unreadable counts, and supports
Pause. Leaving the foreground pauses work; while processing, the screen stays on.
Keep the phone on this screen for indexing. Completed work survives app restarts.

Image inputs use orientation-aware Android ImageDecoder, software sRGB decoding,
a maximum 1280-pixel edge, and JPEG quality 90. Inference prefers NPU text and vision
on Tensor G5; fallback uses CPU text / GPU vision. Each model has its own photo
configuration fingerprint and stored vectors.
Queries use Gemma text embeddings and cosine ranking against image embeddings;
filenames do not affect rank. Results show up to 24 closest photos, not certainty
scores or verified component identities. The photo collection is separate from
the identity-scoped inverter manual.

The index stores metadata and vectors in backup-excluded app-private SQLite.
Original photos stay in the device library: they are **not imported or duplicated**
into the binder. A refresh runs before indexing/search and on resume, invalidating
changed media and removing deleted or no-longer-accessible entries. Original access
therefore depends on retaining the photo and its permission. The preview supports
pinch/pan up to a 2200-pixel edge; **Open original in gallery** hands the original
local URI to a viewer. **Remove index** deletes only this photo index.

This device-library feature extends the original brief; app-private original photo
imports, component assignment, and labels for a frozen offline equipment pack
remain separate pending work. Photo ranking quality and disconnected photo search
must be checked on the owner's actual collection before podcast claims.

Pending: multi-source and diagram image retrieval, photo-pack imports, source editing, OCR if
needed, persisted benchmark evidence, held-out evaluation, and a full disconnected
binder rehearsal. The OEM diagram shortcut is manually designated, not an image
retrieval result. There are no generated explanations or repair instructions.

### Ranking and index policy

Passages are at most 1100 characters with approximately 150 characters of overlap,
never crossing PDF page boundaries. No manual tail is discarded. Text embedding
uses the pinned 768-dimensional models with separate, matching NPU and CPU indexes,
the 1024-token input signature,
and the runtime guide's explicit query/document prefixes. The model-card prefix
discrepancy still needs development comparison before a frozen evaluation.

Lexical scoring uses BM25 (k1=1.2, b=0.75). Hybrid search combines lexical and cosine
similarity ranks with reciprocal-rank fusion (k=60), then returns distinct physical
pages. Any token containing digits must occur exactly on the candidate page;
semantically similar codes do not bypass this filter. These are closest saved
references, not confidence scores. Wrong-component heating requests are flagged.
Vectors are only loaded when the stored model/runtime/template/chunk/context
fingerprint matches. Semantic failure produces a labeled keyword fallback.

Queries currently load and close the selected model for each search. Whole-search
latency includes this initialization and is not claimed to improve by the model-only
microbenchmark ratio. Persistent warm engines are not implemented.

## Local equipment documents

The owner's supplied collection is in `Sprinter/`, which is entirely excluded
from version control. Keep those originals in place. Additional material can go
there, in the project base folder, or in `local-materials/`. The latter
is also entirely excluded from version control. Common document/image formats in the
base folder are also ignored. Avoid committing any owner documents or photographs.
Nothing here uploads those files or bundles them into the APK.

The supplied collection includes the candidate operating manual and diagrams.
The matching-model manual is the first starter-pack candidate; the other inverter
model's manual is excluded. Review exact source variant/revision and select a few
installation photos with owner labels. An OEM diagram and as-installed drawing remain distinct.
Do not assume a downloaded manual applies merely because its filename looks right.

## Build

An isolated Tensor G5 NPU experiment successfully ran real text and image embeddings
on the Pixel. See [NPU-TEST-REPORT.md](NPU-TEST-REPORT.md) for timings, evidence, and
limitations. The optional probe is separate from the NPU-first app integration.
Reproduce with `scripts/Test-Npu.ps1 -Device 'IP:PORT'` (Tensor G5 only).

Requires JDK 17, Android platform 36, build-tools 35.0.0, and an arm64 phone running
Android API 35 or later. The Gradle wrapper pins Gradle 8.13 and its distribution
checksum. Direct plugin/library versions are pinned in the Gradle files.

On this workstation, JDK and Android command-line tools are project-local in
`.tools/`; no global PATH change is required:

```powershell
.\scripts\Build.ps1
# With the Pixel connected, unlocked, and USB debugging authorized:
.\scripts\Build.ps1 -Install
```

On another workstation, install the listed tools and set `JAVA_HOME` and
`ANDROID_HOME`, or open the project in a compatible Android Studio. The same
script works with externally configured tools. Builds initially need network
access to download development dependencies; the phone app has no Internet
permission. SDK license acceptance is required on a newly configured workstation.

APK: `app/build/outputs/apk/debug/app-debug.apk`.

When wireless ADB has stale transports, specify the active device explicitly with
`adb -s IP:PORT install -r app/build/outputs/apk/debug/app-debug.apk`. The connection
port can change after restarting the phone.

Fifteen unit checks run with `./gradlew :app:testDebugUnitTest`. They cover
wrong-code exclusion despite semantic similarity, complete model identifiers,
duplicate-page collapse, page-wide identifier matching, chunk coverage, and vector
dimension/normalization behavior, diagram-label matching, and ensuring curated
diagram cards cannot bypass identifier or source-page constraints, bounded photo
ranking, malformed vectors, and stable ties. An isolated Android instrumentation
runner (`PhotoStoreDeviceTest`) checks real SQLite persistence, resume, source edits,
fingerprint mismatch, removed access, retry, and index removal without touching the
owner's databases, including schema upgrades and separate backend fingerprints.
Four fallback tests cover primary selection, native failures, cancellation/data
errors, and failure of both backends. The explicit `npu-app` instrumentation mode
prepares and checks the owner's actual pack; it is not part of the isolated tests.

## Model preparation

`model-lock.json` pins the repository revision, artifact, size, and SHA-256.
The Tensor G5 model is preferred on the supported Pixel, with the CPU/GPU model
retained for fallback (CPU text inference and GPU vision configuration). Each
model has its own index. See `NPU-TEST-REPORT.md` for isolated inference timings
and `NPU-APP-TEST-REPORT.md` for integrated app validation and timing limitations.

```powershell
.\scripts\Get-Model.ps1 -IncludeNpu
```

Copy both verified model files to the phone's Downloads folder, then use
**Import NPU or CPU/GPU model** in Setup for each. The importer identifies the model
by exact hash and size, stores it privately, and rejects the Tensor G5 artifact on
other SoCs. Build / resume the manual and photo indexes to prepare both backends.
Unpinned model variants are rejected. CPU/GPU can still be used without the NPU file.

The query prefix is an explicitly provisional convention taken from Google's
runtime guide. Resolve its discrepancy with the model card before building and
evaluating the retrieval index. A successful vector computation does not validate
ranking quality. A future index must include the model, input policy, dimensions,
and preprocessing configuration in its fingerprint.

## Device test

1. Install the APK and import the verified model.
2. Run typed and image embedding tests and record initialization/inference time.
3. Check speech resources; request English preparation if needed while online.
4. Speak a phrase and review the editable transcript. Test a model or fault code.
5. Enable airplane mode, explicitly disable Wi-Fi and other network paths, and
   restart the phone.
6. Launch the app; speak a new phrase and run its embedding. Record each result.
7. Test denied microphone permission and interrupted/canceled recording.

The system recognizer is outside the app's own permission boundary. Its resource
availability alone does not prove disconnected speech works. The app never stores
raw audio, but a whole-device privacy claim requires separate service validation.
For the binder test, select the complete REGO A0 PDF in Setup, inspect extraction
coverage, and build the Gemma index. Compare development requests in keyword-only
and hybrid modes, open results and neighboring pages, then repeat after a
disconnected restart. Include a new local import and an incompatible-manual
rejection. Freeze separate held-out queries before measuring retrieval quality.

## References

- [Model artifact](https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm)
- [Embedding runtime API](https://developers.google.com/edge/litert-lm/embedding_models)
- [Android on-device speech](https://developer.android.com/reference/android/speech/SpeechRecognizer)

## License

MIT. See [LICENSE](LICENSE).
