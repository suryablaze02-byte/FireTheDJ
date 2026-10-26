# Draws the launch video's captions and end cards as transparent PNGs, for both formats.
# Usage: powershell -File cards.ps1   (writes into launch\cards\)
Add-Type -AssemblyName System.Drawing
$dir = Join-Path $PSScriptRoot 'cards'
New-Item -ItemType Directory -Force $dir | Out-Null
$logo = [System.Drawing.Image]::FromFile((Join-Path $PSScriptRoot '..\Logo.png'))

$white = [System.Drawing.Color]::FromArgb(242, 242, 245)
$muted = [System.Drawing.Color]::FromArgb(180, 180, 190)
$fire1 = [System.Drawing.Color]::FromArgb(255, 45, 85)
$fire2 = [System.Drawing.Color]::FromArgb(255, 106, 26)
$fire3 = [System.Drawing.Color]::FromArgb(255, 194, 74)

function New-Canvas($w, $h) {
  $b = New-Object System.Drawing.Bitmap $w, $h, ([System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
  $g = [System.Drawing.Graphics]::FromImage($b)
  $g.SmoothingMode = 'AntiAlias'; $g.TextRenderingHint = 'AntiAliasGridFit'; $g.InterpolationMode = 'HighQualityBicubic'
  $g.Clear([System.Drawing.Color]::Transparent)
  return $b, $g
}

# A caption: big bold lines with a soft shadow, an orange accent bar above, over a dark fade.
function Caption($name, $w, $h, [string[]]$lines, $size, $y, $align = 'Center', $x = 0, $boxW = $w) {
  $b, $g = New-Canvas $w $h
  $fadeH = [int]($h * .42); $fy = if ($y -lt $h / 2) { 0 } else { $h - $fadeH }
  $fade = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle 0, $fy, $w, $fadeH),
    [System.Drawing.Color]::FromArgb(($(if ($fy -eq 0) { 200 } else { 0 })), 0, 0, 0),
    [System.Drawing.Color]::FromArgb(($(if ($fy -eq 0) { 0 } else { 200 })), 0, 0, 0), 90)
  $fade.WrapMode = 'TileFlipXY' # no hard seam where the gradient ends
  $g.FillRectangle($fade, 0, $fy, $w, $fadeH)
  $font = New-Object System.Drawing.Font('Segoe UI', $size, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
  $sf = New-Object System.Drawing.StringFormat; $sf.Alignment = $align
  $lineH = $size * 1.18
  $barW = 90; $barX = if ($align -eq 'Center') { $x + ($boxW - $barW) / 2 } else { $x }
  $bar = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle ([int]$barX), ([int]($y - 34)), $barW, 10), $fire1, $fire3, 0)
  $g.FillRectangle($bar, $barX, $y - 34, $barW, 10)
  for ($i = 0; $i -lt $lines.Count; $i++) {
    $r = New-Object System.Drawing.RectangleF $x, ($y + $i * $lineH), $boxW, ($lineH + 10)
    $sh = New-Object System.Drawing.RectangleF ($x + 3), ($y + $i * $lineH + 4), $boxW, ($lineH + 10)
    $g.DrawString($lines[$i], $font, (New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(150, 0, 0, 0))), $sh, $sf)
    $g.DrawString($lines[$i], $font, (New-Object System.Drawing.SolidBrush $white), $r, $sf)
  }
  $b.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $b.Dispose()
}

