# Initial device-test build

Prepared October 13, 2026 for the October 16 noon PDT recording.

This is the historical 0.1 integration report. The current APK has been replaced
by 0.2; see `BINDER-TEST-REPORT.md` for the new build's hash and validation status.

## Verified on the development workstation

- Gradle `:app:assembleDebug` succeeded with the real LiteRT-LM Android dependency.
- Gradle `:app:lintDebug` completed with **0 errors and 8 warnings**.
- The checked-in `scripts/Build.ps1` successfully rebuilt and audited the APK.
- The packaged APK permission list contains `RECORD_AUDIO` and AndroidX's
  app-specific signature permission for non-exported receivers. **No INTERNET
  permission is present.** This is an APK audit, not proof about the speech service.
- The downloaded model is 387,710,976 bytes and matches `model-lock.json` SHA-256.
- The Gradle wrapper JAR SHA-256 matches Gradle's published wrapper checksum:
  `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
- Dependency resolution for the debug build is captured in `app/gradle.lockfile`.
- Git ignore checks exclude a root-level PDF, arbitrary `local-materials/`
  contents, model bytes, and project-local development tools. No remote repository
  was configured and no user equipment documents were uploaded.

APK: `app/build/outputs/apk/debug/app-debug.apk` (50,325,404 bytes).

APK SHA-256:
`94ef47f84772876b625fb9bd58b35aec65d955a690c588ae73f121bf06cce037`.

Lint warnings concern available dependency/Gradle updates, intentionally absent
ChromeOS x86 support, a resource-lifetime warning on an InputStream already
closed by Kotlin `use`, and the missing custom launcher icon. These do not prevent
the test APK build. Gradle also reports that 8.13 will be unsupported by a future
Kotlin release; the current pinned Kotlin 2.4.0 build succeeds.

## Pending, with no success claim

The Pixel 10 Pro XL subsequently connected over paired wireless ADB and reported
Android API 37. The debug APK installed successfully, and Android reported a
successful cold launch of `com.vanfieldguide/.MainActivity` (620 ms launch time;
this is not an inference measurement). The 387,710,976-byte model was copied to
the phone's Downloads folder and its on-phone SHA-256 matched the pinned hash.
The first capture was black while the device was locked/dozing. A subsequent
capture of the foreground app confirmed visible rendering and a successful native
text embedding result: **768 dimensions, 921 ms model initialization, 142 ms
inference**. This is one observed result, not a benchmark or p95 measurement;
the exact query and cold/warm cache state were not recorded. The result demonstrates
that the model was imported and executed on this Pixel. The device was connected
over wireless ADB, so it does not establish disconnected operation.

The owner subsequently reported that the guided speech-to-transcript test worked.
A later app capture showed the transcript "Find my inverter operating instructions",
matching the prompted phrase. Raw audio, speech timing, and resource-preparation
details were not captured. This is a connected-device test, not offline evidence.

The same capture confirmed a successful native image embedding: **768 dimensions,
999 ms model initialization, 1006 ms inference**. The supplied wiring-plan PNG had
been copied to Downloads for this guided test. This is one observed result, with
no ranking-quality or wiring-interpretation claim. Permission-denial and
interruption behavior and disconnected execution remain pending device validation.
The owner then reported that both guided tests passed offline and that the phone
was back online. Record this as **owner-reported offline success**, not independently
observed execution: exact offline timings, screenshots, network configuration,
and restart evidence were not collected. The prompt included a restart and a new
spoken phrase followed by text and image embedding tests; the owner's brief report
did not itemize each substep. The end-to-end binder offline acceptance remains pending.

The initial app's equipment-confirmation label is stale relative to
the owner's recorded confirmation; its readiness banner is a fixed pending label,
not a computed test failure.

The first screen can invoke native text/image embedding tests and the explicit
on-device recognizer. This is integration-test code, not an implemented binder.
Since this initial build, the owner supplied the `Sprinter/` collection and
confirmed the installed inverter identity. A workstation text-layer inspection
and private source inventory exist in `local-materials/source-inventory.json`;
the matching manual is a starter-pack candidate. No app retrieval evaluation has
been performed. Android PDF import/extraction,
lexical/semantic retrieval, source identity enforcement, viewers, OCR decisions,
benchmark measurements, and end-to-end demo acceptance remain future work.

Only the single text and image inference timings above are available. No ranking accuracy,
offline voice accuracy, peak memory, or thermal result is available.
The full brief's acceptance criteria are not yet met.
