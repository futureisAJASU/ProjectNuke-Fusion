# Fusion 1.0.0 Beta Stable

Fusion의 핵심 채팅 기능을 실제 일상 사용에 가까운 조건에서 시험할 수 있도록 전송, 생성, 첨부와 삭제 lifecycle을 집중적으로 안정화한 릴리스입니다.

## 주요 변경사항

- 새 대화 생성과 사용자 메시지 저장을 Room transaction 기반 cancellation-safe commit으로 묶어 orphan conversation과 중복 전송 경계를 줄였습니다.
- exact submission owner와 request identity를 사용해 빠른 연속 Send, stale completion과 대화 전환 race를 차단했습니다.
- generation request가 registry에 설치되어 실제로 취소 가능해진 뒤에만 Stop UI를 표시합니다.
- 첨부 v3 codec과 trust boundary를 적용하고 로컬 경로가 모델·외부 API history에 노출되지 않도록 정리했습니다.
- 첨부 가져오기·폐기·삭제를 canonical managed-file 정책으로 통합했습니다.
- 첨부 원본 URI 권한을 불필요하게 누적하지 않고, 복사 실패·취소 시 남을 수 있는 부분 파일을 정리합니다.
- 이미지 thumbnail 검사와 downsample decode를 IO dispatcher로 이동하고 RGB_565를 사용해 UI 멈춤과 메모리 사용량을 줄였습니다.
- 앱 복귀와 파일 열기 실패 시 첨부 상태를 다시 확인해 사라진 파일을 즉시 unavailable 카드로 전환합니다.
- 대화 검색 색인을 백그라운드에서 캐시해 검색어 입력 중 반복적인 파일 시스템 검사를 줄였습니다.
- 온디바이스 AI 런타임 버전을 고정해 같은 소스가 빌드 시점에 따라 달라지는 문제를 줄였습니다.
- 현재/다른 대화 삭제 모두 cancel-and-await 후 처리하며 dialog busy 상태와 오류 안내를 통일했습니다.
- 앱 내부 버전 정보와 업데이트 기록을 `1.0.0-beta-stable`로 맞추고 R2용 versionCode를 `10001`로 올렸습니다.
- 생성된 답변을 DB에 저장한 뒤 대화 정렬 timestamp 갱신만 실패했을 때 답변을 되삭제하던 문제를 수정했습니다. 일반 생성, Retry와 스타일 재생성 모두 저장된 답변을 유지합니다.
- 응답 버전 metadata는 생성 결과 저장 경로에서 확정 저장하고, 앱 종료·취소가 겹쳐도 선택된 답변 버전이 사라질 가능성을 줄였습니다.
- 모델 가져오기는 안전한 파일명, UUID 저장명, `.part` 임시 파일과 원자적 이동을 사용하며 metadata 저장 실패 시 복사 파일을 되돌립니다.
- 현재 포함된 실행 엔진과 실제 호환성을 맞춰 `.litertlm`만 직접 실행 가능으로 분류하고 `.task`는 보관·호환성 확인 대상으로 표시합니다.
- 선택한 모델 파일 삭제, 외부 모델 연결 해제와 URI 권한 정리를 identity-aware하게 처리합니다.
- Android 시스템 자동 백업과 기기 이전에서 앱 데이터를 제외해 로컬 우선 데이터 정책과 실제 동작을 맞췄습니다.

## 알려진 제한

- 음성 입력과 보이스 모드는 아직 실제 기능이 연결되지 않은 안내 상태입니다.
- 로컬 모델의 속도, 메모리 사용량과 지원 accelerator는 기기·모델 파일·LiteRT backend에 따라 달라집니다.
- 외부 AI API 모드의 첨부 전송은 아직 지원하지 않으며 전송·재시도 전에 차단됩니다.
### Persistence and ownership hardening

- Crash-consistent composer draft hydration and critical settlement.
- Durable draft attachment cleanup references.
- Cancellation-safe conversation deletion and settings restore truthfulness.
- Bounded UTF-8 backup streaming, prompt budgeting, HTTP cancellation, and rollback-safe model replacement.
# 1.0.0-beta-stable ownership follow-up

- Draft reconciliation now uses the single serialized draft owner, including new-conversation draft key `0`.
- Post-commit attachment release waits for durable reconciliation or durable recovery debt.
- Custom provider metadata uses UUID-backed providers, validated endpoints, Keystore-only secrets, and durable metadata commits.
- Model imports use a process-owned coordinator with bounded staged copies and validation before adoption.

## 1.0.0-beta-stable R3 (versionCode 10005)

