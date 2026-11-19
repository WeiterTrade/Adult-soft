# Добавить биографию в офлайн-библиотеку Adult Aristocrat
# 1) Скопируйте биографию из чата (Ctrl+C)  2) Запустите add-bio.bat  3) Введите имя актрисы/актёра
Add-Type -AssemblyName System.Windows.Forms
$ErrorActionPreference = 'Stop'
$cfg = Join-Path $PSScriptRoot 'bio-folder.txt'

# Папка .bio (запоминается после первого раза)
$dir = $null
if (Test-Path $cfg) { $dir = (Get-Content $cfg -Encoding UTF8 -TotalCount 1).Trim() }
if (-not $dir -or -not (Test-Path $dir)) {
    $dlg = New-Object System.Windows.Forms.FolderBrowserDialog
    $dlg.Description = 'Выберите папку с фильмами (в ней будет создана папка .bio)'
    if ($dlg.ShowDialog() -ne 'OK') { exit }
    $dir = Join-Path $dlg.SelectedPath '.bio'
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Set-Content -Path $cfg -Value $dir -Encoding UTF8
}

while ($true) {
    $text = [System.Windows.Forms.Clipboard]::GetText()
    if (-not $text -or $text.Trim().Length -lt 20) {
        Write-Host 'В буфере обмена нет биографии. Скопируйте её из чата и нажмите Enter (или закройте окно).' -ForegroundColor Yellow
        Read-Host | Out-Null
        continue
    }
    Write-Host ''
    $first = ($text.Trim() -split "`n" | Where-Object { $_.Trim() -and $_.Trim() -ne 'Биография' } | Select-Object -First 1)
    if ($first.Length -gt 110) { $first = $first.Substring(0, 110) + '…' }
    Write-Host ('В буфере: ' + $first) -ForegroundColor Cyan
    $name = Read-Host 'Имя латиницей, как в папке .actors (например: Phoenix Marie). Пусто — выход'
    if (-not $name.Trim()) { break }
    $file = Join-Path $dir (($name.Trim() -replace '\s+', '_') + '.txt')
    [System.IO.File]::WriteAllText($file, $text.Trim(), (New-Object System.Text.UTF8Encoding($true)))
    Write-Host "Сохранено: $file" -ForegroundColor Green
    Write-Host 'Скопируйте следующую биографию и нажмите Enter (или закройте окно).'
    Read-Host | Out-Null
}
