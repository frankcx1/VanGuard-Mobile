# Tensor G5 NPU feasibility test

This records the isolated feasibility experiment. The NPU backend has since been
integrated into app 0.4.0; see [NPU-APP-TEST-REPORT.md](NPU-APP-TEST-REPORT.md).

Result: **native NPU text and image embedding inference succeeded on the owner's
Pixel 10 Pro XL**. This experiment uses a separate package,
`com.vanfieldguide.npuprobe`. The field-guide APK, original model, manual, and photo
indexes remain on their existing CPU/GPU path.

## Hardware and pinned artifacts

- Device reports `ro.soc.model=Tensor G5`, Android 17, security patch 2026-10-05.
- LiteRT-LM Android 0.18.0.
- Google Tensor NPU runtime Maven package 2.3.0, using its
  `libLiteRtDispatch_GoogleTensor.so` and vendor native-library declarations.
- Google-published Tensor G5 model: 559,418,036 bytes, SHA-256
  `96cdbae7b3dd6fa0f829fef2d0dd5bafc0179890d9bafbfe972db852e818d758`.
- Repository revision and complete metadata are pinned in `npu-probe/model-lock.json`.
- CPU/GPU baseline is the exact existing 387,710,976-byte model in `model-lock.json`.
- Both model files were SHA-256 checked on the workstation and in the device probe.
- Probe APK requests no Android permissions and has no Internet permission. It
  uses its own private model copies and a generated image, not the owner's photos.

## What ran

The probe explicitly selects `Backend.NPU(nativeLibraryDir)` for text and, when
enabled, vision. The baseline selects CPU text and GPU vision like the app. Both
use the 1024-token configuration and the same text prefixes. Each mode creates a
fresh engine, measures initialization, then makes five embedding calls. Text uses
five short development queries; image mode repeats one generated 640x480 JPEG.
All output vectors had 768 finite dimensions with nonzero values.

Local logs show loading Google's dispatch library, DispatchDelegate handling the
compiled encoder subgraphs, and Edge TPU `CreateExecution` events during the probe.
CPU helpers still handle some surrounding operations. This is evidence of actual
TPU execution, not simply an NPU setting label.

## Measurements

Warm figures below are the medians of calls 2–5, excluding initialization and the
first call. The CPU/GPU baseline and NPU ran sequentially, not concurrently.

| Work | Existing CPU/GPU baseline | NPU, final AOT-only package | Observed ratio |
|---|---:|---:|---:|
| Text embedding | 72.09 ms | 20.43 ms | 3.53x |
| Image embedding | 1028.01 ms | 308.64 ms | 3.33x |

CPU text calls: 242.20, 75.09, 73.13, 71.05, 69.67 ms.
NPU text calls: 22.00, 20.76, 20.66, 20.21, 20.01 ms.
CPU/GPU image calls: 1624.17, 1105.80, 957.10, 969.41, 1086.61 ms.
NPU image calls: 278.07, 315.70, 305.70, 311.58, 300.62 ms.

Initialization: baseline text 696.83 ms; baseline text+vision 5690.55 ms;
final NPU text 425.92 ms; final NPU text+vision 709.82 ms. Cache state, thermals,
and order were not controlled, so these are observations, not stable startup
comparisons or claimed production speedups.

Corresponding CPU/NPU text-vector cosine similarity was 0.99967–0.99980 across
the five queries. Image-vector similarity was 0.99825 on the single repeated
generated image. This shows close numerical agreement on the probe inputs;
it does not establish ranking equivalence on the owner's collection.

## Initialization finding

The first package included both the vendor compiler plugin and dispatch library.
Initialization attempted on-device compilation and logged an Edge TPU allowlist
rejection, but the already-compiled model still ran successfully through dispatch.
The final package excludes `libLiteRtCompilerPlugin_google_tensor.so` because this
model is already compiled. Repeating text and image inference succeeded without
that compilation attempt. No allowlist, device security setting, root access, or
firmware change was used. Driver cleanup warnings remain in the initial logs;
long-session memory behavior has not been measured.

## Reproduce and integration boundary

Run `scripts/Test-Npu.ps1 -Device 'IP:PORT'` on a paired Tensor G5 device. The optional
Gradle module is included only with `-PnpuProbe`; normal app builds are unaffected.
The probe's build and lint passed. Reports and development logs are ignored local
artifacts in `.tools/npu-investigation`.

Before enabling NPU in the field-guide app: add an explicit backend/model choice,
maintain separate model/configuration index fingerprints, rebuild or independently
validate indexes, compare rankings on real photos/manual queries, exercise fallback,
and repeat the full disconnected flow. These microbenchmarks do not mean the app's
563 ms end-to-end photo search will become 3.5 times faster. The 559 MB NPU artifact
also adds storage if the existing CPU/GPU model is retained for fallback.

## Primary sources

- [Google's model repository](https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/tree/e301f74d5551b0c2641bd5cb4652a76239d5c5f8)
- [LiteRT-LM NPU setup](https://developers.google.com/edge/litert/next/litert_lm_npu)
- [LiteRT NPU libraries and AOT execution](https://developers.google.com/edge/litert/next/npu)
- [Pinned Kotlin backend API](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.0/kotlin/java/com/google/ai/edge/litertlm/Config.kt)
