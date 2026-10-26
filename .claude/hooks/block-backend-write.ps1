# frontend-dev 에이전트의 backend/ 하위 수정과 frontend/src/api/generated/ 직접 수정 시도를 차단하는 PreToolUse hook (exit 2 = 차단)
# 한계: Bash는 명령 문자열 패턴으로만 검사하므로 인터프리터(python -c 등)를 통한 쓰기는 막지 못한다.
#       악의적 우회를 막는 보안 경계가 아니라 에이전트의 실수를 줄이는 보조 장치다.
$ErrorActionPreference = 'Stop'
# 한글 메시지가 깨지지 않도록 입출력을 UTF-8로 맞춘다
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

try {
    $raw = [Console]::In.ReadToEnd()
    $payload = $raw | ConvertFrom-Json
} catch {
    # 입력을 해석하지 못하면 차단하지 않는다(에이전트 작업이 통째로 막히는 것을 방지)
    exit 0
}

$projectRoot = $env:CLAUDE_PROJECT_DIR
if (-not $projectRoot) { $projectRoot = $payload.cwd }
if (-not $projectRoot) { exit 0 }
$backendDir = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'backend')).TrimEnd('\') + '\'
# orval 생성물 폴더: 계약(openapi.yaml)에서 `npm run api:generate`로만 바뀌어야 하므로 손으로 고치지 못하게 한다
$generatedDir = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'frontend\src\api\generated')).TrimEnd('\') + '\'

function Block($reason) {
    [Console]::Error.WriteLine("차단됨: $reason frontend-dev는 backend/ 하위와 frontend/src/api/generated/ 를 직접 수정할 수 없다. backend/는 필요한 변경 내용을 사용자에게 보고하고, 생성물은 docs/api/openapi.yaml 수정 후 npm run api:generate 로 갱신한다.")
    exit 2
}

$toolName = $payload.tool_name
$toolInput = $payload.tool_input

# 파일 수정 도구: 대상 경로가 backend/ 또는 생성물 폴더 아래인지 확인
if ($toolName -in @('Edit', 'Write', 'NotebookEdit')) {
    $target = $toolInput.file_path
    if (-not $target) { $target = $toolInput.notebook_path }
    if ($target) {
        # 절대경로는 그대로, 상대경로는 프로젝트 루트 기준으로 해석한다
        if ([System.IO.Path]::IsPathRooted($target)) { $full = [System.IO.Path]::GetFullPath($target) }
        else { $full = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $target)) }
        $fullWithSep = $full + '\'
        if ($fullWithSep.StartsWith($backendDir, [System.StringComparison]::OrdinalIgnoreCase)) {
            Block "$full 은(는) backend/ 하위다."
        }
        if ($fullWithSep.StartsWith($generatedDir, [System.StringComparison]::OrdinalIgnoreCase)) {
            Block "$full 은(는) orval 생성물 폴더 하위다."
        }
    }
    exit 0
}

# Bash: backend 또는 생성물 경로를 언급하면서 쓰기·삭제·설치성 명령이 들어 있으면 차단 (읽기 명령은 허용)
# 주의: `npm run api:generate`는 경로 문자열(api/generated)을 명령에 쓰지 않으므로 이 검사에 걸리지 않고 정상 동작한다
if ($toolName -eq 'Bash') {
    $cmd = [string]$toolInput.command
    if ($cmd -match '(?i)(backend|api[/\\]generated)') {
        $writePattern = '(?i)((?<![\d&])>>?(?![&=])|\btee\b|\bsed\s+-i|\brm\b|\bmv\b|\bcp\b|\bmkdir\b|\btouch\b|Set-Content|Add-Content|Out-File|Remove-Item|Move-Item|Copy-Item|New-Item|Rename-Item|\bgit\s+(checkout|restore|reset|clean|stash|apply|add|rm|mv)\b)'
        if ($cmd -match $writePattern) {
            Block "backend/ 또는 생성물 폴더를 변경할 수 있는 명령이다: $cmd"
        }
    }
}

exit 0
