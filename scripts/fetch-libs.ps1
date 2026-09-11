# 下载编译所需的依赖 jar 到 libs/ 目录
#
# 用法（Windows PowerShell 5.1 与 PowerShell 7 均可）：
#   powershell -ExecutionPolicy Bypass -File scripts\fetch-libs.ps1
#
# 说明：本模组注入 AE2 / GTM 内部实现，依赖 jar 只用于编译与开发运行，不随仓库分发。
#      jar 文件名必须与 build.gradle 中引用的一致。
#
# 如果你电脑上已经有装好这些 mod 的整合包，也可以直接从实例的 mods/ 目录把对应文件复制过来，不必联网。

[CmdletBinding()]
param(
    # 只检查连通性与缺失情况，不下载
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'

# PowerShell 5.1 默认可能用 TLS 1.0，会导致 HTTPS 请求失败
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
} catch { }

$libsDir = Join-Path (Split-Path -Parent $PSScriptRoot) 'libs'
New-Item -ItemType Directory -Force -Path $libsDir | Out-Null
Write-Host "依赖将下载到：$libsDir" -ForegroundColor Cyan

function Test-Host($name) {
    try {
        # 先看解析结果：被 hosts 屏蔽时域名会指向 127.0.0.1 / 0.0.0.0，
        # 这种情况下 TCP 连接反而会“成功”，所以必须先排除回环地址
        $ips = [System.Net.Dns]::GetHostAddresses($name) |
            Where-Object { $_.AddressFamily -eq 'InterNetwork' } |
            ForEach-Object { $_.IPAddressToString }
        if (-not $ips) { return $false }
        if ($ips | Where-Object { $_ -like '127.*' -or $_ -eq '0.0.0.0' }) { return $false }
        $client = New-Object System.Net.Sockets.TcpClient
        $client.Connect($name, 443)
        $ok = $client.Connected
        $client.Close()
        return $ok
    } catch { return $false }
}

# ---------- 连通性预检 ----------
$canGithub = Test-Host 'github.com'
$canModrinth = Test-Host 'modrinth.com'
Write-Host ("github.com:   " + $(if ($canGithub) { '可连接' } else { '不可连接' })) -ForegroundColor $(if ($canGithub) { 'Green' } else { 'Yellow' })
Write-Host ("modrinth.com: " + $(if ($canModrinth) { '可连接' } else { '不可连接' })) -ForegroundColor $(if ($canModrinth) { 'Green' } else { 'Yellow' })

if (-not $canGithub -or -not $canModrinth) {
    $hostsFile = Join-Path $env:SystemRoot 'System32\drivers\etc\hosts'
    $blocked = $false
    try {
        if (Test-Path $hostsFile) {
            $blocked = [bool](Get-Content $hostsFile -ErrorAction SilentlyContinue |
                Where-Object { $_ -match '^\s*127\.0\.0\.1\s+.*(github|modrinth)' })
        }
    } catch { }
    Write-Host ""
    Write-Host "检测到网络受限，可能无法自动下载。" -ForegroundColor Yellow
    if ($blocked) {
        Write-Host "  原因：hosts 文件里有把 github/modrinth 指向 127.0.0.1 的屏蔽条目" -ForegroundColor Yellow
        Write-Host "  文件：$hostsFile" -ForegroundColor Yellow
        Write-Host "  处理：用管理员权限编辑该文件，删掉/注释相关行，然后执行 ipconfig /flushdns" -ForegroundColor Yellow
    } else {
        Write-Host "  处理：检查网络或代理；用代理时先执行 `$env:HTTPS_PROXY = 'http://127.0.0.1:端口'" -ForegroundColor Yellow
    }
    Write-Host "  替代方案：直接从已装好这些 mod 的整合包实例的 mods\ 目录里复制（见下表文件名）" -ForegroundColor Yellow
    Write-Host ""
}

