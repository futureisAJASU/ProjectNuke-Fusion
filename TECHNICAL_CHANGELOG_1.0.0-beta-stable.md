# Fusion 1.0.0 Beta Stable — Technical Changelog

## Submission and generation lifecycle

- Added an exact `MessageSubmissionOwner` hand-off from composer submission to the installed generation request.
- Added `MessageSubmissionCoordinator` to own the irreversible commit boundary.
- New-conversation insertion and first user-message insertion now run inside one Room transaction and a `NonCancellable` settlement.
- Conversation publication happens only after the user message commits; exact raw-input and attachment identities are then reconciled once.
- Timestamp updates are best effort and cannot turn a committed Send into a retryable duplicate draft.
- Registry installation keeps submission progress active until the request is actually installed, then clears only the matching owner.
- Request-install failures clear only matching request state and provide a safe user-facing error.
- Send coroutines no longer directly overwrite another conversation's visible generation presentation state.

## Attachment storage and UI

- Pending attachment discard now canonicalizes the managed root and target, requires a direct managed file, and rejects sibling-prefix, directory and symlink escape targets.
- Pending registration is removed only after deletion succeeds or the managed file is already absent; failed deletion preserves registration.
- Pre-message-commit failures preserve raw input, tray entries, copied files and pending registrations.
- Attachment import is serialized, capacity is reserved against the five-item limit, and mixed success/failure/limit results are reported together.
- Imported documents are copied under collision-resistant managed names without retaining unnecessary source URI grants; cancelled or failed copies remove partial files.
- Thumbnail file checks and downsample decoding run on `Dispatchers.IO` with `RGB_565` to reduce UI stalls and bitmap memory.
- Attachment state is revalidated when the app resumes. Missing attachments become disabled unavailable cards, and open failures trigger immediate revalidation.
- The existing memory-managed thumbnail LRU cache is reused with file metadata in the cache key, avoiding repeated decodes while still invalidating replaced files.
- In-chat search builds its trusted visible-text index on `Dispatchers.IO` and reuses it while the query changes, removing repeated filesystem resolution from typing.
- User-facing missing-image errors no longer expose local filesystem paths.

## Conversation deletion and composer UX

- Current, drawer and legacy deletion paths preserve cancel-before-delete and identity-aware cleanup.
- Delete dialogs expose a global busy state and disable dismiss, Cancel and Delete while any deletion owner is active.
- Composer text, attachment mutation, quick prompts, mode selection, picker and voice actions share one generation/submission/import lock policy.
- Quick-prompt expansion now closes from an effect instead of mutating Compose state during composition.

## Build reproducibility

- Pinned the LiteRT-LM Android runtime to `0.14.0` instead of a dynamic `latest.release` selector.
- Restored the executable bit on the Gradle wrapper script.

## Release metadata

- `versionName`: `1.0.0-beta-stable`
- `versionCode`: `10001` (R2 static-hardening build)
- App information and in-app update history now show Fusion 1.0 Beta Stable.
- Added user-facing release notes and updated README history.


## Static hardening pass after the initial beta candidate

- Added `ResponsePersistenceCoordinator` so assistant-message insertion is irreversible while conversation timestamp updates remain best effort.
- Applied the same persistence contract to normal local/external generation, Retry and style regeneration.
- Response-version state now uses durable `SharedPreferences.commit()` only on generation-result settlement; ordinary version navigation keeps asynchronous writes to avoid main-thread fsync.
- Reloads response-version state when message identities change, covering cancellation immediately after durable persistence.
- Attachment imports are adopted as one batch only after the originating conversation is still current; cancellation and conversation switches discard copied files through the managed storage API.
- Model imports now use sanitized UUID names, `.part` staging files, cancellation checks and atomic move fallback.
- Added `ManagedModelPathPolicy` as the final runtime boundary for direct managed model files, symlink rejection and supported executable extensions.
- Corrected the runtime capability model: this build contains LiteRT-LM only, so `.litertlm` is executable while MediaPipe `.task` files remain non-runnable catalog items.
- Model metadata writes use `AtomicFile`; metadata failure rolls back a newly copied model file.
- Deleting the currently selected model clears its active path, and external unlink/copy flows release persisted document permissions only after metadata settlement succeeds.
- External model accessibility checks run on `Dispatchers.IO` and no longer report a null stream as accessible.
- Disabled Android automatic app-data backup and device-transfer inclusion to match the local-data policy.

