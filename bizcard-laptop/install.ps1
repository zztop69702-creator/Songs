# 영업도우미 노트북용 - 설치 (바탕화면 아이콘 만들기)
# 1) 이 폴더를 %LOCALAPPDATA%\YeongilSales 로 복사 (업데이트 시 솔라피 설정·기록은 그대로 유지)
# 2) 바탕화면에 '영업도우미' 아이콘 생성  3) 바로 실행
$ErrorActionPreference = 'Stop'
$src = Split-Path -Parent $MyInvocation.MyCommand.Path
$dst = Join-Path $env:LOCALAPPDATA 'YeongilSales'
Write-Host ''
Write-Host '  영업도우미 노트북용 설치 중...' -ForegroundColor Green
try {
  # 실행 중인 이전 버전 영업도우미 서버 종료 (다른 폴더에서 켜 둔 것 포함 → 주소가 바뀌어 설정이 안 보이는 문제 방지)
  Get-CimInstance Win32_Process -Filter "Name='powershell.exe'" -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like "*-File*server.ps1*" } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
} catch { }
New-Item -ItemType Directory -Force -Path $dst | Out-Null
if ([System.IO.Path]::GetFullPath($src).TrimEnd('\') -ne [System.IO.Path]::GetFullPath($dst).TrimEnd('\')) {
  # 솔라피 설정(solapi.local.json)이 압축 폴더에 있으면(이전 버전에서 복사해 둔 경우) 함께 옮김, 설치 폴더에 이미 있으면 덮어쓰지 않음
  Get-ChildItem -LiteralPath $src -Recurse -File | ForEach-Object {
    $rel = $_.FullName.Substring($src.Length).TrimStart('\')
    $to = Join-Path $dst $rel
    if ($_.Name -eq 'solapi.local.json' -and (Test-Path -LiteralPath $to)) { return }
    if ($_.Name -eq 'server-log.txt') { return }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $to) | Out-Null
    Copy-Item -LiteralPath $_.FullName -Destination $to -Force
  }
}
Get-ChildItem -LiteralPath $dst -Recurse -File | Unblock-File -ErrorAction SilentlyContinue
$desktop = [Environment]::GetFolderPath('Desktop')
$lnk = Join-Path $desktop '영업도우미.lnk'
$ws = New-Object -ComObject WScript.Shell
$sc = $ws.CreateShortcut($lnk)
$sc.TargetPath = Join-Path $dst '실행_윈도우.bat'
$sc.WorkingDirectory = $dst
$sc.IconLocation = (Join-Path $dst 'app.ico') + ',0'
$sc.WindowStyle = 7   # 검은 창은 작업표시줄에 최소화
$sc.Description = '(주)영일 영업도우미 - 명함 스캔·문자 발송'
$sc.Save()
Write-Host "  설치 위치: $dst"
Write-Host "  바탕화면 아이콘: $lnk"
Write-Host '  설치 완료! 이제부터는 바탕화면의 [영업도우미] 아이콘으로 실행하세요.' -ForegroundColor Green
Write-Host '  (압축 푼 폴더와 받은 zip 파일은 지워도 됩니다)'
Write-Host ''
Start-Process -FilePath $lnk
Start-Sleep -Seconds 4
