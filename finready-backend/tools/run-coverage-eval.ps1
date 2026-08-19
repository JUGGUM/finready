# =============================================================================
# ENCODING: this file MUST stay UTF-8 *with BOM*.
# Windows PowerShell 5.1 reads BOM-less .ps1 as the ANSI codepage (CP949 here).
# A Korean word ending in a CP949 lead byte then swallows the following newline,
# so the next line of code silently becomes part of the comment above it.
# That is not a syntax error - it just makes statements disappear.
# =============================================================================
<#
.SYNOPSIS
    eval/demo_seed.json 의 상담 시나리오를 실제 서버에 태워 Coverage 판정을 라벨과 대조한다.

.DESCRIPTION
    세션 생성 → revision 저장 → coverage 분석을 시나리오마다 한 번씩 돌리고,
    Risk 별 coverageStatus 를 coverageGroundTruth 와 비교해 표로 낸다.

    Swagger 로 손으로 돌리면 긴 한글 본문을 세 번 붙여넣어야 하고, 그 과정에서
    본문이 한 글자라도 달라지면 provenance 결과가 달라져 측정이 오염된다.
    같은 입력을 그대로 쓰기 위한 스크립트다.

    LLM 을 실제로 호출하므로 비용이 든다(시나리오당 약 $0.03).

.EXAMPLE
    ./tools/run-coverage-eval.ps1 -Scenarios CONS_A_002,CONS_A_004,CONS_A_005

.EXAMPLE
    ./tools/run-coverage-eval.ps1 -Scenarios CONS_A_001 -Repeat 2
#>
[CmdletBinding()]
param(
    [string[]] $Scenarios = @('CONS_A_002', 'CONS_A_004', 'CONS_A_005'),
    [string]   $BaseUrl = 'http://localhost:8080',
    [string]   $CustomerId = 'CUST_A',
    [int]      $Repeat = 1,
    [string]   $SeedPath,
    [string]   $OutFile
)

$ErrorActionPreference = 'Stop'

if (-not $SeedPath) {
    $SeedPath = Join-Path $PSScriptRoot '..\src\test\resources\eval\demo_seed.json'
}

