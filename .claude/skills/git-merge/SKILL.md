---
name: git-merge
description: "브랜치를 안전하게 병합하고 충돌을 해결합니다"
disable-model-invocation: true
allowed-tools:
  [
    "Bash(git merge:*)",
    "Bash(git status:*)",
    "Bash(git diff:*)",
    "Bash(git log:*)",
    "Bash(git branch:*)",
    "Bash(git fetch:*)",
    "Bash(git checkout:*)",
    "Bash(git stash:*)",
  ]
---

# Claude 명령어: Merge

브랜치를 안전하게 병합하고, 충돌이 생기면 단계별로 해결을 돕는 도구입니다.

## 사용법

```
/git-merge [브랜치명]           # 지정된 브랜치를 현재 브랜치에 병합
/git-merge                    # 병합 전 상태 점검 후 진행 여부 확인
```

## 지원하는 병합 전략

- **Fast-forward** (기본): 선형 히스토리 유지, 별도 병합 커밋 없음
- **No-fast-forward** (`--no-ff`): 병합 커밋을 남겨서 브랜치 단위 작업을 히스토리에 남김
- **Squash** (`--squash`): 여러 커밋을 하나로 압축해서 병합

## 프로세스

### 병합 전

1. `git status`로 현재 브랜치의 uncommitted 변경사항 확인 → 있으면 stash 여부를 먼저 확인
2. `git fetch`로 대상 브랜치 최신 상태 확보, 브랜치 존재 여부 확인
3. `git merge --no-commit --no-ff [대상]` 등으로 충돌 가능성을 사전 점검 (실제로 커밋하지 않고 충돌만 미리 확인 후 필요시 `git merge --abort`로 되돌림)

### 병합 실행

1. 지정된 전략(기본/`--no-ff`/`--squash`)으로 `git merge` 실행
2. 기본/`--no-ff`인 경우: 병합과 동시에 커밋까지 완료되므로, 충돌 없이 끝나면 결과 요약(병합된 커밋 수, 변경 파일 수)
3. `--squash`인 경우: `git merge --squash`는 변경사항을 스테이징만 하고 커밋을 만들지 않는다. 스테이징된 내용을 사용자에게 보여주고, 커밋 메시지를 만들어 커밋하거나 `/git-commit`에 위임한다 (이 단계를 건너뛰면 실제로 아무것도 반영되지 않는다)

### 충돌 발생 시

1. `git status`로 충돌 파일 목록 확인
2. 각 파일의 충돌 마커(`<<<<<<<`, `=======`, `>>>>>>>`) 내용을 보여주고, 해결 방향 제안
   - 현재 브랜치 내용 유지: `git checkout --ours [파일]`
   - 병합할 브랜치 내용 채택: `git checkout --theirs [파일]`
   - 수동 편집: 직접 수정 후 안내
3. 모든 충돌이 해결되면 `git add`로 스테이지 후 커밋 (또는 `/git-commit` 위임)
4. 병합을 취소하고 싶으면 `git merge --abort`로 병합 전 상태로 복구

## 안전 기능

- 병합 전 uncommitted 변경사항이 있으면 자동으로 진행하지 않고 먼저 stash 또는 커밋을 권한다
- `main`, `develop`처럼 중요해 보이는 브랜치로의 병합은 실행 전 한 번 더 확인을 받는다
- 되돌리기는 `git merge --abort`로만 처리한다. 병합이 이미 완료된 뒤 되돌려야 하는 상황(하드 리셋이 필요한 경우)은 자동으로 실행하지 않고, 어떤 명령을 실행할지 사용자에게 먼저 제시하고 확인을 받는다 (이 스킬에는 `git reset` 권한이 없다)

## 사용 예시

```
/git-merge feature/user-auth              # 기본 병합
/git-merge --no-ff feature/user-auth      # 병합 커밋 남기기
/git-merge --squash feature/user-auth     # Squash 병합
```

## 참고사항

- 빌드 실행이나 테스트 통과 여부 확인은 이 스킬의 범위 밖이다 (별도 테스트/빌드 도구 권한이 없음). 병합 후 빌드 확인이 필요하면 별도로 요청한다
- GitHub PR 상태 연동이 필요하면 `/git-pr`을 별도로 사용한다