## Validation performed in this environment

- `git diff --check`: passed.
- XML parsing for all Android resource and manifest files: passed.
- Kotlin PSI parsing for every modified production and test file: passed with no syntax errors.
- Production `MessageSubmissionCoordinator` and `ResponsePersistenceCoordinator` compilation/execution harness: passed.
- Production `ManagedModelPathPolicy` compilation/execution harness: passed.
- UTF-8, conflict-marker, generated-log, crash-dump, temporary and backup-file scans: passed.
- Git bundle verification and source ZIP integrity checks: passed after packaging.
- Android Gradle build and device tests were intentionally left for the user environment as requested.

## Remaining beta limitations

- Voice input and voice mode remain placeholders.
- External AI API attachment upload remains intentionally blocked before request creation.
- Full Android build, emulator/device smoke tests and release signing must still be run in an environment with Gradle 9.4.1, the Android SDK and network/dependency caches available.
## 2026-08-01

- Added final-prompt assembly budgeting for local and external generation paths.
- Added durable draft attachment reference discovery and rollback-safe model adoption.
- Hardened deletion, settings backup, candidate persistence, and HTTP cancellation ownership.
# Ownership and import follow-up

- Reconciliation commands are serialized with draft hydration and debounced writes.
- Cleanup debt retries use bounded exponential backoff and preserve failed debt records.
- Imported model files are staged as unique `.part` files, fsynced, validated, then atomically adopted.
## R3 hardening (2026-08-02, versionCode 10005)

### Durable provider secret ownership

- `SecretStore.putSecret`/`deleteSecret` now return `Boolean`; `AndroidKeystoreSecretStore` commits durably and returns the settled result.
- `saveProvider` writes a fresh UUID secret, verifies durable settlement, commits metadata, then deletes the replaced secret; metadata-commit failure deletes only the new secret.
- `deleteProvider` dereferences metadata first, then deletes the secret; secret-deletion failure is surfaced without rolling back the metadata commit.
- `AiProviderSettingsScreen` wraps every save/test/delete/export action in `try`/`finally` so the busy state is always released and boolean failures are checked before success feedback.
- `AiProviderAuthMode.NONE` is exempt from secret requirements in row status, editor status, and validation; custom-header names are format-validated.
- Private-network detection covers the full `172.16.0.0/12` range.

### Process-owned model import

- Removed per-import orphan cleanup; orphan recovery now requires an active-token check and a 5-minute grace period.
- Fixed a dead branch where a null adoption result was never deleted; staged `.part` cleanup happens only when no adoption occurred.
- Size accounting queries `OpenableColumns.SIZE` on IO and separates `TOO_LARGE` (declared overflow) from `STORAGE_FULL` (streaming space shortfall), reserving bytes before copy.
- Post-adoption progress and cancellation handling keep adopted files: cancellation after adoption returns success, and only `.part` staging files are removed.

### Bounded LiteRT-LM package validation

- `LiteRtLmPackageValidator` parses magic, header, version, and the section table with overflow-safe offsets, no-overlap enforcement, and a required tokenizer section; `LiteRtLmCapabilities(hasDrafter, ...)` detects the MTP drafter section.
- MTP runtime support is derived from `hasDrafter` instead of filename matching.

### Draft and deletion truthfulness

- Draft hydration publishes the rollback snapshot before completing deferred durability replies.
- `CommittedDraftReconciliationDebtStore.retry` rethrows `CancellationException` instead of swallowing it.
- Conversation cleanup runs each derived-data component independently; partial failure raises `IllegalStateException("cleanup incomplete")` instead of aborting the remaining components.

