# Fusion Future Query Planner

## Why rule-based planner first

The current planner is rule-based because it is deterministic, fast, cheap, and safe to run before every web search. It does not call an LLM, does not need a network request, and cannot leak provider secrets through prompts.

This MVP handles follow-up search requests, keyword extraction, freshness hints, alternate queries, and preferred provider categories. It is intentionally conservative so provider routing remains predictable.

## Future local model planner

A future planner can use a small local model, or a selected external AI provider only when the user has explicitly configured it. The planner should run before web search and produce strict JSON. Fusion should validate the JSON against a schema, reject unknown fields, and fall back to the rule-based planner if parsing fails.

The future planner should receive only the minimum needed context: current user request, previous user topic, lightweight conversation summary, enabled search mode, and available provider categories. It must never receive API keys, client secrets, authorization headers, or raw provider configuration secrets.

## Strict JSON Schema Example

```json
{
  "intent": "CURRENT_INFO",
  "resolvedTopic": "삼성 HBM4E 수율",
  "primaryQuery": "삼성 HBM4E 수율 최신 뉴스",
  "alternateQueries": [
    "Samsung HBM4E yield latest report",
    "삼성 HBM4E 수율 분석",
    "삼성 HBM4E 경쟁사 SK하이닉스 Micron 비교"
  ],
  "preferredProviders": ["FREE_DEFAULT", "NAVER", "KAKAO_DAUM", "BRAVE"],
  "freshness": true,
  "language": "ko-KR",
  "region": "KR",
  "reason": "The user requested additional sources for a Korean semiconductor news topic."
}
```

## Safety Rules

- Output must be strict JSON only.
- Output must not include secrets, API keys, client secrets, or authorization headers.
- Provider names must use known enum values only.
- Alternate queries should be capped to avoid slow search fan-out.
- Fusion should keep the rule-based planner as fallback.
