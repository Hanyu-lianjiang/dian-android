param([switch]$IncludeDeviceChecks)
$ErrorActionPreference = 'Stop'
$required = @('JAVA_HOME', 'ANDROID_HOME', 'DORM_RELEASE_KEYSTORE', 'DORM_RELEASE_STORE_PASSWORD', 'DORM_RELEASE_KEY_ALIAS', 'DORM_RELEASE_KEY_PASSWORD')
foreach ($name in $required) {
    if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Missing environment variable: $name" }
}
$source = $PSScriptRoot
$staging = Join-Path ([IO.Path]::GetTempPath()) ('dorm-release-' + [Guid]::NewGuid().ToString('N'))
$staging = [IO.Path]::GetFullPath($staging)
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\') + '\'
if (-not $staging.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid build staging directory' }
New-Item -ItemType Directory -Path $staging | Out-Null
& {
    robocopy $source $staging /E /XD .gradle .idea build /XF local.properties /NFL /NDL /NJH /NJS /NP | Out-Null
    if ($LASTEXITCODE -ge 8) { throw 'Failed to stage Android project' }
    Push-Location $staging
    try {
        $tasks = @(':app:assembleRelease', ':app:testReleaseUnitTest', ':app:lintRelease')
        if ($IncludeDeviceChecks) { $tasks += ':app:assembleReleaseAndroidTest' }
        & ./gradlew.bat @tasks --quiet
        if ($LASTEXITCODE -ne 0) { throw 'Android build or checks failed' }
    } finally { Pop-Location }
    $apkDir = Join-Path $staging 'app/build/outputs/apk/release'
    $metadata = Get-Content -LiteralPath (Join-Path $apkDir 'output-metadata.json') -Raw | ConvertFrom-Json
    $version = $metadata.elements[0].versionName -replace '[^A-Za-z0-9._-]', '_'
    $output = Join-Path $source 'app/build/outputs/apk/release'
    New-Item -ItemType Directory -Force -Path $output | Out-Null
    $apk = Join-Path $output "dorm-electric-$version.apk"
    Copy-Item -LiteralPath (Join-Path $apkDir $metadata.elements[0].outputFile) -Destination $apk
    $tools = Get-ChildItem -LiteralPath (Join-Path $env:ANDROID_HOME 'build-tools') -Directory | Sort-Object Name -Descending
    $signer = Join-Path $tools[0].FullName 'apksigner.bat'
    & $signer verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed' }
    $reports = Join-Path $source 'app/build/verification'
    New-Item -ItemType Directory -Force -Path $reports | Out-Null
    Copy-Item -LiteralPath (Join-Path $staging 'app/build/test-results/testReleaseUnitTest') -Destination $reports -Recurse -Force
    Get-ChildItem -LiteralPath (Join-Path $staging 'app/build/reports') -Filter 'lint-results-release*' | Copy-Item -Destination $reports
    if ($IncludeDeviceChecks) {
        Copy-Item -LiteralPath (Join-Path $staging 'app/build/outputs/apk/androidTest/release/app-release-androidTest.apk') -Destination (Join-Path $reports 'visual-verification.apk')
    }
    Write-Output "Release APK: $apk"
    Get-FileHash -LiteralPath $apk -Algorithm SHA256
}
Write-Output "Build workspace and diagnostic reports: $staging"
