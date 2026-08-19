# =============================================================================
# ENCODING: this file MUST stay UTF-8 *with BOM*.
# Windows PowerShell 5.1 reads BOM-less .ps1 as the ANSI codepage (CP949 here).
# A Korean word ending in a CP949 lead byte then swallows the following newline,
# so the next line of code silently becomes part of the comment above it.
# That is not a syntax error - it just makes statements disappear.
# =============================================================================

<#
.SYNOPSIS
    F04~F07 전 구간을 실제 서버·실제 LLM으로 한 번 통과시킨다.

.DESCRIPTION
    질문 발급 → 답변 판정 → 재설명 → 후속 확인 → 직원 처리까지 두 갈래를 태운다.

      R01  오해 → 재설명 → 후속 확인 정답  → COMPLETE / AUTO_RESOLVED
      R02  오해 → 재설명 → 후속 확인도 오해 → MANUAL_REVIEW → 직원 처리

    답변 문구는 eval/demo_seed.json 의 라벨된 답변을 그대로 쓴다. 손으로 입력하면
    라벨과 다른 문장을 넣게 되고, 그러면 판정이 맞았는지 틀렸는지 말할 수 없다.

    Gate 가 열려야 질문이 발급되므로 기본 시나리오는 CONS_A_002 다(유일하게 통과하는 상담문).

    LLM 을 실제로 호출한다. 전 구간 1회에 약 $0.07.

.EXAMPLE
    ./tools/run-understanding-eval.ps1
#>
[CmdletBinding()]
param(
    [string] $BaseUrl = 'http://localhost:8080',
    [string] $Scenario = 'CONS_A_002',
    [string] $CustomerId = 'CUST_A',
    [string] $SeedPath,
    [string] $OutFile
)

$ErrorActionPreference = 'Stop'

if (-not $SeedPath) {
    $SeedPath = Join-Path $PSScriptRoot '..\src\test\resources\eval\demo_seed.json'
}

# ---------------------------------------------------------------- HTTP 헬퍼
# PS 5.1 의 Invoke-RestMethod 는 본문 문자열을 UTF-8 로 보내지 않는다.
function Invoke-Json {
    param([string] $Method, [string] $Url, $Body)

    $params = @{
        Method          = $Method
        Uri             = $Url
        ContentType     = 'application/json; charset=utf-8'
        UseBasicParsing = $true
    }
    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 10 -Compress
        $params['Body'] = [System.Text.Encoding]::UTF8.GetBytes($json)
    }

    $response = Invoke-WebRequest @params
    $text = [System.Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
    if ([string]::IsNullOrWhiteSpace($text)) { return $null }
    return $text | ConvertFrom-Json
}

function Write-Step {
    param([string] $Title)
    Write-Host ''
    Write-Host ('-' * 78) -ForegroundColor DarkGray
    Write-Host $Title -ForegroundColor Cyan
    Write-Host ('-' * 78) -ForegroundColor DarkGray
}

function Write-Check {
    param([string] $Label, $Expected, $Actual)
    $ok = ($Expected -eq $Actual)
    $color = 'Red'
    $mark = 'X'
    if ($ok) { $color = 'Green'; $mark = 'O' }
    Write-Host ("  [{0}] {1,-22} expected={2} actual={3}" -f $mark, $Label, $Expected, $Actual) -ForegroundColor $color
    return $ok
}

# ---------------------------------------------------------------- 시드 로드
$seed = Get-Content -Raw -Encoding UTF8 -Path $SeedPath | ConvertFrom-Json
$consultation = $seed.consultations | Where-Object { $_.id -eq $Scenario }
if ($null -eq $consultation) { throw "시드에 없는 시나리오: $Scenario" }

function Get-Answer {
    param([string] $Id)
    $item = $seed.understandingAnswers | Where-Object { $_.id -eq $Id }
    if ($null -eq $item) { throw "시드에 없는 답변: $Id" }
    return $item
}

$checks = New-Object System.Collections.Generic.List[bool]

try {
    $health = Invoke-Json -Method GET -Url "$BaseUrl/actuator/health"
    Write-Host "server: $BaseUrl (status=$($health.status))" -ForegroundColor DarkGray
}
catch {
    throw "서버에 붙지 못했다: $BaseUrl — 앱을 먼저 띄울 것. ($($_.Exception.Message))"
}

# ---------------------------------------------------------------- 1) 세션 + Coverage
Write-Step "1) 세션 생성 → revision → Coverage ($Scenario)"

$session = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions" -Body @{
    productId  = $seed.productId
    customerId = $CustomerId
}
$sessionId = $session.sessionId
Write-Host "  sessionId = $sessionId" -ForegroundColor DarkGray

Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/revisions" -Body @{
    text = $consultation.text
} | Out-Null

$coverage = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/coverage" -Body @{}
$checks.Add((Write-Check 'gateStatus' 'READY_FOR_UNDERSTANDING' $coverage.gateStatus))

