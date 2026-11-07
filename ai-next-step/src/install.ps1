$ErrorActionPreference = 'Stop'
$name = '경험곱하기AI'
$ver = 'v1.8'
# 이전 이름. 저장 폴더(입력값이 들어 있는 브라우저 프로필)는 이 이름을 그대로 써서 입력값을 지킨다
$oldName = 'AI다음행보'
$text = [IO.File]::ReadAllText($env:SELF)
function Block($tag) { [Convert]::FromBase64String([regex]::Match($text, "$tag\r?\n([A-Za-z0-9+/=]+)").Groups[1].Value) }

# 앱 위치가 바뀌어도 입력값이 남도록 프로필과 위치 기록은 항상 이 폴더에 둔다
$data = Join-Path $env:LOCALAPPDATA $oldName
$record = Join-Path $data 'install_path.txt'
$previous = if (Test-Path -LiteralPath $record) { [IO.File]::ReadAllText($record).Trim() } else { $data }

Add-Type -AssemblyName System.Windows.Forms
# 묻지 않고 자동 설치: 설치 파일을 둔 폴더(사용자가 저장한 곳) 안에 '$name' 폴더를 만든다.
# ZIP 안에서 바로 실행해 임시 폴더에 있으면 기본 위치(LOCALAPPDATA)에 설치한다
$here = Split-Path -Parent $env:SELF
$temp = [IO.Path]::GetTempPath().TrimEnd('\')
# 임시 폴더 경로가 짧은 이름(8.3)으로 올 수도 있어 폴더 이름으로도 확인한다
$inTemp = $here.StartsWith($temp, [StringComparison]::OrdinalIgnoreCase) -or $here -match '\\AppData\\Local\\Temp(\\|$)' -or $here -match '\\Temp\d*_[^\\]*\.zip'
$base = if ($inTemp) { $env:LOCALAPPDATA } else { $here }
$dir = if ((Split-Path $base -Leaf) -eq $name) { $base } else { Join-Path $base $name }

New-Item -ItemType Directory -Force $dir, $data | Out-Null
$html = Join-Path $dir "${name}_$ver.html"
$ico = Join-Path $dir "$name.ico"
[IO.File]::WriteAllBytes($html, (Block '__HTML__'))
[IO.File]::WriteAllBytes($ico, (Block '__ICO__'))
# 새 파일을 쓴 뒤에 지워서, 쓰기에 실패해도 이전 앱이 남게 한다
Get-ChildItem -LiteralPath $previous, $dir -Filter "${name}_v*.html" -ErrorAction SilentlyContinue | Where-Object { $_.FullName -ne $html } | Remove-Item -Force
# 이전 이름의 앱 파일, 아이콘, 바로가기를 정리한다. 입력값은 저장 폴더의 프로필에 있어 지워지지 않는다
Get-ChildItem -LiteralPath $previous, $dir -Filter "${oldName}_v*.html" -ErrorAction SilentlyContinue | Remove-Item -Force
Remove-Item -LiteralPath (Join-Path $previous "$oldName.ico") -Force -ErrorAction SilentlyContinue
if ($previous -ne $dir) { Remove-Item -LiteralPath (Join-Path $previous "$name.ico") -Force -ErrorAction SilentlyContinue }
if ($previous -ne $data -and $previous -ne $dir -and (Test-Path -LiteralPath $previous) -and -not (Get-ChildItem -LiteralPath $previous -Force)) { Remove-Item -LiteralPath $previous -Force }
[IO.File]::WriteAllText($record, $dir)

$browsers = @(
  "$env:ProgramFiles\Google\Chrome\Application\chrome.exe",
  "$env:LOCALAPPDATA\Google\Chrome\Application\chrome.exe",
  "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
  "$env:ProgramFiles\Microsoft\Edge\Application\msedge.exe"
)
$browser = $browsers | Where-Object { Test-Path $_ } | Select-Object -First 1
$url = ([Uri]$html).AbsoluteUri

$shell = New-Object -ComObject WScript.Shell
$folders = @([Environment]::GetFolderPath('Desktop'), [Environment]::GetFolderPath('Programs'))
foreach ($folder in $folders) { Remove-Item -LiteralPath (Join-Path $folder "$oldName.lnk") -Force -ErrorAction SilentlyContinue }
foreach ($folder in $folders) {
  $link = $shell.CreateShortcut((Join-Path $folder "$name.lnk"))
  # 평소 쓰는 브라우저 설정과 분리된 전용 프로필로 앱 창을 연다
  if ($browser) { $link.TargetPath = $browser; $link.Arguments = "--user-data-dir=`"$data\profile`" --no-first-run --no-default-browser-check --app=`"$url`"" } else { $link.TargetPath = $html }
  $link.IconLocation = "$ico,0"
  $link.Description = "$name $ver"
  $link.WorkingDirectory = $dir
  $link.Save()
}
try { & ie4uinit.exe -show } catch { }
Write-Host "$name $ver 설치가 끝났습니다. 앱을 실행합니다."
Start-Process (Join-Path $folders[0] "$name.lnk")
# 설치 결과를 창으로 알려 준다 (명령 창은 바로 닫히므로)
[Windows.Forms.MessageBox]::Show("바탕화면에 '$name' 아이콘을 만들고 앱을 실행했습니다.`n`n설치 폴더: $dir", "$name $ver 설치 완료") | Out-Null
