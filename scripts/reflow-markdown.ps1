# reflow-markdown.ps1 —— 统一 Markdown 排版，让“源码行结构 = GitHub 渲染结果”
#
# 为什么需要它：
#   CommonMark/GFM 会把段落内的单个换行当作空格，把多行并成一段。
#   按“长句折行”写的文档在 GitHub 上会显示成一大段（或反过来被 hardbreak 拆成一行一句）。
#   本脚本把每个「逻辑块」（段落 / 列表项）合并成**单独一行**，
#   于是无论渲染器是否 hardbreak，显示结果都一致。
#
# 保守规则（不会误伤代码）：
#   * 代码围栏（``` / ~~~）内部原样保留；
#   * 缩进 ≥4 空格的行走 Markdown 缩进代码块，一律不合并；
#   * 标题、表格行、列表项、引用、HTML 行各自成块，不互相吞并；
#   * 列表/表格紧跟在正文段落后时自动补空行（否则 GitHub 可能不识别为列表/表格）；
#   * 中英混排时，只有两侧都是中日韩字符才不加空格。
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File scripts\reflow-markdown.ps1           # 处理全部 .md
#   powershell -ExecutionPolicy Bypass -File scripts\reflow-markdown.ps1 -DryRun    # 只报告不改动

[CmdletBinding()]
param(
    [string[]]$Path,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Path) {
    $Path = @(Get-ChildItem $root -Recurse -File -Filter *.md -ErrorAction SilentlyContinue |
        Where-Object { $_.FullName -notmatch '\\(build|\.ghome|\.gradle|run|refsrc|dist|node_modules)\\' } |
        Select-Object -ExpandProperty FullName)
}

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$utf8Bom = New-Object System.Text.UTF8Encoding($true)

function Is-CJK([char]$c) {
    return ($c -ge [char]0x3000 -and $c -le [char]0x303F) -or
           ($c -ge [char]0x4E00 -and $c -le [char]0x9FFF) -or
           ($c -ge [char]0xFF00 -and $c -le [char]0xFFEF)
}

function Join-Text([string]$a, [string]$b) {
    $b = $b.Trim()
    if ($a.Length -eq 0) { return $b }
    if ($b.Length -eq 0) { return $a }
    $prev = $a[$a.Length - 1]
    $next = $b[0]
    # 两侧都是中日韩字符：直接相接，不插空格
    if ((Is-CJK $prev) -and (Is-CJK $next)) { return $a + $b }
    # 中文与开括号/开引号相邻时不插空格（“、[链接]”“保证(1)”），英文则照常插空格（“slot (not pushed…”）
    # 注意：弯引号不能字面写进 PS 字符串（PS 5.1 会把 U+2018/201C 当字符串分隔符），用 char 拼接
    $openers = '([{（〔【「『《〈' + [char]0x201C + [char]0x2018
    if ((Is-CJK $prev) -and ($openers.IndexOf($next) -ge 0)) { return $a + $b }
    if ((Is-CJK $next) -and ($openers.IndexOf($prev) -ge 0)) { return $a + $b }
    return $a + ' ' + $b
}

function Test-BlockStart([string]$line) {
    if ($line -match '^\s*(#{1,6})\s') { return $true }
    if ($line -match '^\s*(```|~~~)') { return $true }
    if ($line -match '^\s*\|') { return $true }
    if ($line -match '^\s*>') { return $true }
    if ($line -match '^\s*([-*+]|\d+[.)])\s') { return $true }
    if ($line -match '^\s*<') { return $true }
    if ($line -match '^\s*([-*_]\s*){3,}$') { return $true }
    if ($line -match '^\s{4,}\S') { return $true }
    return $false
}

function Test-ProseLine([string]$line) {
    if ($line -match '^\s*$') { return $false }
    if ($line -match '^\s*(#{1,6})\s') { return $false }
    if ($line -match '^\s*\|') { return $false }
    if ($line -match '^\s*>') { return $false }
    if ($line -match '^\s*([-*+]|\d+[.)])\s') { return $false }
    if ($line -match '^\s*<') { return $false }
    if ($line -match '^\s*(```|~~~)') { return $false }
    return $true
}

$totalBefore = 0
$totalAfter = 0
$changed = 0

foreach ($file in $Path) {
    $bytes = [System.IO.File]::ReadAllBytes($file)
    $hasBom = ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF)
    $text = [System.IO.File]::ReadAllText($file, $utf8NoBom) -replace "`r`n", "`n"
    $lines = $text -split "`n", -1

    $out = New-Object System.Collections.Generic.List[string]
    $pending = $null
    $inFence = $false
    $fenceMarker = ''

    foreach ($raw in $lines) {
        $line = $raw -replace '\s+$', ''

        if (-not $inFence -and $line -match '^\s*(```|~~~)') {
            if ($null -ne $pending) { $out.Add($pending); $pending = $null }
            $inFence = $true
            $fenceMarker = $Matches[1]
            $out.Add($line)
            continue
        }
        if ($inFence) {
            $out.Add($line)
            if ($line -match ('^\s*' + [regex]::Escape($fenceMarker))) { $inFence = $false }
            continue
        }
        if ($line -match '^\s*$') {
            if ($null -ne $pending) { $out.Add($pending); $pending = $null }
            $out.Add('')
            continue
        }

        $startsNew = Test-BlockStart $line

        # 列表/表格紧跟在正文段落后：补空行
        if ($startsNew -and ($line -match '^\s*\|' -or $line -match '^\s*([-*+]|\d+[.)])\s')) {
            if ($out.Count -gt 0 -and $out[$out.Count - 1] -ne '' -and (Test-ProseLine $out[$out.Count - 1])) {
                $out.Add('')
            }
        }

        if ($startsNew) {
            if ($null -ne $pending) { $out.Add($pending); $pending = $null }
            # 列表项要“能被续接”：它后面的缩进续行要并进这一项，而不是各自成行
            if ($line -match '^\s*([-*+]|\d+[.)])\s') {
                $pending = $line
            } else {
                # 标题/表格/围栏/HTML/缩进代码：独立成行，不吸收后续行
                $out.Add($line)
            }
        } else {
            if ($null -eq $pending) { $pending = $line } else { $pending = Join-Text $pending $line }
        }
    }
    if ($null -ne $pending) { $out.Add($pending) }

    while ($out.Count -gt 0 -and $out[$out.Count - 1] -eq '') { $out.RemoveAt($out.Count - 1) }
    $result = ($out -join "`n") + "`n"

    $totalBefore += $lines.Count
    $totalAfter += $out.Count
    if ($result -ne $text) {
        $changed++
        "{0,-46} {1,5} 行 -> {2,5} 行" -f $file.Replace("$root\", ''), $lines.Count, $out.Count
        if (-not $DryRun) {
            $enc = if ($hasBom) { $utf8Bom } else { $utf8NoBom }
            [System.IO.File]::WriteAllText($file, $result, $enc)
        }
    }
}

Write-Host ""
if ($changed -eq 0) {
    Write-Host "全部文件已是“一段一行”，无需改动。" -ForegroundColor Green
} else {
    $verb = if ($DryRun) { '需要排版' } else { '已排版' }
    Write-Host ("{0} 个文件{1}（总行数 {2} -> {3}）" -f $changed, $verb, $totalBefore, $totalAfter) -ForegroundColor Green
}