if ($coverage.gateStatus -ne 'READY_FOR_UNDERSTANDING') {
    throw "Gate 가 열리지 않아 이해확인으로 갈 수 없다. blocking=$($coverage.blockingRiskIds -join ',')"
}

# ---------------------------------------------------------------- 2) F04 질문 발급
Write-Step '2) F04 질문 발급'

$questions = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/questions" -Body @{}
$checks.Add((Write-Check 'totalRiskCount' 3 $questions.totalRiskCount))
foreach ($q in $questions.questions) {
    # 계약(openapi Question)의 필드명은 generationSource 가 아니라 source 다
    Write-Host ("  {0} [{1}] {2}" -f $q.riskId, $q.source, $q.question) -ForegroundColor Gray
}

# ---------------------------------------------------------------- 공통 흐름
function Submit-Answer {
    param([string] $Path, [string] $RiskId, [string] $Answer)
    return Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/$Path" -Body @{
        riskId       = $RiskId
        answer       = $Answer
        answerSource = 'CUSTOMER_DIRECT_DEMO'
    }
}

function Show-ReExplanation {
    param($Re)
    Write-Host ''
    Write-Host '  --- 재설명 본문 ---' -ForegroundColor Yellow
    Write-Host ("  {0}" -f $Re.explanation) -ForegroundColor White
    Write-Host ''
    Write-Host ("  source     : {0}" -f $Re.source) -ForegroundColor Gray
    Write-Host ("  guardrail  : retried={0} violations=[{1}]" -f `
        $Re.guardrail.retried, ($Re.guardrail.violations -join ',')) -ForegroundColor Gray
    Write-Host ("  sourcePage : {0}" -f $Re.sourcePage) -ForegroundColor Gray
    Write-Host ("  recheckQ   : [{0}] {1}" -f $Re.recheckQuestionSource, $Re.recheckQuestion) -ForegroundColor Gray
}

# ---------------------------------------------------------------- 3) R01 — 재설명 후 정답
Write-Step '3) R01  오해 → F06 재설명 → F07 후속 확인 정답'

$a1 = Get-Answer 'ANS_R01_001'
Write-Host ("  답변(attempt 1, gold={0}): {1}" -f $a1.goldLabel, $a1.answer) -ForegroundColor Gray
$r01First = Submit-Answer 'understanding' 'R01' $a1.answer
$checks.Add((Write-Check 'R01 aiStatus' $a1.goldLabel $r01First.aiStatus))
$checks.Add((Write-Check 'R01 nextAction' 'REEXPLAIN' $r01First.nextAction))
Write-Host ("  reason: {0}" -f $r01First.reason) -ForegroundColor DarkGray

$r01Re = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/reexplain" -Body @{ riskId = 'R01' }
Show-ReExplanation $r01Re
$checks.Add((Write-Check 'R01 re nextAction' 'RECHECK' $r01Re.nextAction))

# 멱등 확인 — 두 번째 호출은 LLM 을 부르지 않고 같은 문장을 돌려줘야 한다
$r01ReAgain = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/reexplain" -Body @{ riskId = 'R01' }
$checks.Add((Write-Check 'R01 재호출 멱등' $r01Re.explanation $r01ReAgain.explanation))
$checks.Add((Write-Check 'R01 후속질문 멱등' $r01Re.recheckQuestion $r01ReAgain.recheckQuestion))

$a2 = Get-Answer 'ANS_R01_002'
Write-Host ''
Write-Host ("  답변(attempt 2, gold={0}): {1}" -f $a2.goldLabel, $a2.answer) -ForegroundColor Gray
$r01Second = Submit-Answer 'recheck' 'R01' $a2.answer
$checks.Add((Write-Check 'R01 recheck aiStatus' $a2.goldLabel $r01Second.aiStatus))
$checks.Add((Write-Check 'R01 workflowStatus' 'COMPLETE' $r01Second.workflowStatus))
$checks.Add((Write-Check 'R01 disposition' 'AUTO_RESOLVED' $r01Second.finalDisposition))
$checks.Add((Write-Check 'R01 remainingAttempts' 0 $r01Second.remainingAttempts))

# ---------------------------------------------------------------- 4) R02 — 두 번 다 오해 → 직원 처리
Write-Step '4) R02  오해 → 재설명 → 후속도 오해 → 직원 처리'

$b1 = Get-Answer 'ANS_R02_001'
Write-Host ("  답변(attempt 1, gold={0}): {1}" -f $b1.goldLabel, $b1.answer) -ForegroundColor Gray
$r02First = Submit-Answer 'understanding' 'R02' $b1.answer
$checks.Add((Write-Check 'R02 aiStatus' $b1.goldLabel $r02First.aiStatus))
$checks.Add((Write-Check 'R02 nextAction' 'REEXPLAIN' $r02First.nextAction))

$r02Re = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/reexplain" -Body @{ riskId = 'R02' }
Show-ReExplanation $r02Re

Write-Host ''
Write-Host ("  답변(attempt 2, 같은 오해를 반복): {0}" -f $b1.answer) -ForegroundColor Gray
$r02Second = Submit-Answer 'recheck' 'R02' $b1.answer
$checks.Add((Write-Check 'R02 workflowStatus' 'MANUAL_REVIEW_REQUIRED' $r02Second.workflowStatus))
$checks.Add((Write-Check 'R02 disposition(null)' $null $r02Second.finalDisposition))
$checks.Add((Write-Check 'R02 nextAction' 'STAFF_RESOLUTION_REQUIRED' $r02Second.nextAction))

$resolution = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/risks/R02/staff-resolution" -Body @{
    disposition = 'RESOLVED_BY_STAFF'
    reason      = '구두로 최대 손실 범위를 다시 설명하고 고객이 이해함'
    actor       = 'staff-demo'
}
$checks.Add((Write-Check 'R02 처리후 workflow' 'COMPLETE' $resolution.workflowStatus))
$checks.Add((Write-Check 'R02 처리후 disposition' 'RESOLVED_BY_STAFF' $resolution.finalDisposition))
# 규칙 1 — 직원이 해결해도 AI 원판정은 그대로다
$checks.Add((Write-Check 'R02 aiStatus 보존' $r02Second.aiStatus $resolution.aiStatus))

# ---------------------------------------------------------------- 5) 새로고침 복구
Write-Step '5) GET /sessions/{id} — 새로고침 복구'

$snapshot = Invoke-Json -Method GET -Url "$BaseUrl/api/sessions/$sessionId"
Write-Host ("  sessionStatus = {0}" -f $snapshot.sessionStatus) -ForegroundColor Gray
Write-Host ("  resumePoint   = {0}" -f $snapshot.resumePoint) -ForegroundColor Gray
Write-Host ("  nextAction    = {0}" -f $snapshot.nextAction) -ForegroundColor Gray

$checks.Add((Write-Check 'coverage 복구' $coverage.gateStatus $snapshot.coverage.gateStatus))
$checks.Add((Write-Check 'coverage risks' 9 @($snapshot.coverage.risks).Count))
$checks.Add((Write-Check 'understanding 건수' 3 @($snapshot.understanding).Count))

# R01·R02 는 끝났고 R03 만 미답변이므로, 복구는 R03 의 attempt 1 질문을 가리켜야 한다
foreach ($state in $snapshot.understanding) {
    $pending = '-'
    if ($null -ne $state.pendingQuestion) { $pending = "attempt $($state.pendingQuestion.attempt)" }
    Write-Host ("  {0} workflow={1,-24} disposition={2,-18} pending={3} attempts={4}" -f `
        $state.riskId, $state.workflowStatus, $state.finalDisposition, $pending, @($state.attempts).Count) -ForegroundColor Gray
}

