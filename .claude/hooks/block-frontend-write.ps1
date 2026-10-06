# backend-dev 에이전트의 frontend/ 하위 수정 시도를 차단하는 PreToolUse hook (exit 2 = 차단)
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
$frontendDir = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'frontend')).TrimEnd('\') + '\'

function Block($reason) {
    [Console]::Error.WriteLine("차단됨: $reason backend-dev는 frontend/ 하위를 수정할 수 없다. 필요한 변경 내용을 사용자에게 보고한다.")
    exit 2
}

$toolName = $payload.tool_name
$toolInput = $payload.tool_input

# 파일 수정 도구: 대상 경로가 frontend/ 아래인지 확인
if ($toolName -in @('Edit', 'Write', 'NotebookEdit')) {
    $target = $toolInput.file_path
    if (-not $target) { $target = $toolInput.notebook_path }
    if ($target) {
        # 절대경로는 그대로, 상대경로는 프로젝트 루트 기준으로 해석한다
        if ([System.IO.Path]::IsPathRooted($target)) { $full = [System.IO.Path]::GetFullPath($target) }
        else { $full = [System.IO.Path]::GetFullPath((Join-Path $projectRoot $target)) }
        if (($full + '\').StartsWith($frontendDir, [System.StringComparison]::OrdinalIgnoreCase)) {
            Block "$full 은(는) frontend/ 하위다."
        }
    }
    exit 0
}

# Bash: frontend 경로를 언급하면서 쓰기·삭제·설치성 명령이 들어 있으면 차단 (읽기 명령은 허용)
if ($toolName -eq 'Bash') {
    $cmd = [string]$toolInput.command
    if ($cmd -match '(?i)frontend') {
        $writePattern = '(?i)((?<![\d&])>>?(?![&=])|\btee\b|\bsed\s+-i|\brm\b|\bmv\b|\bcp\b|\bmkdir\b|\btouch\b|Set-Content|Add-Content|Out-File|Remove-Item|Move-Item|Copy-Item|New-Item|Rename-Item|\bnpm\s+(install|i|ci|uninstall|update)\b|\bnpx\b|\bgit\s+(checkout|restore|reset|clean|stash|apply|add|rm|mv)\b)'
        if ($cmd -match $writePattern) {
            Block "frontend/ 를 변경할 수 있는 명령이다: $cmd"
        }
    }
}

exit 0
