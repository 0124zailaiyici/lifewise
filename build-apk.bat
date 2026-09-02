@echo off
setlocal
cd /d %~dp0frontend

set JAVA_HOME=D:\jdk-home\jdk-21
set ANDROID_HOME=D:\Android\Sdk
set ANDROID_SDK_ROOT=D:\Android\Sdk
set PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\platform-tools;%PATH%
set JAVA_TOOL_OPTIONS=-Djava.net.preferIPv4Stack=true

echo [1/3] build web (local mode)...
call npm run build
if errorlevel 1 exit /b 1

echo [2/3] cap sync...
call npx cap sync android
if errorlevel 1 exit /b 1

echo [3/3] assembleDebug APK...
cd android
if not exist local.properties (
  > local.properties echo sdk.dir=D:/Android/Sdk
)
call gradlew.bat assembleDebug --no-daemon
if errorlevel 1 (
  echo Gradle startup failed, retrying once...
  timeout /t 2 /nobreak >nul
  call gradlew.bat assembleDebug --no-daemon
  if errorlevel 1 exit /b 1
)

cd ..\..
if not exist release mkdir release
copy /Y frontend\android\app\build\outputs\apk\debug\app-debug.apk release\LifeWise-local-debug.apk >nul
copy /Y frontend\android\app\build\outputs\apk\debug\app-debug.apk release\LifeWise-debug.apk >nul
copy /Y frontend\android\app\build\outputs\apk\debug\app-debug.apk release\LifeWise-1.4-local-debug.apk >nul
echo.
echo APK ready: %cd%\release\LifeWise-1.4-local-debug.apk
echo Mode: local-first (no LifeWise server). Configure AI URL + Key in app Profile page.
endlocal
