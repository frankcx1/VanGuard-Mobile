# Manual binder 0.2 / 0.2.1 validation

This report describes the first binder build, separately from the 0.1 integration
tests recorded in `TEST-REPORT.md`.

## Workstation checks

- Build and Android lint succeeded; lint reported 0 errors and 13 warnings.
- Six retrieval unit tests passed with no failures or errors.
- Packaged APK permissions audited with Android aapt: RECORD_AUDIO and AndroidX's
  app-specific signature permission; no INTERNET permission.
- The supplied REGO manual's cover, introduction, and technical specifications
  were reviewed against the owner-confirmed 12V / 3000W / US inverter. OEM diagram
  provenance remains distinct from the installed van's wiring.
- Complete source PDF transferred to the Pixel Downloads folder. No PDF bytes are
  included in the APK or committed to version control.
- APK installed successfully over paired wireless ADB; Android reported launch
  success. Existing app-private Gemma model storage was preserved by the update.

Initial 0.2 APK SHA-256:
`097cc64a86f3dd08d970039a0c30aa3ae180134cbb2d3ed405d5fa1bd7fb17f3`.

## Device evidence after 0.2.1

Version 0.2.1 APK SHA-256:
`5afc70d1a7997e1e6591aa7ee86b0913d556140e657e763900560342e9d23abb`.
Build and lint completed with 0 errors, 13 warnings, and 1 hint.

- The connected Pixel's app-private SQLite snapshot shows **100 pages with text**
  and **177/177 passages with stored embeddings**. Page 14's extracted text is
  present. The reported missing diagram was not missing from the index.
- Inspection of the original results screen showed large text-only excerpts from
  diagram pages. Those excerpts obscured the fact that the original contained a
  drawing. This was a presentation problem; the original query's rank order was
  not captured, so no claim is made about its position in that search.
- Version 0.2.1 adds real PDF previews in a distinct Saved diagrams section, matched
  by reviewed source labels, plus three compact initial text results and Show more.
  The existing embedding fingerprint is unchanged; all imported data survived the
  package update without reindexing.
- A typed development request, "Find the inverter wiring diagram", returned two
  saved diagram cards and ten hybrid text results. The app reported **823 ms** for
  that one search (preview rendering completes separately). This is not p95.
- The first card showed the actual inverter drawing, labeled **PDF page 14**.
  Tapping Open diagram displayed the complete original page in the viewer. Both
  the preview and original were visually inspected on the connected Pixel.
- Eight unit tests passed; identifier filtering also applies to curated diagrams.
- The updated APK again passed the no-INTERNET packaged permission audit.

Diagram selection is reviewed metadata matching, not native image embedding
retrieval. This remains visible in the app and must be described honestly in the
podcast. These development queries cannot count as held-out evaluation requests.

## Checks still pending

The full binder's disconnected flow, cancellation/restart persistence under
interruption, additional viewer gestures, and held-out ranking evaluation remain
pending. The original import/index actions were performed by the owner; completed
coverage was subsequently inspected in the app-private database.

The user reported both preceding integration tests passed offline. That does not
automatically verify this new manual-search build. No held-out retrieval accuracy,
p95 latency, peak memory, or thermal claim is available.

## Scope and limitations

This first binder accepts exactly the reviewed source hash. It does not silently
bind an arbitrary PDF to the confirmed equipment. Adding another source requires
its own applicability metadata and import support. The original 100-page manual
is retained and all pages are exposed in Browse, including pages with no text or
extraction errors. Image/photo search and OCR are not implemented in this build.

Semantic prefixes follow the runtime guide and are fingerprinted as a development
candidate; the model-card discrepancy remains pending development comparison.
The original diagram page shortcut is hand-selected setup work. Search performance
and visual PDF detail remain to be measured on the Pixel.
