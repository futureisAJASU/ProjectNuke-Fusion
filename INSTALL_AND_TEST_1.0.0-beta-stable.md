# Fusion 1.0.0 Beta Stable 적용 및 확인 방법

## R3 (versionCode 10005) 적용 방법

R3는 Git 저장소의 `main` 브랜치에 커밋되어 있다. 기존 R2 프로젝트가 있다면:

```powershell
git status
git log --oneline -1 f71de43   # R2 헤드 확인
git fetch origin main
git rebase origin/main         # R2 → R3 커밋 적용
```

Gradle Sync 후 프로젝트 루트 Terminal에서 전체 gate를 실행한다.

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat compileDebugUnitTestKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
git diff --check
```

디버그 APK는 `app\build\outputs\apk\debug\app-debug.apk`에 생성된다.

## 가장 안전한 방법: 새 폴더에서 열기

1. 기존 Fusion 프로젝트 폴더를 그대로 백업하거나 Git commit을 만든다.
2. `Fusion-1.0.0-beta-stable-r2-source.zip`을 새 폴더에 압축 해제한다.
3. Android Studio에서 압축을 푼 폴더를 **File → Open**으로 연다.
4. Gradle Sync가 끝나면 프로젝트 루트 Terminal에서 다음을 실행한다.

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lintDebug
```

5. 디버그 APK는 보통 아래에 생성된다.

```text
app\build\outputs\apk\debug\app-debug.apk
```

6. USB 디버깅이 켜진 기기에는 다음처럼 업데이트 설치할 수 있다.

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

기존 앱과 applicationId·서명 키가 같아야 `-r` 업데이트가 가능하다. 서명이 다르면 기존 앱을 지워야 하지만, 삭제하면 앱 내부 채팅과 설정도 함께 삭제될 수 있으므로 먼저 필요한 내용을 내보내야 한다.

## 기존 Git 프로젝트에 적용하는 방법

현재 프로젝트가 이전에 전달한 초기 Beta Stable 커밋 `b0b2fb9`라면:

```powershell
git status
git apply --check .\Fusion-1.0.0-beta-stable-r2-from-b0b2fb9.patch
git apply .\Fusion-1.0.0-beta-stable-r2-from-b0b2fb9.patch
```

현재 프로젝트가 원래 `fusion(62)`의 HEAD `fcbbc2c`라면:

```powershell
git status
git apply --check .\Fusion-1.0.0-beta-stable-r2-from-fusion62.patch
git apply .\Fusion-1.0.0-beta-stable-r2-from-fusion62.patch
```

`git status`에 기존 수정사항이 있다면 먼저 commit 또는 stash해야 한다. `git apply --check`가 실패하면 억지로 적용하지 말고 새 소스 ZIP을 별도 폴더에서 여는 편이 안전하다.

## Git bundle을 사용하는 방법

```powershell
git clone .\fusion-1.0.0-beta-stable-r2.bundle Fusion-Beta-Stable-R2
cd Fusion-Beta-Stable-R2
git checkout v1.0.0-beta-stable
```

## 필수 수동 점검

- 새 대화 첫 메시지 전송과 빠른 연속 Send
- 답변 생성 중 Stop
- 전송·생성 중 다른 대화로 이동했다가 복귀
- 큰 이미지 여러 개 첨부 중 대화 전환 또는 Back
- 누락된 첨부파일 표시와 재시도 차단
- 로컬 일반 생성, Retry, 스타일 재생성
- 외부 API 일반 생성과 첨부 차단
- 모델 `.litertlm` 가져오기, 복사 취소, 현재 모델 삭제
- `.task` 파일이 실행 가능으로 잘못 표시되지 않는지 확인
- 현재 대화와 다른 대화 삭제
- 앱을 백그라운드로 보냈다가 복귀

테스트나 빌드가 실패하면 전체 로그에서 첫 번째 실제 컴파일 오류와 그 주변을 함께 확인하는 것이 좋다.
