# 영업도우미 노트북용 - 로컬 서버 (Windows 기본 PowerShell만 사용, 설치 필요 없음)
# 이 노트북(localhost)에서만 접속됩니다. 외부에서는 접속할 수 없습니다.
# 솔라피 문자 자동 발송: API Secret은 Windows 사용자 계정으로 암호화(DPAPI)해서 solapi.local.json에 저장합니다.
$ErrorActionPreference = 'Stop'
try { [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12 } catch { }
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootFull = [System.IO.Path]::GetFullPath($root)
$cfgPath = Join-Path $rootFull 'solapi.local.json'
$solBase = 'https://api.solapi.com'
$mime = @{
  '.html' = 'text/html; charset=utf-8'; '.js' = 'text/javascript; charset=utf-8'; '.css' = 'text/css; charset=utf-8'
  '.json' = 'application/json; charset=utf-8'; '.png' = 'image/png'; '.jpg' = 'image/jpeg'; '.ico' = 'image/x-icon'
  '.woff2' = 'font/woff2'; '.wasm' = 'application/wasm'; '.gz' = 'application/octet-stream'; '.txt' = 'text/plain; charset=utf-8'
}

function Get-Cfg {
  if (-not (Test-Path -LiteralPath $cfgPath)) { return $null }
  try {
    $c = Get-Content -LiteralPath $cfgPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $ss = ConvertTo-SecureString $c.secretEnc
    $b = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($ss)
    try { $secret = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($b) } finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b) }
    return @{ apiKey = [string]$c.apiKey; apiSecret = $secret; from = [string]$c.from }
  } catch { return $null }
}
function Save-Cfg($apiKey, $apiSecret, $from) {
  $enc = ConvertTo-SecureString $apiSecret -AsPlainText -Force | ConvertFrom-SecureString
  $o = @{ apiKey = $apiKey; secretEnc = $enc; from = $from } | ConvertTo-Json
  [System.IO.File]::WriteAllText($cfgPath, $o, (New-Object System.Text.UTF8Encoding($false)))
}
function Get-Auth($cfg) {
  $date = (Get-Date).ToString("yyyy-MM-dd'T'HH:mm:ssK")
  $chars = '1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'
  $rng = New-Object System.Security.Cryptography.RNGCryptoServiceProvider
  $bytes = New-Object byte[] 32; $rng.GetBytes($bytes)
  $salt = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
  $h = New-Object System.Security.Cryptography.HMACSHA256 (,[System.Text.Encoding]::UTF8.GetBytes($cfg.apiSecret))
  $sig = -join ($h.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($date + $salt)) | ForEach-Object { $_.ToString('x2') })
  return "HMAC-SHA256 apiKey=$($cfg.apiKey), date=$date, salt=$salt, signature=$sig"
}
function Invoke-Sol($cfg, $method, $path, $bodyObj) {
  $req = [System.Net.HttpWebRequest]::Create("$solBase/$path")
  $req.Method = $method; $req.Timeout = 30000
  $req.Headers.Add('Authorization', (Get-Auth $cfg))
  if ($bodyObj) {
    $req.ContentType = 'application/json; charset=utf-8'
    $data = [System.Text.Encoding]::UTF8.GetBytes(($bodyObj | ConvertTo-Json -Depth 6 -Compress))
    $req.ContentLength = $data.Length
    $s = $req.GetRequestStream(); $s.Write($data, 0, $data.Length); $s.Close()
  }
  try { $resp = $req.GetResponse() } catch [System.Net.WebException] {
    $resp = $_.Exception.Response
    if (-not $resp) { return @{ status = 0; body = $null; err = $_.Exception.Message } }
  }
  $rd = New-Object System.IO.StreamReader($resp.GetResponseStream(), [System.Text.Encoding]::UTF8)
  $txt = $rd.ReadToEnd(); $rd.Close()
  $code = [int]$resp.StatusCode; $resp.Close()
  $obj = $null; try { $obj = $txt | ConvertFrom-Json } catch { }
  return @{ status = $code; body = $obj; text = $txt }
}
function Sol-Err($r) {
  if ($r.status -eq 0) { return "솔라피 서버에 연결하지 못했습니다 (인터넷 확인): $($r.err)" }
  if ($r.body -and $r.body.errorMessage) { return "$($r.body.errorCode): $($r.body.errorMessage)" }
  return "HTTP $($r.status) $($r.text)"
}
function Handle-Api($ctx, $rel) {
  $q = $ctx.Request
  # 다른 웹사이트가 이 노트북 서버로 몰래 문자 발송을 요청하지 못하게 막음
  $origin = $q.Headers['Origin']
  if ($q.HttpMethod -ne 'GET') {
    if ($q.Headers['X-Yeongil'] -ne '1' -or ($origin -and $origin -notmatch '^http://(localhost|127\.0\.0\.1):\d+$')) { return @{ ok = $false; error = 'forbidden' } }
  }
  $body = $null
  if ($q.HasEntityBody) { $rd = New-Object System.IO.StreamReader($q.InputStream, [System.Text.Encoding]::UTF8); $raw = $rd.ReadToEnd(); $rd.Close(); if ($raw) { $body = $raw | ConvertFrom-Json } }
  switch ($rel) {
    'api/sms/status' {
      $c = Get-Cfg
      if (-not $c) { return @{ ok = $true; configured = $false; server = 'powershell' } }
      return @{ ok = $true; configured = $true; server = 'powershell'; from = $c.from; apiKey = ($c.apiKey.Substring(0, [Math]::Min(4, $c.apiKey.Length)) + '****') }
    }
    'api/sms/config' {
      if (-not $body.apiKey -or -not $body.apiSecret -or -not $body.from) { return @{ ok = $false; error = 'API Key, API Secret, 발신번호를 모두 넣으세요.' } }
      $c = @{ apiKey = ([string]$body.apiKey).Trim(); apiSecret = ([string]$body.apiSecret).Trim(); from = (([string]$body.from) -replace '\D', '') }
      $r = Invoke-Sol $c 'GET' 'cash/v1/balance' $null
      if ($r.status -ne 200) { return @{ ok = $false; error = (Sol-Err $r) } }
      Save-Cfg $c.apiKey $c.apiSecret $c.from
      return @{ ok = $true; balance = $r.body.balance; point = $r.body.point }
    }
    'api/sms/balance' {
      $c = Get-Cfg; if (-not $c) { return @{ ok = $false; error = 'not configured' } }
      $r = Invoke-Sol $c 'GET' 'cash/v1/balance' $null
      if ($r.status -ne 200) { return @{ ok = $false; error = (Sol-Err $r) } }
      return @{ ok = $true; balance = $r.body.balance; point = $r.body.point }
    }
    'api/sms/send' {
      $c = Get-Cfg; if (-not $c) { return @{ ok = $false; error = '문자 설정이 없습니다.' } }
      $to = ([string]$body.to) -replace '\D', ''
      if ($to.Length -lt 10) { return @{ ok = $false; error = '받는 번호가 올바르지 않습니다.' } }
      $its = @($body.items)
      if (-not $body.items) { $its = @(@{ text = $body.text; image = $body.image; subject = $body.subject }) }
      if ($its.Count -lt 1 -or $its.Count -gt 5) { return @{ ok = $false; error = '한 번에 1~5건만 보낼 수 있습니다.' } }
      $msgs = @()
      foreach ($it in $its) {
        $msg = @{ to = $to; from = $c.from; text = [string]$it.text }
        if ($it.image) {
          $u = Invoke-Sol $c 'POST' 'storage/v1/files' @{ file = [string]$it.image; type = 'MMS'; name = 'card.jpg' }
          if ($u.status -ne 200 -or -not $u.body.fileId) { return @{ ok = $false; error = '이미지 업로드 실패 - ' + (Sol-Err $u) } }
          $msg.imageId = $u.body.fileId; $msg.type = 'MMS'
          if ($it.subject) { $msg.subject = [string]$it.subject }
        }
        $msgs += $msg
      }
      $r = Invoke-Sol $c 'POST' 'messages/v4/send-many/fast' @{ messages = $msgs; allowDuplicates = $true }
      if ($r.status -ne 200) { return @{ ok = $false; error = (Sol-Err $r) } }
      $failed = @($r.body.failedMessageList | Where-Object { $_ })
      if ($failed.Count -gt 0) { return @{ ok = $false; error = "$($msgs.Count)건 중 $($failed.Count)건 접수 실패: $($failed[0].statusCode) $($failed[0].statusMessage)" } }
      return @{ ok = $true; count = $msgs.Count; groupId = $r.body.groupInfo.groupId }
    }
    'api/sms/reset' { if (Test-Path -LiteralPath $cfgPath) { Remove-Item -LiteralPath $cfgPath }; return @{ ok = $true } }
  }
  return @{ ok = $false; error = 'unknown api' }
}

