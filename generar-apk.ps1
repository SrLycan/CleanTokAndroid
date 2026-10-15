<#
  Genera el APK para compartir con el equipo, ya apuntando al servidor de AWS.

    .\generar-apk.ps1 -Servidor https://3-85-10-20.sslip.io

  Deja dist\CleanTok-<versión>.apk. La primera vez crea cleantok-equipo.jks (la llave de firma):
  guárdala, porque con la misma llave tus compañeros pueden actualizar sin desinstalar.
#>
param([Parameter(Mandatory = $true)] [string] $Servidor)
# Las herramientas nativas escriben avisos en stderr: se revisa $LASTEXITCODE en vez de detenerse con ellos.
$ErrorActionPreference = "Continue"
Set-Location $PSScriptRoot

if (-not $env:JAVA_HOME) { $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr" }
$keytool = Join-Path $env:JAVA_HOME "bin\keytool.exe"

if (-not (Test-Path "keystore.properties")) {
    $clave = -join ((48..57) + (65..90) + (97..122) | Get-Random -Count 24 | ForEach-Object { [char]$_ })
    & $keytool -genkeypair -keystore cleantok-equipo.jks -alias cleantok -keyalg RSA -keysize 2048 -validity 10000 `
        -storepass $clave -keypass $clave -dname "CN=CleanTok, O=CleanTok, C=EC" 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "keytool falló" }
    "storeFile=cleantok-equipo.jks`nstorePassword=$clave`nkeyAlias=cleantok`nkeyPassword=$clave" | Out-File -Encoding ascii keystore.properties
    Write-Host "Llave de firma creada: cleantok-equipo.jks (respáldala junto con keystore.properties)"
}

.\gradlew.bat assembleRelease "-Pcleantok.servidor=$($Servidor.TrimEnd('/'))"
if ($LASTEXITCODE -ne 0) { throw "La compilación falló" }

$version = (Select-String -Path app\build.gradle.kts -Pattern 'versionName = "(.+)"').Matches[0].Groups[1].Value
New-Item -ItemType Directory -Force dist | Out-Null
$apk = "dist\CleanTok-$version.apk"
Copy-Item app\build\outputs\apk\release\app-release.apk $apk -Force
Write-Host "`nAPK listo: $(Resolve-Path $apk)  (servidor por defecto: $Servidor)"