# The end card: logo, wordmark ("Fire" in fire colours), tagline and where to get it, on a warm dark ground.
function EndCard($name, $w, $h, $scale, $tag = 'Your AI DJ for every party') {
  $b, $g = New-Canvas $w $h
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath; $path.AddEllipse(-$w * .3, -$h * .2, $w * 1.6, $h * 1.4)
  $bg = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
  $bg.CenterColor = [System.Drawing.Color]::FromArgb(255, 59, 26, 18); $bg.SurroundColors = @([System.Drawing.Color]::FromArgb(255, 11, 11, 14))
  $g.FillRectangle($bg, 0, 0, $w, $h)
  $ls = [int](260 * $scale); $cx = $w / 2
  $top = [int]($h / 2 - 330 * $scale)
  $g.DrawImage($logo, [int]($cx - $ls / 2), $top, $ls, $ls)
  $wf = New-Object System.Drawing.Font('Segoe UI Black', [int](104 * $scale), [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
  # Typographic measuring, so "Fire" and "TheDJ" sit together as one word.
  $tight = [System.Drawing.StringFormat]::GenericTypographic
  $fireW = $g.MeasureString('Fire', $wf, 2000, $tight).Width; $djW = $g.MeasureString('TheDJ', $wf, 2000, $tight).Width
  $wx = $cx - ($fireW + $djW) / 2; $wy = $top + $ls + 22 * $scale
  $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.RectangleF $wx, $wy, $fireW, (130 * $scale)), $fire3, $fire1, 90)
  $g.DrawString('Fire', $wf, $grad, $wx, $wy, $tight)
  $g.DrawString('TheDJ', $wf, (New-Object System.Drawing.SolidBrush $white), ($wx + $fireW), $wy, $tight)
  $sf = New-Object System.Drawing.StringFormat; $sf.Alignment = 'Center'
  $tf = New-Object System.Drawing.Font('Segoe UI Semibold', [int](40 * $scale), [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
  $g.DrawString($tag, $tf, (New-Object System.Drawing.SolidBrush $white), (New-Object System.Drawing.RectangleF 0, ($wy + 150 * $scale), $w, (60 * $scale)), $sf)
  # Call to action pill.
  $pw = [int](620 * $scale); $ph = [int](92 * $scale); $px = [int]($cx - $pw / 2); $py = [int]($wy + 250 * $scale)
  $pill = New-Object System.Drawing.Drawing2D.GraphicsPath
  $pill.AddArc($px, $py, $ph, $ph, 90, 180); $pill.AddArc($px + $pw - $ph, $py, $ph, $ph, 270, 180); $pill.CloseFigure()
  $g.FillPath((New-Object System.Drawing.Drawing2D.LinearGradientBrush((New-Object System.Drawing.Rectangle $px, $py, $pw, $ph), $fire1, $fire3, 0)), $pill)
  $cf = New-Object System.Drawing.Font('Segoe UI', [int](38 * $scale), [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
  $g.DrawString("Free $([char]0x00B7) Download on GitHub", $cf, (New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)), (New-Object System.Drawing.RectangleF $px, ($py + 18 * $scale), $pw, $ph), $sf)
  $uf = New-Object System.Drawing.Font('Consolas', [int](30 * $scale), [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
  $g.DrawString('github.com/suryablaze02-byte/FireTheDJ', $uf, (New-Object System.Drawing.SolidBrush $muted), (New-Object System.Drawing.RectangleF 0, ($py + $ph + 36 * $scale), $w, (50 * $scale)), $sf)
  $b.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $b.Dispose()
}

$caps = @{
  'crates'  = @('It sorts your music', 'into genre crates');
  'set'     = @('Then DJs a non-stop set.', 'On its own.');
  'mix'     = @('Beat-matched mixes.', 'Real DJ effects.');
  'vis'     = @('5 visualizers');
  'tv'      = @('Cast them to your TV');
  'sources' = @('Your folders. YT Music.', 'Any playlist.')
}
foreach ($k in $caps.Keys) {
  # Vertical: captions across the top third. Landscape: on the left, beside the phone.
  Caption "v_$k" 1080 1920 $caps[$k] 74 210
  Caption "h_$k" 1920 1080 $caps[$k] 78 430 'Near' 120 1120
}
# Over the full-screen visualizers in landscape, captions sit centred low.
Caption 'hv_vis' 1920 1080 @('5 visualizers') 84 860
Caption 'hv_tv' 1920 1080 @('Cast them to your TV') 84 860
EndCard 'v_end' 1080 1920 1.25
EndCard 'h_end' 1920 1080 1.0

# Version 1.1: the TV app and what's new. TV scenes caption low and centred in both shapes'
# landscape, and across the top in vertical; phone scenes keep the 1.0 placement.
$caps11 = @{
  'tvnew'   = @('Now on Android TV');
  'tvhome'  = @('Start a mix', 'from the couch');
  'tvvis'   = @('Visualizers fill', 'the big screen');
  'tvrot'   = @('A new look', 'every 30 seconds');
  'tvctrl'  = @('Play, pause & stop', 'from the remote');
  'tour'    = @('New: a quick', 'guided tour');
  'live'    = @('New songs join', 'the set live');
  'same'    = @('Same AI DJ.', 'Beat-matched mixes.')
}
foreach ($k in $caps11.Keys) {
  Caption "v_$k" 1080 1920 $caps11[$k] 74 210
  Caption "h_$k" 1920 1080 $caps11[$k] 78 430 'Near' 120 1120
  Caption "hv_$k" 1920 1080 @($caps11[$k] -join ' ') 76 900
}
EndCard 'v_end11' 1080 1920 1.25 "Version 1.1 $([char]0x00B7) Phone + Android TV"
EndCard 'h_end11' 1920 1080 1.0 "Version 1.1 $([char]0x00B7) Phone + Android TV"

# Backgrounds behind the phone screen: the splash's warm glow on near-black.
function Ground($name, $w, $h) {
  $b, $g = New-Canvas $w $h
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath; $path.AddEllipse(-$w * .4, $h * .1, $w * 1.8, $h * 1.3)
  $br = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
  $br.CenterColor = [System.Drawing.Color]::FromArgb(255, 70, 26, 16); $br.SurroundColors = @([System.Drawing.Color]::FromArgb(255, 10, 10, 13))
  $g.Clear([System.Drawing.Color]::FromArgb(255, 10, 10, 13)); $g.FillRectangle($br, 0, 0, $w, $h)
  $b.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $b.Dispose()
}
Ground 'v_bg' 1080 1920
Ground 'h_bg' 1920 1080

# Rounded-corner masks for the phone screen (white = visible), sized as the edit places it.
function Mask($name, $w, $h, $r) {
  $b, $g = New-Canvas $w $h
  $g.Clear([System.Drawing.Color]::Black)
  $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $d = 2 * $r
  $p.AddArc(0, 0, $d, $d, 180, 90); $p.AddArc($w - $d - 1, 0, $d, $d, 270, 90)
  $p.AddArc($w - $d - 1, $h - $d - 1, $d, $d, 0, 90); $p.AddArc(0, $h - $d - 1, $d, $d, 90, 90); $p.CloseFigure()
  $g.FillPath([System.Drawing.Brushes]::White, $p)
  $b.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $b.Dispose()
}
Mask 'v_mask' 724 1460 52
Mask 'h_mask' 486 980 36

# A TV on the warm ground: a thin dark bezel around where the edit places the screen, and a stand.
function TvGround($name, $w, $h, $sx, $sy, $sw, $sh) {
  $b, $g = New-Canvas $w $h
  $path = New-Object System.Drawing.Drawing2D.GraphicsPath; $path.AddEllipse(-$w * .4, $h * .1, $w * 1.8, $h * 1.3)
  $br = New-Object System.Drawing.Drawing2D.PathGradientBrush($path)
  $br.CenterColor = [System.Drawing.Color]::FromArgb(255, 70, 26, 16); $br.SurroundColors = @([System.Drawing.Color]::FromArgb(255, 10, 10, 13))
  $g.Clear([System.Drawing.Color]::FromArgb(255, 10, 10, 13)); $g.FillRectangle($br, 0, 0, $w, $h)
  $bz = [int]($sw * .012) + 4
  $body = [System.Drawing.Color]::FromArgb(255, 22, 22, 26)
  $standW = [int]($sw * .22); $neck = [int]($sh * .07)
  $g.FillRectangle((New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 30, 30, 35))), [int]($sx + $sw / 2 - $standW / 2), ($sy + $sh + $bz + $neck), $standW, [int]($neck * .35))
  $g.FillRectangle((New-Object System.Drawing.SolidBrush $body), [int]($sx + $sw / 2 - 14), ($sy + $sh + $bz), 28, $neck)
  $g.FillRectangle((New-Object System.Drawing.SolidBrush $body), ($sx - $bz), ($sy - $bz), ($sw + 2 * $bz), ($sh + 2 * $bz))
  $g.DrawRectangle((New-Object System.Drawing.Pen ([System.Drawing.Color]::FromArgb(255, 60, 60, 68)), 2), ($sx - $bz), ($sy - $bz), ($sw + 2 * $bz), ($sh + 2 * $bz))
  $b.Save((Join-Path $dir "$name.png"), [System.Drawing.Imaging.ImageFormat]::Png); $g.Dispose(); $b.Dispose()
}
# Kept in step with edit-1.1.js's TV layout.
TvGround 'v_tvbg' 1080 1920 40 760 1000 562
TvGround 'h_tvbg' 1920 1080 200 50 1520 855
$logo.Dispose()
Get-ChildItem $dir | ForEach-Object { $_.Name }
