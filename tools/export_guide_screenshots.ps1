param([string]$SourceDirectory = (Join-Path $PSScriptRoot '../build/client-smoke'))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assetDirectory = Join-Path $PSScriptRoot '../src/main/resources/assets/astral_repository/textures/gui/guide'
New-Item -ItemType Directory -Force -Path $assetDirectory | Out-Null
$captures = @(
    @{Name='wand_editor'; Source='wand/artwork.png'; X=130; Y=84; W=840; H=580},
    @{Name='painted_runes'; Source='wand/placed.png'; X=430; Y=124; W=185; H=185},
    @{Name='nexus'; Source='nexus.png'; X=232; Y=108; W=636; H=532},
    @{Name='nexus_cursor'; Source='nexus_cursor.png'; X=232; Y=108; W=636; H=532},
    @{Name='settings'; Source='rune_inventory.png'; X=286; Y=106; W=494; H=536},
    @{Name='filter_search'; Source='rune_inventory.png'; X=286; Y=106; W=494; H=536},
    @{Name='hover'; Source='rune_hover_builtin.png'; X=490; Y=255; W=270; H=200},
    @{Name='tome'; Source='tome_inventory.png'; X=332; Y=68; W=426; H=612},
    @{Name='crystals'; Source='astral_plane_front.png'; X=805; Y=260; W=235; H=235},
    @{Name='tools'; Source='item_overlays_default.png'; X=40; Y=150; W=1020; H=490}
)
$manifest = foreach ($capture in $captures) {
    $sourcePath = (Resolve-Path (Join-Path $SourceDirectory $capture.Source)).Path
    $source = [Drawing.Bitmap]::new($sourcePath)
    $canvas = [Drawing.Bitmap]::new(256,256,[Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $graphics = [Drawing.Graphics]::FromImage($canvas)
    try {
        if ($source.Width -ne 1100 -or $source.Height -ne 750) { throw "Expected 1100x750 client capture: $sourcePath" }
        # Patchouli's image page samples only the upper-left 200x200 of a 256x256 texture.
        $graphics.Clear([Drawing.Color]::Transparent)
        $graphics.FillRectangle([Drawing.Brushes]::Black,0,0,200,200)
        $scale = [Math]::Min(200.0/$capture.W,200.0/$capture.H)
        $width = [int][Math]::Round($capture.W*$scale)
        $height = [int][Math]::Round($capture.H*$scale)
        $destination = [Drawing.Rectangle]::new([int]((200-$width)/2),[int]((200-$height)/2),$width,$height)
        $graphics.InterpolationMode = [Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.DrawImage($source,$destination,$capture.X,$capture.Y,$capture.W,$capture.H,[Drawing.GraphicsUnit]::Pixel)
        $outputPath = Join-Path $assetDirectory ($capture.Name+'.png')
        $canvas.Save($outputPath,[Drawing.Imaging.ImageFormat]::Png)
        [ordered]@{asset=$capture.Name+'.png';source=$capture.Source;sourceSha256=(Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLower();crop=@($capture.X,$capture.Y,$capture.W,$capture.H)}
    } finally { $graphics.Dispose();$canvas.Dispose();$source.Dispose() }
}
$manifest | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $SourceDirectory 'guide-image-provenance.json') -Encoding utf8
Write-Output "Exported $($captures.Count) Patchouli illustrations from actual client screenshots."