- Provider secrets are written and deleted durably: mutations return Boolean results and only committed Keystore/preferences writes are treated as success. Failed metadata commits roll back secret creation instead of leaking orphan secrets.
- Provider screens use try/finally so save/test/delete never leave the editor busy when an exception occurs; `NONE` auth mode skips secret requirements consistently, and private-network detection covers the full `172.16.0.0/12` range.
- Model import no longer scans for orphans on every import. Orphan recovery is token-based and only removes files abandoned for a grace period; adopted files are never deleted after adoption, cancellation after adoption returns success, and declared vs. streamed sizes are accounted separately (`TOO_LARGE` vs `STORAGE_FULL`).
- LiteRT-LM packages are validated structurally (magic, header, bounded section table, overflow-safe offsets, no overlaps, required tokenizer section) and expose drafter-section capability for MTP support instead of filename matching.
- Draft hydration publishes the rollback snapshot before completing deferred durability replies, `CancellationException` is rethrown instead of being swallowed by debt retry, and conversation cleanup executes each derived-data component independently so one failure does not abort the rest.
- Final prompt assembly returns a typed `Ready`/`TooLarge` result; `TooLarge` stops before any engine/provider invocation, and prompt budgets compute their limit from the effective model context.
- MTP runtime state is exact: `OFF`, `REQUESTED`, `ACTIVE`, `UNSUPPORTED`, `FALLBACK_DISABLED`, `FAILED`. The speculative-decoding flag is applied before engine initialization, a fallback engine is never cached under an MTP key, and `AUTO` uses a GPU+MTP → GPU → CPU+MTP → CPU fallback ladder.
- MTP support is derived from validated package capabilities (`hasDrafter`), and chat, benchmark, and A/B test screens share one `resolveEffectiveMtpSetting` policy.
- MTP behavior is covered by production tests using an injectable engine factory and flag adapter.

## 1.0.0-beta-stable R4 (versionCode 10006)

- 대화 내용이 모델 컨텍스트 한도를 초과하면 전송·재시도·스타일 재생성·외부 API 호출 모두에서 요청 전에 차단하고 안내 메시지를 표시합니다. 이제 과도한 입력이 조용히 엔진으로 전달되지 않습니다.
- 프롬프트 예산은 출력 토큰 예약분을 먼저 공제한 뒤 필수 입력(시스템·현재 질문)을 검사하며, 최소 입력조차 맞지 않으면 생성 처럼 잘린 내용 대신 바로 오류로 처리합니다.
- 대화 삭제 후 부산물 정리가 구성 요소별 성공 여부를 확인하며 실패한 부분만 다시 시도합니다. 상세한 정리가 일부 실패했는데도 완료로 처리되는 경우를 줄였습니다.
- 삭제 시점에 실제 제거되는 작성 중 첨부 경로를 정확히 가로채 정리 기록에 포함합니다.
- 손상된 UTF-8 문자열을 포함한 모델 파일을 이전에는 조용히 잘라 통과시켰던 문제를 수정하고, 이제 즉시 무효 처리하여 안내합니다.
- versionCode를 `10006`으로 올렸습니다.

## 1.0.0-beta-stable R5 (versionCode 10007)

- 가속기나 MTP 설정을 바꾸면 모델을 다시 불러오지만, 온도·토큰·시드 같은 회차별 옵션은 모델을 다시 불러오지 않습니다. KV 캐시 용량만 엔진 신원으로 사용하고 출력 길이 제한은 앱 단에서 안전하게 끊습니다.
- MTP 켜기/끄기에 관계없이 프롬프트 바이트가 동일하도록 보장하여 측정 비교가 공정합니다.
- MTP 실패 기억이 앱 재시작 후에도 유지되며 정확한 백엔드 단위로 기록되어, GPU 실패가 CPU 요청을 오염시키지 않습니다.
- 실패 후 AUTO는 GPU+MTP → GPU → CPU로만 넘어가고 자동 CPU+MTP는 사용하지 않습니다.
- MTP 지원 여부는 엔진 생성 전의 공식 기능 탐사로 확인합니다. 탐사 결과는 기능 지원 증거일 뿐 실제 실행 활성화 증거가 아니므로, MTP 플래그가 적용되어 엔진이 초기화되면 'MTP 요청으로 초기화됨'으로 표시합니다. 기능 탐사 결과가 부정이면 해당 후보를 건너뛰고 자동으로 끕니다.
- 채팅 성능 요약과 벤치마크 결과에 네이티브 첫 토큰·프리필·디코딩 속도 정보를 추가했습니다.
- versionCode를 `10007`로 올렸습니다.
