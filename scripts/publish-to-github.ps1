<#
.SYNOPSIS
    把 GregNuovo 工程一键发布到你的 GitHub 仓库。

.DESCRIPTION
    做四件事：预检（git / GitHub 连通性）→ 初始化本地仓库并提交 → 设置远端 → 推送。
    会拒绝把依赖 jar、上游源码副本、构建产物提交进去。

.PARAMETER RepoUrl
    你的仓库地址，例如 https://github.com/yourname/GregNuovo.git

.PARAMETER CreateRepo
    可选：仓库还不存在时，用 gh CLI 自动创建（需要已安装并登录 GitHub CLI）。
    传入仓库名，例如 -CreateRepo GregNuovo

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\publish-to-github.ps1 -RepoUrl https://github.com/yourname/GregNuovo.git

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\publish-to-github.ps1 `
        -RepoUrl https://github.com/yourname/GregNuovo.git -UserName yourname -UserEmail you@example.com
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$RepoUrl,
    [string]$Branch = 'main',
    [string]$UserName,
    [string]$UserEmail,
    [string]$Message = 'GregNuovo 1.1.0',
    [string]$CreateRepo,
    [switch]$SkipPush
)

$ErrorActionPreference = 'Stop'

function Info($m) { Write-Host "· $m" -ForegroundColor Cyan }
function Ok($m)   { Write-Host "✓ $m" -ForegroundColor Green }
function Warn($m) { Write-Host "! $m" -ForegroundColor Yellow }
function Die($m)  { Write-Host "✗ $m" -ForegroundColor Red; exit 1 }

# ---------- 1) 定位工程根目录 ----------
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path (Join-Path $root 'build.gradle'))) {
    Die "没找到 build.gradle。请把本脚本放在工程的 scripts/ 目录下再运行。"
}
Info "工程根目录：$root"

# ---------- 2) 预检：git ----------
$git = (Get-Command git -ErrorAction SilentlyContinue).Source
if (-not $git) {
    Die @"
本机没安装 Git，无法推送。请先安装 Git for Windows，然后重开终端再运行本脚本：
  官方下载：https://git-scm.com/download/win
  国内镜像：https://registry.npmmirror.com/-/binary/git-for-windows/
  （也可以用 GitHub Desktop，它自带 git）
"@
}
Ok "git：$git"

# ---------- 3) 预检：GitHub 连通性 ----------
$hostsFile = Join-Path $env:SystemRoot 'System32\drivers\etc\hosts'
$hostsBlocked = $false
try {
    if (Test-Path $hostsFile) {
        $hostsBlocked = [bool](Get-Content $hostsFile -ErrorAction SilentlyContinue |
            Where-Object { $_ -match '^\s*127\.0\.0\.1\s+.*github\.com' })
    }
} catch {}

$reachable = $false
try {
    $client = New-Object System.Net.Sockets.TcpClient
    $client.Connect('github.com', 443)
    $reachable = $client.Connected
    $client.Close()
} catch { $reachable = $false }

if (-not $reachable) {
    Warn "无法连接 github.com:443"
    if ($hostsBlocked) {
        Warn "原因：hosts 文件里有把 github.com 指向 127.0.0.1 的屏蔽条目"
        Warn "  文件：$hostsFile"
        Warn "  处理：用管理员权限编辑该文件，删掉/注释所有含 github 的行，然后执行 ipconfig /flushdns"
    } else {
        Warn "  处理：检查网络或代理；用代理时先设置 `$env:HTTPS_PROXY = 'http://127.0.0.1:端口'"
    }
    if (-not $SkipPush) { Die "网络不通，已停止。只做本地提交可加 -SkipPush。" }
}

# ---------- 4) 本地提交 ----------
Push-Location $root
try {
    if (-not (Test-Path (Join-Path $root '.git'))) {
        & $git init | Out-Null
        Ok "已初始化本地仓库"
    } else {
        Ok "本地仓库已存在（.git）"
    }

    $name = (& $git config user.name) 2>$null
    if (-not $name) {
        if (-not $UserName) { Die "git 还没配置提交身份。请加参数：-UserName 你的名字 -UserEmail 你的邮箱" }
        & $git config user.name $UserName
    }
    $mail = (& $git config user.email) 2>$null
    if (-not $mail) {
        if (-not $UserEmail) { Die "git 还没配置提交邮箱。请加参数：-UserEmail 你的邮箱" }
        & $git config user.email $UserEmail
    }

    & $git add -A

    # 安全检查：依赖 jar / 上游源码 / 构建产物 一律不许进仓库
    $staged = @(& $git diff --cached --name-only)
    $bad = $staged | Where-Object {
        $_ -match '^libs/.+\.jar$' -or $_ -match '^refsrc/' -or $_ -match '^build/' -or
        $_ -match '^\.ghome/' -or $_ -match '^run/' -or $_ -match '^dist/'
    }
    if ($bad) { Die ("以下文件不应提交（请检查 .gitignore）：`n  " + ($bad -join "`n  ")) }
    Ok "本次暂存 $($staged.Count) 个文件"

    if ($staged.Count -gt 0) {
        & $git commit -m $Message | Out-Null
        Ok "已提交：$Message"
    } else {
        Info "没有新的改动需要提交"
    }
    & $git branch -M $Branch

    # 可选：用 gh 创建远端仓库
    if ($CreateRepo) {
        $gh = (Get-Command gh -ErrorAction SilentlyContinue).Source
        if ($gh) {
            Info "用 gh 创建仓库 $CreateRepo ..."
            & $gh repo create $CreateRepo --public --source . --remote origin --push
            if ($LASTEXITCODE -eq 0) { Ok "已创建并推送：$CreateRepo"; return }
            Warn "gh repo create 失败，改为手动设置远端"
        } else {
            Warn "没找到 gh CLI，跳过自动建仓（可先到 GitHub 网页新建空仓库）"
        }
    }

    $remotes = @(& $git remote)
    if ($remotes -contains 'origin') {
        & $git remote set-url origin $RepoUrl
    } else {
        & $git remote add origin $RepoUrl
    }
    Ok "远端 origin = $RepoUrl"

    if ($SkipPush) { Info "已按 -SkipPush 跳过推送"; return }

    Info "推送中…… 首次会要求登录：浏览器授权，或用 Personal Access Token 当密码"
    & $git push -u origin $Branch
    if ($LASTEXITCODE -ne 0) {
        Die @"
推送失败。常见原因：
  1) GitHub 上还没建这个仓库 —— 请先在网页新建**空仓库**（不要勾 Add a README / .gitignore / license）；
  2) 账号没有写权限，或登录的不是仓库所有者；
  3) 网络/代理中断。
"@
    }
    Ok "推送完成：$RepoUrl"
    Info "下一步：在 GitHub 上 Releases → Draft a new release，Tag 填 v1.1.0，附件上传 build/libs/gregnuovo-1.1.0.jar"
} finally {
    Pop-Location
}