# ---------------------------------------------------------------- HTTP 헬퍼
# PS 5.1 의 Invoke-RestMethod 는 본문 문자열을 UTF-8 로 보내지 않는다.
# 한글 상담문이 깨지면 evidence 인용이 원문과 안 맞아 provenance 가 전부 실패한다.
function Invoke-Json {
    param(
        [string] $Method,
        [string] $Url,
        $Body
    )

    $params = @{
        Method      = $Method
        Uri         = $Url
        ContentType = 'application/json; charset=utf-8'
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

function Get-Prop {
    param($Object, [string] $Name)
    if ($null -eq $Object) { return $null }
    $prop = $Object.PSObject.Properties[$Name]
    if ($null -eq $prop) { return $null }
    return $prop.Value
}

# ---------------------------------------------------------------- 시드 로드
if (-not (Test-Path $SeedPath)) {
    throw "시드 파일을 찾을 수 없다: $SeedPath"
}
$seed = Get-Content -Raw -Encoding UTF8 -Path $SeedPath | ConvertFrom-Json

# 서버가 떠 있는지 먼저 본다 — 안 떠 있으면 첫 POST 에서 애매한 예외가 난다
try {
    $health = Invoke-Json -Method GET -Url "$BaseUrl/actuator/health"
    Write-Host "server: $BaseUrl (status=$($health.status))" -ForegroundColor DarkGray
}
catch {
    throw "서버에 붙지 못했다: $BaseUrl — 앱을 먼저 띄울 것. ($($_.Exception.Message))"
}

$transcript = New-Object System.Collections.Generic.List[object]
$totalMatch = 0
$totalRisk = 0

foreach ($scenarioId in $Scenarios) {

    $consultation = $seed.consultations | Where-Object { $_.id -eq $scenarioId }
    if ($null -eq $consultation) {
        Write-Warning "시드에 없는 시나리오: $scenarioId — 건너뛴다"
        continue
    }

    for ($run = 1; $run -le $Repeat; $run++) {

        $runLabel = $scenarioId
        if ($Repeat -gt 1) { $runLabel = "$scenarioId (run $run/$Repeat)" }

        Write-Host ''
        Write-Host ('=' * 78) -ForegroundColor DarkGray
        Write-Host "$runLabel — $($consultation.title)" -ForegroundColor Cyan
        Write-Host ('=' * 78) -ForegroundColor DarkGray

        $session = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions" -Body @{
            productId  = $seed.productId
            customerId = $CustomerId
        }
        $sessionId = $session.sessionId

        $revision = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/revisions" -Body @{
            text = $consultation.text
        }

        $started = Get-Date
        $coverage = Invoke-Json -Method POST -Url "$BaseUrl/api/sessions/$sessionId/coverage" -Body @{}
        $wallMs = [int]((Get-Date) - $started).TotalMilliseconds

        # ------------------------------------------------------- Risk 대조
        $groundTruth = $consultation.coverageGroundTruth
        $rows = New-Object System.Collections.Generic.List[object]
        $matched = 0

        foreach ($risk in $coverage.risks) {
            $expected = Get-Prop -Object $groundTruth -Name $risk.riskId
            $actual = $risk.coverageStatus
            $ok = ($expected -eq $actual)
            if ($ok) { $matched++ }

            $policy = 'warn'
            if ($risk.coveragePolicy -eq 'GATE_REQUIRED') { $policy = 'GATE' }
            $mark = 'X'
            if ($ok) { $mark = 'O' }

            $rows.Add([pscustomobject]@{
                riskId     = $risk.riskId
                policy     = $policy
                expected   = $expected
                classifier = $risk.classifierStatus
                actual     = $actual
                semantic   = $risk.semanticRelation
                downgraded = $risk.downgraded
                match      = $mark
            })
        }

        $rows | Format-Table -AutoSize | Out-String -Width 200 | Write-Host

        # ------------------------------------------------------- Gate 대조
        $gateExpected = $consultation.expectedGateResult
        $gateOk = ($gateExpected -eq $coverage.gateStatus)
        $gateColor = 'Red'
        if ($gateOk) { $gateColor = 'Green' }
        $riskColor = 'Yellow'
        if ($matched -eq $rows.Count) { $riskColor = 'Green' }

        Write-Host ("Risk  : {0}/{1} 일치" -f $matched, $rows.Count) -ForegroundColor $riskColor
        Write-Host ("Gate  : expected={0} actual={1}" -f $gateExpected, $coverage.gateStatus) -ForegroundColor $gateColor
        Write-Host ("blocking={0}" -f ($coverage.blockingRiskIds -join ',')) -ForegroundColor DarkGray
        Write-Host ("warning ={0}" -f ($coverage.warningRiskIds -join ',')) -ForegroundColor DarkGray
        Write-Host ("latency : classifier={0}ms verifier={1}ms wall={2}ms prompt={3}" -f $coverage.analysis.classifierLatencyMs, $coverage.analysis.verifierLatencyMs, $wallMs, $coverage.analysis.promptVersion) -ForegroundColor DarkGray

        $totalMatch += $matched
        $totalRisk += $rows.Count

        $transcript.Add([pscustomobject]@{
            scenario     = $scenarioId
            run          = $run
            sessionId    = $sessionId
            revisionId   = $revision.revisionId
            gateExpected = $gateExpected
            gateActual   = $coverage.gateStatus
            gateMatch    = $gateOk
            riskMatched  = $matched
            riskTotal    = $rows.Count
            rows         = $rows
            analysis     = $coverage.analysis
        })
    }
}

Write-Host ''
Write-Host ('-' * 78) -ForegroundColor DarkGray
$gateMatchCount = @($transcript | Where-Object { $_.gateMatch }).Count
Write-Host ("합계  Risk {0}/{1}   Gate {2}/{3}" -f $totalMatch, $totalRisk, $gateMatchCount, $transcript.Count) -ForegroundColor Cyan

if ($OutFile) {
    $transcript | ConvertTo-Json -Depth 10 | Out-File -FilePath $OutFile -Encoding utf8
    Write-Host "결과 저장: $OutFile" -ForegroundColor DarkGray
}
