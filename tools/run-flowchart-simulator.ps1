param(
    [ValidateRange(1024, 65535)][int]$Port = 18333,
    [switch]$Check,
    [string]$Jdk = 'C:/Program Files/Java/jdk-21'
)

$ErrorActionPreference = 'Stop'
$simulatorRoot = Split-Path $PSScriptRoot -Parent
$tempRoot = Join-Path $simulatorRoot 'build/tmp/r01-java'
$simulatorClasses = Join-Path $simulatorRoot 'build/simulator/standalone-classes'
$javac = Join-Path $Jdk 'bin/javac.exe'
$java = Join-Path $Jdk 'bin/java.exe'
if (!(Test-Path -LiteralPath $javac)) { throw "JDK 21 not found: $Jdk" }
if (!(Test-Path -LiteralPath $tempRoot)) { throw "Expected project-local temp directory does not exist: $tempRoot" }
$env:TEMP = (Resolve-Path -LiteralPath $tempRoot).Path
$env:TMP = $env:TEMP
$gsonRoot = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson'
$gson = Get-ChildItem -LiteralPath $gsonRoot -Recurse -Filter 'gson-*.jar' -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '-(sources|javadoc)\.jar$' } | Sort-Object FullName | Select-Object -Last 1
if (!$gson) { throw "Gson jar not found in the existing Gradle cache: $gsonRoot" }
New-Item -ItemType Directory -Force -Path $simulatorClasses | Out-Null
$sources = @(
    (Join-Path $simulatorRoot 'src/main/java/ink/myumoon/portholequest/model/QuestLogSnapshot.java'),
    (Join-Path $simulatorRoot 'src/main/java/ink/myumoon/portholequest/model/QuestFlowchartLayout.java'),
    (Join-Path $simulatorRoot 'src/main/java/ink/myumoon/portholequest/model/QuestFlowchartCase.java'),
    (Join-Path $simulatorRoot 'src/main/java/ink/myumoon/portholequest/model/QuestFlowchartRouting.java')
)
$sources += Get-ChildItem -LiteralPath (Join-Path $simulatorRoot 'src/simulator/java') -Recurse -Filter '*.java' |
    Select-Object -ExpandProperty FullName
& $javac --release 21 --add-modules jdk.httpserver -encoding UTF-8 -classpath $gson.FullName -d $simulatorClasses @sources
if ($LASTEXITCODE -ne 0) { throw "Simulator compilation failed ($LASTEXITCODE)" }
if ($Check) {
    & $java --add-modules jdk.httpserver -cp "$simulatorClasses;$($gson.FullName)" ink.myumoon.portholequest.simulator.QuestFlowchartSimulatorCheck
} else {
    & $java --add-modules jdk.httpserver -cp "$simulatorClasses;$($gson.FullName)" ink.myumoon.portholequest.simulator.QuestFlowchartSimulator `
        (Join-Path $simulatorRoot 'src/simulator/web/index.html') $Port
}
if ($LASTEXITCODE -ne 0) { throw "Simulator exited with code $LASTEXITCODE" }