### Typed final prompt assembly

- `FinalPromptAssembler.assemble` returns `PromptAssemblyResult.Ready`/`TooLarge`; `readyOrThrow` converts successful assembly to messages and stops before any engine/provider call on `TooLarge`.
- `FinalPromptBudgeter.fit` returns `FittedMessages(messages, isTooLarge)` and computes the limit via `computeLimit`; the `FUSION_TOO_LARGE` sentinel string is removed.

### Exact MTP runtime state and fallback

- `MtpRuntimeStatus` is now `OFF`/`REQUESTED`/`ACTIVE`/`UNSUPPORTED`/`FALLBACK_DISABLED`/`FAILED`.
- `ExperimentalFlags.enableSpeculativeDecoding` is applied before `Engine(config).initialize()`; cache-hit state is resolved exactly; a fallback engine is cached under an effective MTP=false key so it is never reused for an MTP request.
- `AUTO` uses a `GPU+MTP → GPU → CPU+MTP → CPU` ladder via `buildEngineCandidateLadder`; GPU vision failures retry with a CPU vision backend.
- Chat, benchmark, and A/B screens share `resolveEffectiveMtpSetting`/`defaultSpeculativeDecodingEnabled` from `MtpPolicy`.
- `LiteRtLlmEngine` accepts an injectable engine factory and flag adapter; `selectFirstWorkingEngine` drives the ladder and is covered by production unit tests.

### Validation performed

