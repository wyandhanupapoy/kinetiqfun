Add-Type -AssemblyName System.Drawing
$filepath = "$PWD\app\src\main\res\drawable\icongame.png"
$temppath = "$PWD\app\src\main\res\drawable\icongame_small.png"
$img = [System.Drawing.Image]::FromFile($filepath)
$bmp = new-object System.Drawing.Bitmap 512, 512
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.DrawImage($img, 0, 0, 512, 512)
$g.Dispose()
$img.Dispose()
$bmp.Save($temppath, [System.Drawing.Imaging.ImageFormat]::Png)
$bmp.Dispose()
Remove-Item $filepath
Rename-Item $temppath -NewName "icongame.png"
