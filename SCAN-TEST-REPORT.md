# Photo to saved manual: 0.5.0

Installed on the owner's Pixel 10 Pro XL (Tensor G5). APK SHA-256:
`89da52908221669fae64503b53d803d468bb48ed7b2f942f46bc31917ef71c94`.

## Delivered behavior

The Scan tab offers Take photo (Android camera with full-size FileProvider output)
and Choose photo (document picker). Decoding respects image orientation and limits
the longest edge to 1600 pixels. Bundled Latin OCR extracts label text; Gemma performs
fresh image inference and compares it with equipment descriptions and the matching
manual passage vectors. Existing NPU-first selection and CPU/GPU fallback apply.

The screen separates exact full-model matches, incomplete model-family matches,
possible visual/brand matches, conflicting RIV model identifiers, and no match.
Visual similarity is a heuristic with broad negative alternatives, not a calibrated
confidence score or exact equipment identification. Editing label text invalidates
the prior match; Check label reruns identity matching and marks the text as edited.

Candidate results link to the saved REGO A0 original, the reviewed OEM wiring
diagram on physical PDF page 14, the existing manual search, and up to three
image-to-text reference pages. Only this reviewed manual is supported. A conflicting
model receives no candidate/manual shortcuts. OCR can still match identifiers when
Gemma is unavailable. No manual is downloaded and no repair text is generated.

Camera output is restricted to the app's capture cache directory and deleted after
analysis, failure, or cancellation. Clearing removes the in-memory preview, label,
and results. Existing manual and photo indexes are read without schema changes.
The APK has no Internet permission. The OCR model is bundled, as described in
[Google's Android text recognition guide](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).
OCR is separate from Gemma; only the Gemma backend is labeled Pixel NPU.

## Verification

- Build and lint passed (existing dependency/version and other warnings remain).
- All 20 unit tests passed, including five new identity-matching checks covering
  exact identifiers, OCR whitespace, incomplete family, other models/variants,
  multiple conflicting identifiers, fuzzy-digit rejection, and visual-only states.
- On-device scan integration passed twice. The final run includes capture cleanup.
- Generated, clearly printed label images were recognized by the bundled OCR on
  the Pixel. Both NPU and CPU/GPU scans matched the exact expected model and
  returned three actual saved manual pages. A different RIV number was rejected;
  an incomplete number was labeled family-only. A white blank image returned no
  equipment candidate. These fixtures are not photos of the installed inverter.
- Real URI decoding through the Scan view model passed, as did label edits removing
  stale matches, conflicting edited text, and clearing the scan.
- FileProvider URI creation, cancelled capture, writing a generated image through
  the capture URI, successful capture-result processing, and deletion of the
  temporary JPEG passed on-device. This tests the camera output contract without
  claiming an actual physical camera photograph.
- Initial generated-label NPU scans took about 2.2–2.7 seconds total, including
  roughly 600–628 ms of embedding calls. CPU/GPU's generated-label scan took about
  8.2 seconds total. These are uncontrolled development observations; initialization,
  OCR, and cache/device state contribute to latency. Timings appear in the app.
- Final preference remains NPU preferred. Existing 177-passage and 48-photo indexes
  were retained. Raw scan evidence is private in `.tools/scan-validation.json`.

## Remaining checks

The phone was locked during this turn. Actual screen/camera-app interaction was
not visually inspected. The camera output contract and scan processing were tested
through instrumentation. Photograph the installed inverter and its model label,
then verify the original manual and diagram links on the phone. Real-equipment
visual-match relevance and label quality remain unmeasured. A disconnected restart
and complete podcast rehearsal remain pending; no full offline rehearsal is claimed.

The model still opens per request. This change does not add a persistent warm model,
general-purpose equipment recognition, or a multi-manual catalog.
