@echo off
cd dist
echo Building installer...
del README.TXT
"C:\Program Files\7-Zip\7z.exe" a "C:\Users\Shwam\Copy cambird@f2s.com\EastAngliaSignalMapServer.exe" -mmt -mx5 -sfx7z.sfx *
echo Build complete