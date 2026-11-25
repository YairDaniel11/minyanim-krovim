# מעלה את מפתח החתימה הקבוע (שנוצר מחוץ למאגר) ל-GitHub Secrets של המאגר.
# הרצה חד-פעמית:  powershell -File tools\set-signing-secrets.ps1
# דורש gh מחובר (gh auth status). הסודות לא נכתבים לקובץ ולא מודפסים.
param(
    [string]$Repo = "YairDaniel11/minyanim-krovim",
    [string]$Dir = (Join-Path $env:USERPROFILE "minyanim-signing")
)
$ErrorActionPreference = "Stop"
$ks = Join-Path $Dir "minyanim-release.keystore"
$info = Get-Content (Join-Path $Dir "signing-info.txt") -Encoding UTF8
$pw = ($info | Where-Object { $_ -like "store/key password:*" }) -replace "^store/key password:\s*", ""
if (-not (Test-Path $ks) -or -not $pw) { throw "keystore / signing-info.txt not found in $Dir" }

[Convert]::ToBase64String([IO.File]::ReadAllBytes($ks)) | gh secret set KEYSTORE_BASE64 -R $Repo
$pw | gh secret set KEYSTORE_PASSWORD -R $Repo
$pw | gh secret set KEY_PASSWORD -R $Repo
"minyanim" | gh secret set KEY_ALIAS -R $Repo
gh secret list -R $Repo
