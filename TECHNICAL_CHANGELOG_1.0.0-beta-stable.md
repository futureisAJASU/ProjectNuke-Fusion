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
- `versionCode`: `10000`
- App information and in-app update history now show Fusion 1.0 Beta Stable.
- Added user-facing release notes and updated README history.

## Validation performed in this environment

- `git diff --check`: passed.
- UTF-8 decoding and conflict-marker scan: passed.
- Generated log, crash dump, temporary and backup file scan: passed.
- Standalone Kotlin harness for commit ordering, transaction-failure ownership and draft reconciliation: passed.
- Standalone Kotlin harness for canonical pending-attachment discard and registration behavior: passed.
- Standalone compilation of production `MessageSubmissionCoordinator` with its JVM tests: passed.
- Standalone compilation of composer policy test sources against matching production signatures: passed.
- `testDebugUnitTest` and `assembleDebug` were attempted through the wrapper but could not start because Gradle 9.4.1 is not cached and this environment cannot resolve `services.gradle.org`.

## Remaining beta limitations

- Voice input and voice mode remain placeholders.
- External AI API attachment upload remains intentionally blocked before request creation.
- Full Android build, emulator/device smoke tests and release signing must still be run in an environment with Gradle 9.4.1, the Android SDK and network/dependency caches available.
