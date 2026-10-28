param(
    [string] $JavaHome = $env:JAVA_HOME,
    [string] $Destination = (Join-Path $PSScriptRoot '..\DoTPet-Windows-v0.4.2'),
    [string] $AppVersion = '0.4.2'
)

$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$taskRoot = [System.IO.Path]::GetFullPath((Join-Path $projectRoot '..\..'))
if ([string]::IsNullOrWhiteSpace($JavaHome) -or !(Test-Path (Join-Path $JavaHome 'bin\jpackage.exe'))) {
    $JavaHome = Join-Path $taskRoot 'work\temurin21\jdk-21.0.12.1+1'
}
foreach ($tool in @('javac', 'jar', 'jlink', 'jpackage')) {
    if (!(Test-Path (Join-Path $JavaHome "bin\$tool.exe"))) { throw "JDK 21 tool not found: $tool" }
}
$destinationPath = [System.IO.Path]::GetFullPath($Destination)
if (Test-Path -LiteralPath $destinationPath) {
    throw "Destination already exists. Choose a new -Destination to preserve the previous package: $destinationPath"
}

$packageWork = Join-Path $taskRoot ('work\windows-package-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$classes = Join-Path $packageWork 'classes'
$inputDirectory = Join-Path $packageWork 'input'
$runtime = Join-Path $packageWork 'runtime'
$imageOutput = Join-Path $packageWork 'image'
New-Item -ItemType Directory -Path $classes, $inputDirectory, $imageOutput -Force | Out-Null

$cacheRoot = Join-Path $env:USERPROFILE '.gradle\caches\modules-2\files-2.1\org.openjfx'
$fxJars = foreach ($module in @('base', 'graphics', 'controls')) {
    $fileName = "javafx-$module-21.0.13-win.jar"
    $artifactDirectory = Join-Path $cacheRoot "javafx-$module\21.0.13"
    $jar = Get-ChildItem -LiteralPath $artifactDirectory -Recurse -Filter $fileName | Select-Object -First 1
    if ($null -eq $jar) { throw "Build with Gradle once to resolve JavaFX: $fileName" }
    Copy-Item -LiteralPath $jar.FullName -Destination $inputDirectory
    $jar.FullName
}
$modulePath = $fxJars -join ';'
$nativeJars = foreach ($artifact in @('jna', 'jna-platform')) {
    $fileName = "$artifact-5.19.1.jar"
    $jarPath = Join-Path $projectRoot "libs\$fileName"
    if (!(Test-Path -LiteralPath $jarPath)) {
        $artifactDirectory = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\net.java.dev.jna\$artifact\5.19.1"
        $jar = Get-ChildItem -LiteralPath $artifactDirectory -Recurse -Filter $fileName | Select-Object -First 1
        if ($null -eq $jar) { throw "Build with Gradle once to resolve JNA: $fileName" }
        $jarPath = $jar.FullName
    }
    Copy-Item -LiteralPath $jarPath -Destination $inputDirectory
    $jarPath
}
$sources = Get-ChildItem (Join-Path $projectRoot 'src\main\java') -Recurse -Filter '*.java' | ForEach-Object FullName
& (Join-Path $JavaHome 'bin\javac.exe') --release 21 --module-path $modulePath --add-modules javafx.controls -cp ($nativeJars -join ';') -encoding UTF-8 -d $classes $sources
if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
Copy-Item -Path (Join-Path $projectRoot 'src\main\resources\*') -Destination $classes -Recurse -Force
& (Join-Path $JavaHome 'bin\jar.exe') ("-J-Djava.io.tmpdir=" + $inputDirectory) --create --file (Join-Path $inputDirectory 'dotpet.jar') --main-class com.dotpet.Main -C $classes .
if ($LASTEXITCODE -ne 0) { throw 'Application JAR creation failed.' }

# The app image owns its runtime; the user's PATH and JAVA_HOME are irrelevant at launch.
& (Join-Path $JavaHome 'bin\jlink.exe') ("-J-Djava.io.tmpdir=" + $inputDirectory) --module-path (Join-Path $JavaHome 'jmods') `
    --add-modules java.base,java.desktop,java.logging,java.prefs,java.xml,jdk.unsupported,jdk.charsets `
    --output $runtime --strip-debug --no-header-files --no-man-pages --compress=2
if ($LASTEXITCODE -ne 0) { throw 'Bundled Java runtime creation failed.' }

$localFxPath = '$APPDIR\javafx-base-21.0.13-win.jar;$APPDIR\javafx-graphics-21.0.13-win.jar;$APPDIR\javafx-controls-21.0.13-win.jar'
& (Join-Path $JavaHome 'bin\jpackage.exe') --type app-image --name DoTPet --app-version $AppVersion `
    --description 'DoT Pet desktop companion' --vendor 'DoT Pet' `
    --input $inputDirectory --main-jar dotpet.jar --main-class com.dotpet.Main `
    --runtime-image $runtime --dest $imageOutput --temp (Join-Path $packageWork 'jpackage-temp') `
    --java-options ('--module-path=' + $localFxPath) --java-options '--add-modules=javafx.controls' `
    --java-options '-Dfile.encoding=UTF-8'
if ($LASTEXITCODE -ne 0) { throw 'Windows app image creation failed.' }

Copy-Item -LiteralPath (Join-Path $imageOutput 'DoTPet') -Destination $destinationPath -Recurse
$instructions = @'
DoT Pet 실행 방법

1. 이 폴더 안의 DoTPet.exe를 더블클릭하세요.
2. Java/JDK 설치나 JAVA_HOME 설정은 필요하지 않습니다.
3. 다른 곳으로 옮길 때는 이 폴더 전체를 옮기세요.
   DoTPet.exe, app, runtime 폴더는 함께 있어야 합니다.

펫은 작업표시줄 바로 위에 나타납니다.
기본 캐릭터는 파란 슬라임입니다. 대기·걷기·달리기·잠자기 애니메이션이 포함되어 있습니다.
다른 캐릭터를 쓰고 있다면 꾸미기 → 기본 슬라임 → 펫에 적용 후 닫기를 누르세요.
좌클릭: 빠른 작업 / 키 설정 / 꾸미기 / 설정
우클릭: 멈추기 / 공중 고정 / 종료
마우스 드래그: 펫 이동 및 던지기
화면 높이의 절반 정도 이상에서 떨어지면 넘어졌다 일어납니다.
일어날 때 가끔 머리 위에 붉은 화남 표시가 나타납니다.

전역 단축키/휠 드래그: 좌클릭 → 키 설정에서 직접 켜고 저장하세요.
오토 클릭 긴급 중지: 기본 F8 (키 설정에서 변경 가능)
붙여넣기 / 붙여넣고 Enter: 키 설정에서 원하는 단축키나 휠 제스처를 연결하세요.
프로그램 설정에서 걷기·중력·수다·항상 위 표시·크기·위치 기억을 바꿀 수 있습니다.
적용한 캐릭터와 애니메이션, 마지막 위치는 다음 실행에도 복원됩니다.
픽셀 편집기: 동작·프레임 선택 → 이미지 가져오기로 한 장씩 넣으세요.
이미지는 선택한 프레임만 바꿉니다. 전체에 쓰려면 모든 프레임에 복사를 누르세요.
전역 입력을 끈 상태에서도 직접 시작한 오토 클릭 동안에는 긴급 중지 키가 작동합니다.

무한 클릭: 좌클릭 → 빠른 작업 → 무한 반복 체크 → 오토 클릭 시작
기본은 지정 횟수 반복이며, 무한 반복도 직접 시작해야 실행됩니다.
중지 버튼 / 긴급 중지 키 / 빠른 작업 창 닫기 / 앱 종료로 멈출 수 있습니다.

펫은 가끔 크림색 말풍선으로 농담이나 잡지식을 말합니다.
말풍선은 약 7초 뒤 사라지며, 눌러서 바로 닫을 수도 있습니다.
메뉴·편집·드래그·자동화 중에는 수다를 숨깁니다.

사용자 캐릭터 데이터: %APPDATA%\DoTPet\pets
입력/문구/오토 클릭 설정: %APPDATA%\DoTPet\input.properties
'@
[System.IO.File]::WriteAllText((Join-Path $destinationPath '실행 방법.txt'), $instructions, [System.Text.UTF8Encoding]::new($true))
Write-Output "Portable application ready: $destinationPath\DoTPet.exe"
