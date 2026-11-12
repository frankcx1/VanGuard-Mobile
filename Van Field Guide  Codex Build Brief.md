# Van Field Guide: Codex Build Brief

An offline, voice-searchable binder for the equipment in Frank's Sprinter van, running on his Pixel 10 Pro XL. Speak a request and retrieve the correct saved manual page, schematic, or installation photo, with the original always available to inspect.

| Planning detail | Value |
|---|---|
| Prepared | October 12, 2026 |
| Status | Proposed vanlife use case; not implemented or device-tested |
| Working name | Van Field Guide |
| First equipment category | Heating system or inverter, chosen after identifying the installed hardware |
| First complete demo pack | One system's manual, schematic, and installation photos |
| Next expansion | Add the other system after the first passes |
| Core interaction | Push to talk, review transcript, search, open original |
| Implementation target | Native Kotlin/Compose Android app; all inference and retrieval available offline after preparation |
| Scope relationship | Supersedes the photo-only Pocket Recall MVP; PDFs, page-level retrieval, and microphone input are now core requirements |

This is a retrieval aid, not a diagnostic system or a remote control for van equipment. Equipment makes, models, electrical variants, installed configuration, and document revisions remain unconfirmed.

## Problem statement

The user wants access to pictures and documents while off-grid in a Sprinter van, including material for the inverter and heating system. The proposed application solves a specific inconvenience: finding the relevant part of already-saved information without knowing its filename, document title, page number, or exact terminology.

Its value depends on preparing an accurate local equipment pack before departure. It cannot retrieve a manual that was never saved, know an undocumented wiring modification, or inspect the van's actual condition.

### The real-world scene

Imagine a cold evening at an off-grid campsite, parked with no connectivity, and the heater does not start. Instead of scrolling through downloads and photos, the owner selects Heating and taps the microphone.

> “My heater isn't starting. Find the startup troubleshooting section and the wiring diagram.”

The app shows the recognized words for correction, then presents relevant saved material. It does not announce a fault diagnosis or invent a repair.

| Result type | Proposed result | What makes it useful |
|---|---|---|
| Manual page | Startup/troubleshooting section from the confirmed heater model's user guide | Opens the exact PDF page with surrounding pages available |
| Schematic | The identified heater wiring diagram, clearly labeled OEM reference or van-builder/as-installed drawing | Keeps a generic manufacturer's diagram distinct from this van's wiring |
| Installation photo | An owner-labeled photo of the heater/controller installation | Opens the original photo and its supplied label, not an inferred wiring interpretation |

The table illustrates the desired behavior, not results from actual manuals. No page number, hardware model, connection, or fault meaning has been established.

### Other useful requests

- **Inverter operation:** “Find the operating instructions for my inverter when I'm not plugged into shore power.”
- **Electrical references:** “Show me the inverter wiring diagram and my installation photos.”
- **Component location:** “Find the photo I labeled as the inverter compartment.”
- **Fault reference:** “Find this fault code in my heater manual,” followed by a reviewed or typed code.
- **Routine use:** “Find the instructions for changing the heater timer.”

Each request asks the app to locate evidence, not to conclude that an operating mode is safe or a component is faulty. Whether a relevant source exists and ranks correctly must be tested against the actual equipment pack.

## Goals

- **Retrieve useful evidence:** Put an accepted relevant source page or image in the top three for at least 8 of 10 held-out requests against the frozen starter pack.
- **Make voice work offline:** Complete a new spoken search after a disconnected phone restart, without online dictation or cloud recognition.
- **Preserve equipment identity:** Never silently use another model's instructions as a match for the selected installed component.
- **Make originals accessible:** Open every result's actual local page or full-resolution photo without depending on a cloud file provider.
- **Report honestly:** Record source coverage, setup effort, voice errors, retrieval errors, latency, and any manual correction.

Compare the experience with manually opening the saved manuals and with basic keyword search. The demonstration should show where semantic search helps rather than attributing every success to the embedding model.

## Non-goals

