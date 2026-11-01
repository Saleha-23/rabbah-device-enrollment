# Builds a debug-signed APK with the Android SDK tools only (no Gradle).
$ErrorActionPreference = 'Stop'
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
$bt  = "$sdk\build-tools\35.0.0"
$jar = "$sdk\platforms\android-34\android.jar"
$jbr = "C:\Program Files\Android\Android Studio\jbr\bin"
$env:JAVA_HOME = Split-Path $jbr
$env:PATH = "$jbr;$env:PATH"
Set-Location $PSScriptRoot
Remove-Item build -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory build\classes, build\res | Out-Null

& "$bt\aapt2.exe" compile --dir res -o build\res.zip
New-Item -ItemType Directory build\gen | Out-Null
& "$bt\aapt2.exe" link -o build\base.apk -I $jar --manifest AndroidManifest.xml --min-sdk-version 24 --target-sdk-version 34 --java build\gen build\res.zip
$src = Get-ChildItem src, build\gen -Recurse -Filter *.java | ForEach-Object FullName
& "$jbr\javac.exe" --release 8 -Xlint:-options -classpath $jar -d build\classes $src
& "$jbr\jar.exe" cf build\classes.jar -C build\classes .
& "$bt\d8.bat" --min-api 24 --lib $jar --output build build\classes.jar

Copy-Item build\base.apk build\unsigned.apk
Push-Location build; & "$jbr\jar.exe" uf unsigned.apk classes.dex; Pop-Location
& "$bt\zipalign.exe" -f 4 build\unsigned.apk build\aligned.apk

$ks = "$env:USERPROFILE\.android\debug.keystore"
if (-not (Test-Path $ks)) {
  New-Item -ItemType Directory (Split-Path $ks) -Force | Out-Null
  & "$jbr\keytool.exe" -genkeypair -keystore $ks -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass pass:android --key-pass pass:android --out rabbah-enroll.apk build\aligned.apk
Write-Host "Built $PSScriptRoot\rabbah-enroll.apk"
