# 성능 측정용 DB 복사본(ottnavi_perf)을 만들고 측정용 사용자를 넣는다(Task 100).
# 개발 DB(ottnavi)는 템플릿으로 읽기만 한다. 실행은 저장소 루트에서: .\infra\k6\prepare-perf-db.ps1 -Users 50
# 전제: Docker Desktop이 켜져 있고 `docker compose -f infra/docker-compose.yml up -d`로 postgres가 떠 있다.
#       개발 백엔드(bootRun)는 꺼져 있어야 한다(템플릿으로 쓰는 DB에 접속이 있으면 복사가 실패한다).
param([int]$Users = 50)

# psql의 NOTICE 메시지가 표준 오류로 나오므로 PowerShell 5.1이 오류로 오해하지 않게 하고, 종료 코드로 직접 판단한다
$ErrorActionPreference = 'Continue'
$compose = @('compose', '-f', 'infra/docker-compose.yml')

# 1. 기존 복사본을 지우고 개발 DB를 템플릿으로 새로 복사한다
docker @compose exec -T postgres psql -U ottnavi -d postgres -v ON_ERROR_STOP=1 -c "DROP DATABASE IF EXISTS ottnavi_perf"
if ($LASTEXITCODE -ne 0) { throw '기존 복사본 삭제에 실패했다(측정 백엔드가 접속 중이면 먼저 내린다: docker compose -f infra/docker-compose.yml --profile perf stop backend-perf)' }
docker @compose exec -T postgres psql -U ottnavi -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE ottnavi_perf TEMPLATE ottnavi"
if ($LASTEXITCODE -ne 0) { throw '복사본 생성에 실패했다(개발 백엔드가 개발 DB에 붙어 있는지 확인)' }

# 2. 복사본에서 사용자 데이터를 비우고 측정용 사용자를 넣는다
# (PowerShell 파이프는 한글을 깨뜨리고 입력이 전달되지 않는 경우가 있어 cmd의 파일 리다이렉트로 SQL을 그대로 넘긴다)
cmd /c "docker compose -f infra/docker-compose.yml exec -T postgres psql -U ottnavi -d ottnavi_perf -v ON_ERROR_STOP=1 -v users=$Users < infra\k6\seed-perf-users.sql"
if ($LASTEXITCODE -ne 0) { throw '측정용 사용자 입력에 실패했다' }