# ---------- 清单 ----------
# GTM 不在 Modrinth，走 GitHub Release 直链；其余用 Modrinth 官方 API 解析
$manifest = @(
    @{ File = 'gtceu-1.20.1-7.3.0.jar';               Source = 'github';   Url = 'https://github.com/GregTechCEu/GregTech-Modern/releases/download/v7.3.0-1.20.1/gtceu-1.20.1-7.3.0.jar' },
    @{ File = 'appliedenergistics2-forge-15.4.10.jar'; Source = 'modrinth'; Project = 'ae2';              Version = '15.4.10'; Loader = 'forge' },
    @{ File = 'ldlib-forge-1.20.1-1.0.52.jar';         Source = 'modrinth'; Project = 'ldlib';            Version = '1.0.52';  Loader = 'forge' },
    @{ File = 'architectury-9.2.14-forge.jar';         Source = 'modrinth'; Project = 'architectury-api'; Version = '9.2.14';  Loader = 'forge' },
    @{ File = 'configuration-forge-1.20.1-3.1.0.jar';  Source = 'modrinth'; Project = 'configuration';    Version = '3.1.0';   Loader = 'forge' },
    @{ File = 'guideme-20.1.15.jar';                   Source = 'modrinth'; Project = 'guideme';          Version = '20.1.15'; Loader = 'forge' }
)

function Get-ModrinthUrl($project, $version, $loader) {
    $uri = "https://api.modrinth.com/v2/project/$project/version?loaders=%5B%22$loader%22%5D&game_versions=%5B%221.20.1%22%5D"
    $versions = Invoke-RestMethod -Uri $uri -UseBasicParsing
    $match = $versions | Where-Object { $_.version_number -eq $version } | Select-Object -First 1
    if (-not $match) {
        $available = ($versions | Select-Object -First 8 | ForEach-Object { $_.version_number }) -join ', '
        throw "Modrinth 上找不到 $project $version（forge 1.20.1）。可用版本示例：$available"
    }
    return $match.files[0].url
}

$failed = @()
foreach ($item in $manifest) {
    $target = Join-Path $libsDir $item.File
    if (Test-Path $target) {
        Write-Host "已存在，跳过：$($item.File)" -ForegroundColor DarkGray
        continue
    }
    if ($CheckOnly) {
        $failed += $item
        Write-Host "缺失：$($item.File)" -ForegroundColor Yellow
        continue
    }
    try {
        $url = $item.Url
        if ($item.Source -eq 'modrinth') {
            Write-Host "解析 $($item.File) ..."
            $url = Get-ModrinthUrl $item.Project $item.Version $item.Loader
        }
        Write-Host "下载 $($item.File) ..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri $url -OutFile $target -UseBasicParsing
        $size = (Get-Item $target).Length
        if ($size -lt 4096) { throw "文件过小（$size 字节），可能不是有效 jar" }
        Write-Host "  完成（$([math]::Round($size / 1MB, 2)) MB）" -ForegroundColor Green
    } catch {
        Write-Host "  失败：$($_.Exception.Message)" -ForegroundColor Red
        if (Test-Path $target) { Remove-Item $target -Force }
        $failed += $item
    }
}

Write-Host ""
if ($failed.Count -gt 0) {
    Write-Host "以下文件仍缺失，请手动下载后放到 $libsDir（文件名必须完全一致）：" -ForegroundColor Yellow
    foreach ($item in $failed) {
        if ($item.Source -eq 'github') {
            Write-Host ("  {0}" -f $item.File)
            Write-Host ("      {0}" -f $item.Url)
        } else {
            Write-Host ("  {0}" -f $item.File)
            Write-Host ("      https://modrinth.com/mod/{0}/versions?g=1.20.1&l=forge （选版本 {1}）" -f $item.Project, $item.Version)
        }
    }
    exit 1
}

Write-Host "依赖已就绪，当前 libs\ 内容：" -ForegroundColor Green
Get-ChildItem $libsDir -Filter *.jar | Select-Object Name, @{ n = 'MB'; e = { [math]::Round($_.Length / 1MB, 2) } } | Format-Table -AutoSize
Write-Host "下一步：gradlew build（本机离线可用 C:\gradle-8.8\bin\gradle.bat --offline build）"
exit 0