$listener = $null; $port = 0
foreach ($p in 8765..8785) {
  try {
    $l = New-Object System.Net.HttpListener
    $l.Prefixes.Add("http://localhost:$p/")
    $l.Start(); $listener = $l; $port = $p; break
  } catch { }
}
if (-not $listener) { Write-Host '서버를 시작하지 못했습니다. (8765~8785 포트가 모두 사용 중)'; exit 1 }
$url = "http://localhost:$port/"
Write-Host ''
Write-Host '  영업도우미 노트북용 실행 중' -ForegroundColor Green
Write-Host "  주소: $url"
Write-Host '  ※ 이 검은 창을 닫으면 앱이 멈춥니다. 다 쓰면 닫으세요.'
Write-Host ''
$browser = @("$env:ProgramFiles\Google\Chrome\Application\chrome.exe", "${env:ProgramFiles(x86)}\Google\Chrome\Application\chrome.exe", "$env:LocalAppData\Google\Chrome\Application\chrome.exe", "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe", "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe") | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
if ($browser) { Start-Process -FilePath $browser -ArgumentList $url } else { Start-Process $url }
while ($listener.IsListening) {
  try { $ctx = $listener.GetContext() } catch { break }
  $res = $ctx.Response
  try {
    $rel = [System.Uri]::UnescapeDataString($ctx.Request.Url.AbsolutePath.TrimStart('/'))
    if ($rel.StartsWith('api/')) {
      try { $out = Handle-Api $ctx $rel } catch { $out = @{ ok = $false; error = "서버 오류: $($_.Exception.Message)" } }
      if ($out.error -eq 'forbidden') { $res.StatusCode = 403 }
      if ($rel -eq 'api/sms/send') { Write-Host ("  [{0}] 문자 발송 {1}" -f (Get-Date -Format 'HH:mm:ss'), $(if ($out.ok) { '접수 성공' } else { '실패: ' + $out.error })) }
      $res.ContentType = 'application/json; charset=utf-8'
      $b = [System.Text.Encoding]::UTF8.GetBytes(($out | ConvertTo-Json -Compress))
    } else {
      if ($rel -eq '' -or $rel.EndsWith('/')) { $rel = $rel + 'index.html' }
      $path = [System.IO.Path]::GetFullPath((Join-Path $rootFull $rel))
      if (-not $path.StartsWith($rootFull, [System.StringComparison]::OrdinalIgnoreCase) -or $path -like '*.local.json' -or -not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $res.StatusCode = 404; $b = [System.Text.Encoding]::UTF8.GetBytes('Not found')
      } else {
        $ext = [System.IO.Path]::GetExtension($path).ToLower()
        $res.ContentType = if ($mime.ContainsKey($ext)) { $mime[$ext] } else { 'application/octet-stream' }
        $res.AddHeader('Cache-Control', 'no-cache')
        $b = [System.IO.File]::ReadAllBytes($path)
      }
    }
    $res.ContentLength64 = $b.Length
    $res.OutputStream.Write($b, 0, $b.Length)
  } catch { } finally { try { $res.OutputStream.Close() } catch { } }
}