- **No AI mechanic:** No generated repair procedures, wiring changes, fault diagnosis, or bypass instructions.
- **No live knowledge:** No internet lookup, weather, location tracking, vehicle telemetry, or automatic knowledge of equipment state.
- **No equipment control:** No Bluetooth/CAN integration, switching, configuration writes, or actions on heating/electrical hardware.
- **No continuous listening:** No wake word or always-on microphone.
- **No conversational generation:** Voice input is core; a speaking chatbot is not.

## User stories

- **Off-grid owner:** As the owner, I want to describe the reference I need while disconnected so I can stop hunting through files.
- **Careful reader:** As the owner, I want the exact model, revision, and source page visible so I can judge applicability.
- **Voice user:** As the owner, I want to correct a misheard brand, model, or fault code before searching.
- **Prepared traveler:** As the owner, I want a readiness checklist before leaving service so I know which files and speech resources are actually available.
- **Fallback user:** As the owner, I want typed search and ordinary document browsing even if speech or semantic inference fails.

## Requirements

### Must have: P0

| Requirement | Given / when / then acceptance criterion | Dependency |
|---|---|---|
| Equipment profile | Given imported material, when it is assigned to Heating or Inverter, then the owner confirms its make/model and applicability; unknowns remain visibly unverified | Actual hardware identity |
| Explicit local import | Given selected PDFs and photos, when setup completes, then all accepted original bytes are in app-private storage and reopen after restart | File import |
| Page-level document retrieval | Given a saved manual and a relevant query, when it ranks, then the result opens the actual matching page, not merely the file's cover | Extraction and page index |
| Diagram/photo retrieval | Given an indexed schematic or installation image, when it ranks, then its original, supplied labels, and source type remain visible | Image embeddings and provenance |
| Offline voice | Given prepared speech resources and microphone permission, when a user speaks while disconnected, then a reviewable transcript is produced locally and used for retrieval | Speech compatibility spike |
| Typed fallback | Given denied microphone permission, unavailable speech, or noise, when the user types, then the same retrieval and viewer remain usable | Query UI |
| Model-scoped retrieval | Given a selected component, when searching, then incompatible or unverified equipment sources are excluded by default or explicitly require review | Equipment/source bindings |
| Accurate source display | Given a result, when opened, then its title, component, revision if known, physical PDF page index, and original are available | Source metadata |
| Exact-identifier handling | Given a fault/model code, when spoken or typed, then it is reviewable and receives lexical lookup; semantically similar but different codes are not claimed as exact matches | Transcript and text index |
| Honest readiness | Given a readiness check, when complete, then downloaded bytes, extraction/index coverage, and actual offline voice/search test status are reported separately | Diagnostics |

### Should have: P1

- **Second system:** Add the inverter after the heater, or vice versa, preserving strict component boundaries.
- **Favorites:** Pin a few frequently used pages and installation photos for direct access without running a model.
- **Scanned-page OCR:** Add bundled OCR if the starter pack contains image-only manual pages that need text search; promote this to P0 only if necessary for the selected documents.
- **Benchmark export:** Export a redacted report only after an explicit user action.

### Could have: P2

- **Offline read-aloud:** Read a user-selected source excerpt verbatim using a separately verified local voice, with its source displayed.
- **Simple voice navigation:** Support fixed commands such as “open the first result,” without requiring an LLM.
- **More systems:** Add water pump, solar controller, batteries, refrigerator, and other confirmed hardware.

## Success metrics

All thresholds are proposed targets, not measured results. Freeze the equipment pack and evaluation queries before measuring.

