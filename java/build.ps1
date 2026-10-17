$ErrorActionPreference = "Stop"
$jdk = "C:\Users\guido\.antigravity-ide\extensions\redhat.java-1.57.0-win32-x64\jre\25.0.4.1-win32-x86_64\bin"

if (-not (Test-Path "java\target\classes")) {
    New-Item -ItemType Directory -Path "java\target\classes" -Force | Out-Null
}

$files = (Get-ChildItem -Recurse java\src\main\java -Filter *.java).FullName | ForEach-Object {
    '"' + $_.Replace('\', '/') + '"'
}
Set-Content -Path "java\sources.txt" -Value $files -Encoding Ascii

Write-Host "Compiling Java sources..."
& "$jdk\javac.exe" --release 8 -encoding UTF-8 -d "java\target\classes" "@java\sources.txt"
if ($LASTEXITCODE -ne 0) {
    Remove-Item "java\sources.txt" -ErrorAction SilentlyContinue
    exit $LASTEXITCODE
}

Write-Host "Packaging JAR..."
& "$jdk\jar.exe" cfe "java\target\cpssp-solver-1.0.0.jar" cpssp.Main -C "java\target\classes" .
if ($LASTEXITCODE -ne 0) {
    Remove-Item "java\sources.txt" -ErrorAction SilentlyContinue
    exit $LASTEXITCODE
}

Remove-Item "java\sources.txt" -ErrorAction SilentlyContinue
Write-Host "Build successful! Target: java\target\cpssp-solver-1.0.0.jar"
