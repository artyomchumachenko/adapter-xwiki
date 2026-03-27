param(
    [string]$ImageName = "xwiki-sast",
    [string]$OutputDir = "reports/sast"
)

$ErrorActionPreference = "Stop"

New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null

Write-Host "Building Docker image $ImageName from Dockerfile.sast"
docker build -f Dockerfile.sast -t $ImageName .

Write-Host "Compiling project in Docker"
docker run --rm `
    -v "${PWD}:/workspace" `
    -w /workspace `
    $ImageName `
    "mvn -DskipTests compile"

Write-Host "Generating XML report"
docker run --rm `
    -v "${PWD}:/workspace" `
    -w /workspace `
    $ImageName `
    "mvn -P sast -DskipTests -Dspotbugs.xmlOutput=true -Dspotbugs.htmlOutput=false -Dspotbugs.sarifOutput=false spotbugs:spotbugs"

Copy-Item "target/spotbugsXml.xml" "$OutputDir/spotbugs-report.xml" -Force

Write-Host "Generating HTML report"
docker run --rm `
    -v "${PWD}:/workspace" `
    -w /workspace `
    $ImageName `
    "mvn -P sast -DskipTests -Dspotbugs.xmlOutput=false -Dspotbugs.htmlOutput=true -Dspotbugs.outputDirectory=target/site spotbugs:spotbugs"

Copy-Item "target/site/spotbugs.html" "$OutputDir/spotbugs-report.html" -Force

Write-Host "Generating SARIF report"
docker run --rm `
    -v "${PWD}:/workspace" `
    -w /workspace `
    $ImageName `
    "mvn -P sast -DskipTests -Dspotbugs.xmlOutput=false -Dspotbugs.htmlOutput=false -Dspotbugs.sarifOutput=true -Dspotbugs.sarifOutputDirectory=target -Dspotbugs.sarifOutputFilename=spotbugs.sarif.json spotbugs:spotbugs"

Copy-Item "target/spotbugs.sarif.json" "$OutputDir/spotbugs.sarif.json" -Force

Write-Host "SAST reports were saved to $OutputDir"