$r01State = $snapshot.understanding | Where-Object { $_.riskId -eq 'R01' }
$r02State = $snapshot.understanding | Where-Object { $_.riskId -eq 'R02' }
$checks.Add((Write-Check 'R01 attempts 보존' 2 @($r01State.attempts).Count))
$checks.Add((Write-Check 'R01 pending 없음' $null $r01State.pendingQuestion))
$checks.Add((Write-Check 'R02 직원처리 보존' 'RESOLVED_BY_STAFF' $r02State.staffResolution.disposition))
# 규칙 1 — 직원이 해결해도 AI 원판정은 리포트에 남는다
$checks.Add((Write-Check 'R02 AI판정 보존' 'MISUNDERSTOOD' $r02State.attempts[-1].aiStatus))
$checks.Add((Write-Check 'nextAction 복구' 'NEXT_RISK' $snapshot.nextAction))

# ---------------------------------------------------------------- 결과
$passed = @($checks | Where-Object { $_ }).Count
Write-Host ''
Write-Host ('=' * 78) -ForegroundColor DarkGray
$summaryColor = 'Yellow'
if ($passed -eq $checks.Count) { $summaryColor = 'Green' }
Write-Host ("검증 {0}/{1} 통과   sessionId={2}" -f $passed, $checks.Count, $sessionId) -ForegroundColor $summaryColor

if ($OutFile) {
    [pscustomobject]@{
        sessionId       = $sessionId
        scenario        = $Scenario
        coverage        = $coverage
        questions       = $questions
        r01             = @{ first = $r01First; reExplanation = $r01Re; second = $r01Second }
        r02             = @{ first = $r02First; reExplanation = $r02Re; second = $r02Second; resolution = $resolution }
        snapshot        = $snapshot
    } | ConvertTo-Json -Depth 12 | Out-File -FilePath $OutFile -Encoding utf8
    Write-Host "결과 저장: $OutFile" -ForegroundColor DarkGray
}
