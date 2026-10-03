---
name: git-pr
description: "GitHub Pull Request를 생성하고 관리합니다"
disable-model-invocation: true
allowed-tools:
  [
    "Bash(gh pr:*)",
    "Bash(git push:*)",
    "Bash(git status:*)",
    "Bash(git log:*)",
    "Bash(git diff:*)",
    "Bash(git branch:*)",
    "Bash(git fetch:*)",
  ]
---

# Claude 명령어: Pull Request

현재 브랜치를 기준으로 GitHub Pull Request를 생성하고, 상태를 조회·전환하는 도구입니다.

## 사용법

```
/git-pr                       # 현재 브랜치로 PR 생성 (제목·설명 자동 생성)
/git-pr "PR 제목"              # 제목 지정하여 PR 생성
/git-pr --draft               # Draft PR 생성
/git-pr --ready               # Draft PR을 Ready로 전환
/git-pr --status              # 현재 브랜치의 PR 상태 확인
/git-pr --close               # PR 닫기
```

## 프로세스

### PR 생성 전 점검

1. `git status`로 uncommitted 변경사항 확인
2. `git log`로 커밋 히스토리 확인, `git fetch`로 원격 상태 최신화
3. 현재 브랜치가 아직 원격에 push되지 않았다면 `git push -u origin [브랜치명]`으로 push
4. `git diff`로 base 브랜치 대비 변경된 파일 목록·통계 확인

### PR 내용 생성

1. **제목**: 인자로 제목이 주어졌으면 그대로 사용, 없으면 커밋 히스토리를 요약해서 제안
2. **설명**: 아래 템플릿에 맞춰 커밋 메시지 요약·주요 변경사항을 채워 작성
3. **라벨/리뷰어/마일스톤**: 사용자가 명시적으로 요청한 경우에만 `gh pr create --label`, `--reviewer`, `--milestone` 플래그로 지정한다. 저장소 규칙(CODEOWNERS 등)에 따른 자동 배정은 GitHub 자체 기능이며, 이 스킬이 대신 판단하지 않는다

### PR 생성

```
gh pr create --title "[제목]" --body "[생성한 설명]" [--draft]
```

## PR 설명 템플릿

```markdown
## 변경사항 요약

[커밋 히스토리 기반 요약]

## 변경 이유

[브랜치명·커밋 메시지 기반 목적]

## 체크리스트

- [ ] 셀프 리뷰 완료
- [ ] 관련 테스트 통과 확인
- [ ] 문서 업데이트 필요 여부 확인

## 관련 이슈

Closes #[issue-number] (있는 경우)
```

## 상태 관리

```
/git-pr --status          # gh pr view 로 현재 브랜치의 PR 상태 확인
/git-pr --draft           # gh pr ready --undo 로 Draft 전환
/git-pr --ready           # gh pr ready 로 Ready 전환
/git-pr --close           # gh pr close 로 PR 닫기
```

## 문제 해결

1. **GitHub CLI 인증 오류** → `gh auth login` 실행 안내
2. **브랜치 push 오류** → 원격에 브랜치가 없으면 `git push -u origin [브랜치명]`으로 먼저 push
3. **PR 생성 실패 (중복)** → `gh pr list`로 같은 브랜치의 기존 PR 존재 여부 먼저 확인

## 참고사항

- CI/CD 트리거, 코드 품질 검사(ESLint/TypeScript 등), 의존성 보안 스캔은 GitHub Actions 등 별도 설정에 의해 자동으로 동작하는 것이지, 이 스킬이 직접 수행하는 기능이 아니다
- Jira/Linear 같은 외부 프로젝트 관리 도구 연동은 지원하지 않는다 (해당 도구의 MCP 연결이 없으면 불가능)
- PR 병합(`gh pr merge`)은 이 스킬 범위에 포함하지 않는다. 병합이 필요하면 GitHub 웹에서 직접 하거나 별도로 요청한다