| Metric | Target / reporting requirement |
|---|---|
| Retrieval quality | Relevant source in top three for at least 8/10 held-out requests |
| Equipment correctness | No incompatible-model source presented as verified for the selected component |
| Voice usability | At least 8/10 spoken requests usable without editing; report raw accuracy and post-correction retrieval separately |
| Critical identifiers | Report exact recognition of model/fault codes separately; do not hide failures inside average transcript quality |
| Warm typed search | Target ≤2 seconds p95 from Search to visible results |
| Voice end-to-results | Target ≤5 seconds p95 after the user finishes speaking; measure transcription and retrieval separately |
| Offline operation | New speech, retrieval, original viewing, and at least one new local import/index test pass while disconnected |
| Preparation | Report download sizes, full-pack indexing time, supported/indexed/failed page counts, and corrections needed |
| Resource use | Report named process-memory metrics, storage use, and sustained-use/thermal observations on the actual Pixel |

For small test samples, report individual timings as well as percentiles. Do not claim that a quiet-room voice result establishes performance over a running fan or outdoors.

## Open questions

| Question | Owner | Blocking status |
|---|---|---|
| What are the inverter and heater makes/models, including controller and electrical variant where relevant? | Frank | Blocks authoritative source selection, not the app scaffold |
| Which manuals and diagrams are already saved, and are any van-builder/as-installed drawings available? | Frank | Blocks preparation of the real demo pack |
| Do the PDFs have text layers, or are they scanned images? | Codex after import | Determines whether OCR is necessary in P0 |
| Which system should lead the demo? | Frank | Default to whichever has the clearest verified source pack |
| Which local speech service, language resources, and runtime work on the actual Pixel? | Codex + device test | Blocks voice-success claims |
| Will the first version read anything aloud? | Frank | Non-blocking; default is voice in, evidence on screen |

## Technical design

### Separate listening, finding, and viewing

```text
Push-to-talk
    |
    v
On-device speech recognition
    |
    v
Editable transcript + explicit selected component
    |
    v
Text query embedding + local lexical lookup
    |
    v
Component-scoped local index
    |
    +--> Manual passages / PDF pages
    +--> Schematics
    +--> Installation photos
    |
    v
Local original viewer with provenance
```

Use Kotlin/Compose, a small Room/SQLite database, and an app-owned adapter around a verified Android embedding runtime. Start with one module and exact local vector search, not a backend, vector server, agent framework, or web wrapper.

### Embedding model

The LiteRT model specifications list EmbeddingGemma 2 Text Vision 440M with a 388 MB CPU/GPU download ([model specifications](https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm)). Use that as the initial candidate, with the actual runtime/artifact version, digest, and input conventions pinned after a device spike.

