# Import the supplied remote sprite unchanged, including its dimensions and alpha.
param([string]$SourcePath = (Join-Path $PSScriptRoot 'art/remote-orb.png'))
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
$destination=Join-Path $taskRoot 'src/main/resources/assets/astral_repository/textures/item/astral_nexus.png'
Copy-Item -LiteralPath $SourcePath -Destination $destination
Write-Output "Imported supplied remote orb texture unchanged: $destination"