- `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `testDebugUnitTest` (349 tests), `assembleDebug`, and `lintDebug`: passed.
- `git diff --check`: passed.
- `versionCode` bumped `10004` → `10005` with `versionName` unchanged at `1.0.0-beta-stable`.

## R4 hardening (2026-08-03, versionCode 10006)

### Truthful final prompt budgeting

- `FinalPromptBudgeter.fit` deducts the output-token reserve from the context limit once and validates mandatory (system + current user) input against the reserve-deducted `available`, not the raw limit; when mandatory input exceeds `available` it returns `TooLarge` with an empty message list instead of emitting fabricated "Budget exceeded" content.
- `FinalPromptAssembler.Ready.contextsRemoved` counts whole removed turns (`(removed messages + 1) / 2`) instead of raw message counts.
- `TooLarge` is now a user-visible gate, not a throw: `runExternalAiRequest` returns `ExternalAiChatResult.Error`, and the local retry, style-regeneration and primary send paths abort with a toast (and an assistant error reply in the primary send path) before any engine or provider invocation.
- Added budgeter tests for `TooLarge`-with-empty-messages, reserve-exhaustion, and assembler turn counting.

### Cleanup truthfulness

- `ConversationCleanupDebtStore.retry` checks every component's Boolean result (`deleteResponseVersionState`, `deleteConversationSummary`, memory candidates, ratings, pending paths); previously ignored `false` results dropped the debt as successful. Only the failed message-ids/paths are re-recorded, so one transient failure no longer forces completed components to be redone.
- `ConversationDeletion` captures pending attachment paths inside `settleTarget` from the exact draft being removed (before `clear`), covering both the `DELETED` and `ALREADY_ABSENT` branches instead of racing the pre-deletion draft state.

### Strict UTF-8 package decoding (bug fix)

- `decodeStrictUtf8` relied on the two-argument `CharsetDecoder.decode(in, out, endOfInput)` throwing on malformed input, but that API never throws — malformed input is signaled only via the returned `CoderResult`, which was ignored. Malformed UTF-8 (e.g., a lone `0x80` continuation byte) was silently truncated and accepted, making `FailureReason.INVALID_UTF8` dead code.
- The decoder now checks both `decode` and `flush` results plus leftover input and rejects any malformed or unconsumed bytes with `ParseException("$what is not valid UTF-8")`, so the validator reports `INVALID_UTF8` and exposes no capabilities.
- Added a negative test that patches a valid package string in place with a lone continuation byte and asserts `INVALID_UTF8` with `validationVersion == 0`; the golden Gemma 4 E2B package test still passes.

### Validation performed

- `compileDebugKotlin`, `compileDebugUnitTestKotlin`, `testDebugUnitTest` (all suites, incl. the 2.4 GB golden package test), `assembleDebug`, and `lintDebug`: passed.
- `git diff --check`: passed.
- `versionCode` bumped `10005` → `10006` with `versionName` unchanged at `1.0.0-beta-stable`.

## R5 LiteRT-LM MTP lifecycle hardening (2026-08-04, versionCode 10006 -> 10007)

### Typed engine profile vs per-turn conversation options

- RequestedEngineProfile (modelPath, accelerator, mtpRequested, kvCacheCapacityTokens, enableVisionBackend) is now the sole engine-creation identity; ConversationOptions (maxOutputToken, temperature, topK, topP, seed) is per-turn and can never rebuild or reload an engine.
- KV cache capacity (EngineConfig.maxNumTokens) is the engine identity; the output limit is an app-level streaming guard (~4 chars/token estimate, cancelProcess + Success.truncated), because v0.14.0's ConversationConfig exposes no max output token.

### Prompt byte identity

- System instruction and user prompt are built from messages only; runtime settings never appear in prompt bytes, so MTP on/off produce byte-identical prompts (enforced by LiteRtPromptIdentityTest).

### Mandatory flag settlement

- Never initialize an Engine while ExperimentalFlags.enableSpeculativeDecoding state is unknown: a failed enable skips the MTP candidate, a failed disable skips the plain candidate, and the flag is re-settled before every candidate and on unload/failure.

### Fallback ladder and persistent failure memory

- AUTO ladder is now GPU+MTP -> GPU -> CPU (no automatic CPU+MTP, max 3 candidates); CPU+MTP remains an explicit experimental path.
- MtpFailureMemory persists across restarts via SharedPreferences and is keyed by canonical model path, exact candidate backend, MTP state, validator version, KV capacity and vision flag, so a GPU+MTP failure never poisons a CPU or plain request.

### Typed runtime identity

- ModelFingerprint (canonical path, size, mtime, validator version, MTP capability) plus EngineRuntimeKey replace the string cache key; file/capability changes invalidate the loaded engine. LoadedRuntimeState holds one consistent snapshot for reuse decisions and status reporting.

### Capability probe and truthful runtime status

- Renamed the pre-Engine `runtimeMtpProbe` to `mtpCapabilityProbe` to reflect that `Capabilities(modelPath).hasSpeculativeDecodingSupport()` is a capability check performed before Engine creation, not runtime-activity evidence.
- A positive capability result no longer produces `RUNTIME_CONFIRMED_ACTIVE`; a successful MTP Engine initialization now reports `INITIALIZED_WITH_MTP_REQUEST`. `RUNTIME_CONFIRMED_ACTIVE` remains a reserved enum value, deliberately unreachable from `resolveMtpRuntimeStatus` until LiteRT-LM exposes positive execution evidence (e.g. drafted/accepted-token counters).
- A negative capability result still skips the MTP candidate (kept from R5).
- Added an exhaustive matrix test proving `RUNTIME_CONFIRMED_ACTIVE` is never produced by the resolver for any combination of inputs.
- Corrected release notes that previously claimed "runtime activity confirmation" to describe the truthful capability-gated status.

### Native benchmark stats

- Conversation.getBenchmarkInfo() is distilled into GenerationBenchmarkStats on every successful generation and surfaced in chat metrics (native tTFT, decode/prefill tok/s) and benchmark results (native tTFT, prefill, decode, init time).

### Validation performed

- compileDebugKotlin, compileDebugUnitTestKotlin, 	estDebugUnitTest (444 tests, incl. the 2.4 GB golden package test), ssembleDebug, and lintDebug: passed.
- git diff --check: passed.
- versionCode bump 10006 -> 10007 pending device benchmark gate; versionName unchanged at 1.0.0-beta-stable.