The embedding API returns vectors for inputs; the documented interface is not a speech-to-text transcript API ([LiteRT-LM embedding guide](https://developers.google.com/edge/litert-lm/embedding_models)). For this application, transcribe voice separately and feed the reviewed text to search rather than assuming audio embeddings will give a correctable transcript.

The model card and runtime guide currently show different search-query prefix conventions ([model card](https://huggingface.co/google/embeddinggemma-2), [runtime guide](https://developers.google.com/edge/litert-lm/embedding_models)). Resolve the convention against the selected artifact/reference implementation and validate it on development examples before evaluation.

Keep image and document embeddings tied to the same compatible model/configuration fingerprint. A changed model, dimension, preprocessing policy, or input-template policy requires deliberate index invalidation rather than silently mixing representations.

### Offline speech

Android documents `isOnDeviceRecognitionAvailable`, `createOnDeviceSpeechRecognizer`, speech-support checks, model-download requests, and the `RECORD_AUDIO` permission ([SpeechRecognizer API](https://developer.android.com/reference/android/speech/SpeechRecognizer)). Use the explicit on-device recognizer as the first compatibility candidate, provision needed language resources before departure, and verify actual operation with connectivity disabled.

Android also warns that `EXTRA_PREFER_OFFLINE` may have no effect depending on the recognizer implementation ([RecognizerIntent API](https://developer.android.com/reference/android/speech/RecognizerIntent)). Do not treat that flag on a generic recognizer as proof of an offline implementation.

- **Interaction:** Push to talk with a visible listening state, a short timeout, Stop/Cancel, and a final editable transcript.
- **Lifecycle:** Follow the API's required thread/lifecycle rules; release speech resources when finished and handle interrupted listening explicitly.
- **Equipment terms:** Allow review of brand names, model strings, “inverter” versus “converter,” and alphanumeric identifiers.
- **Failure:** If speech is unavailable, say so and offer typing; do not silently fall back to a remote recognizer.
- **No retained recordings:** The app must not persist raw audio by default; document the selected speech-service boundary rather than making a whole-device privacy claim.
- **Budget:** Measure speech and embedding resources together; a model download size is not the app's total memory footprint.

The first phone test decides whether this service is sufficient. If it is not, evaluate a pinned app-owned offline recognizer as a separate implementation decision, not a hidden runtime fallback.

### PDFs, diagrams, and images

Android documents page rendering and, from API 35, text-content and text-search methods on `PdfRenderer.Page` ([PDF page API](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer.Page)). Verify the target phone's API support and use page-aware local extraction/rendering; do not assume an image-only page has a searchable text layer.

ML Kit provides a bundled text-recognition option with immediately available model resources, distinct from the Play Services option that may require a later download ([Android text-recognition guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android)). If OCR is needed, use the bundled path or another verified local implementation and test it offline before marking a page ready.

- **Text passages:** Start with roughly 250–400-token chunks inside page boundaries, keeping nearby headings and moderate overlap; tune only on development examples.
- **Page references:** Store the physical PDF page index and a printed page label when reliably available; never fabricate a printed page number.
- **Schematics:** Index designated diagram pages as images and any available titles/labels; do not infer circuit connectivity, polarity, wire ratings, or electrical safety.
- **Photos:** Preserve the original, component association, date if known, and owner-supplied caption; make manual labels distinguishable from extracted text.
- **Scans:** Label text as OCR-derived and allow inspection of the actual page; do not silently “correct” an ambiguous fault code or technical label.
- **Rendering:** Index and render bounded pages sequentially, never an entire long PDF in memory; use safe zoom/tile handling for detailed viewing.
- **Coverage:** Keep complete originals available to browse and display all unsupported or unindexed pages; no hidden truncation of the manual.

### Hybrid retrieval and source boundaries

Use semantic retrieval for descriptive requests and lexical lookup for exact model strings, part numbers, and fault codes. Rank manual passages separately from diagram/photo evidence, then present grouped results so a visually similar photograph does not bury the relevant operating manual.

Within each group, combine lexical and semantic ranks through a documented, testable rule rather than adding incompatible raw scores. Display “closest saved references,” not percentage confidence or a diagnosis.

Bind every source to a stable component ID, with make/model, variant, source type, revision, applicability status, and original-file hash. Keep OEM instructions, van-builder drawings, owner photographs, and owner notes visibly distinct; do not resolve a conflict by assuming one is authoritative.

An equipment/source mismatch should block verified-result status. If no compatible source is available, say that rather than silently borrowing a similar model's guide.

## The equipment pack

Prepare the smallest useful pack for one confirmed system before expanding. Hand-labeling which files belong to the actual heater or inverter is legitimate setup work, but should not be misrepresented as the model independently identifying the hardware.

| Item | Minimum preparation |
|---|---|
| Equipment identity | Owner-confirmed make/model, relevant variant, and controller identity; a label photo is helpful |
| User guide | Correct model/revision PDF with complete original retained |
| Schematic | Applicable OEM diagram and, if available, a separate builder/as-installed drawing |
| Installation photos | About 5–10 useful existing photos, assigned to the component with honest owner labels |
| Provenance | Original URL or supplied filename, revision/date if known, local file hash, and confirmation status |

Use non-sensitive sample documents during coding and the confirmed real pack for the final device test. Do not upload private installation photographs or van documents into Codex/GitHub merely to develop the importer.

## Build sequence

### Prove the two risky integrations

Build a minimal device test with one real PDF, one diagram, one photo, and typed plus spoken queries. Prove native embeddings and on-device speech independently before connecting them.

Exit only after actual Pixel tests pass, or leave those results explicitly pending if the device is unavailable. A compiled UI and a mocked transcript are not a voice-search demonstration.

### Build the scoped offline binder

Implement the component profile, explicit local import, page/text extraction, image indexing, lexical retrieval, and original viewers. Add progress, resumable indexing, configuration fingerprints, error states, and app-copy deletion without touching source originals.

Keep typed search and manual browsing working even when the model is unavailable. These are resilience features, not substitutes for testing the semantic-search goal.

### Connect voice and readiness

Add the microphone permission flow, push-to-talk, transcript review, selected-component context, and grouped results. A “Trip ready” view must distinguish files copied, sources confirmed, pages indexed, speech resources provisioned, and an offline test actually passed with the current configuration.

If the user says “inverter” while Heating is selected, make the mismatch visible instead of silently applying the wrong scope. Do not add an LLM simply to manage two component choices.

### Run the off-grid test

Prepare the full starter pack while online, then turn on airplane mode and explicitly disable Wi-Fi and other network paths. Restart the phone, launch the app, ask an unseen spoken request, and open the original source.

Repeat with a misheard identifier, a missing manual, and a similar but incompatible model document. Also import and index one new already-local photo or page while disconnected so the evidence is not limited to cached results.

## Safety, privacy, and evidence requirements

- **No generated technical advice:** Return source excerpts and originals, not synthesized repairs or inferred wiring instructions.
- **Preserve context:** Keep warnings and neighboring source pages accessible; a search snippet must not pretend to be a complete procedure.
- **No internet permission:** Audit final merged APK manifests and dependencies; the app must not request `INTERNET`.
- **Separate service boundary:** A system speech service is a dependency outside the app's manifest boundary, so the app's missing permission alone does not establish speech isolation; test the complete disconnected flow.
- **Private storage:** Store originals, derivatives, and indexes locally; configure backup/transfer exclusions and avoid analytics, upload logging, and remote crash reporting.
- **Minimal permissions:** Microphone is justified for explicit voice input; location, whole-library access, contacts, and vehicle-control permissions are not.
- **No unrequested export:** Any sharing of documents or diagnostics requires an explicit action and destination review.
- **Honest test provenance:** Show whether a hit was supported by native image embeddings, extracted text, OCR, lexical matching, or owner metadata.
- **No emergency claim:** This is a convenience/reference tool, not an emergency service, safety-certified aid, or assurance that equipment is safe to operate.

This plan calls for no physical changes or repairs to the van's equipment. Its proposed build/testing scope is the phone app and stored information only.

## Codex handoff prompt

```text
Read Van_Field_Guide_Codex_Build_Brief.md as the primary product scope.
It replaces the earlier photo-only Pocket Recall assumptions: PDFs,
page-level source retrieval, component identity, and offline voice are core.

Build a small native Kotlin/Compose Android prototype for a Pixel 10 Pro XL.
Start with one owner-confirmed van system and its manual, schematic,
and installation photos. Do not invent the actual equipment models.

First prove real on-device embeddings and real offline speech independently.
Pin published dependencies and model artifacts. Use explicit on-device
speech recognition with preflight resource checks, not generic online
dictation plus an offline preference flag.

Implement local originals, page-aware extraction, semantic and exact-text
retrieval, component-scoped grouped results, and original-page/image viewers.
Show a correctable voice transcript. Keep typed search and ordinary document
browsing as fallbacks.

No cloud backend, remote recognition fallback, fake embeddings, generated
repair instructions, equipment control, or INTERNET permission in the app.
Treat the system speech service as a separately tested dependency.

Produce a reproducible project, APK, setup guide, model/dependency lock,
permission audit, and honest test report. If the phone or real source pack
is unavailable, use clearly labeled safe fixtures and mark device/source
validation pending. Never claim successful offline execution without it.
```

## Podcast framing

“My next local-AI experiment is an offline field guide for my Sprinter. If I'm off-grid and need the heater manual or inverter wiring diagram, can I ask my phone and get the exact saved page plus my installation photos?”

“Not an AI mechanic, and not a chatbot guessing at repairs. Just the right information, for my actual equipment, when I can't get online.”
