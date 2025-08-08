@echo off
echo ===================================
echo Gemstone Crab Plugin Runner
echo ===================================

echo Building the plugin with all dependencies...
call gradlew clean shadowJar

if %ERRORLEVEL% NEQ 0 (
    echo Build failed! Please check the error messages above.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo Build successful! Starting RuneLite with the plugin...
echo.

:: Find the generated JAR file
for /f "tokens=*" %%a in ('dir /b /s "build\libs\*.jar"') do set JAR_FILE=%%a

if not defined JAR_FILE (
    echo Error: Could not find the generated JAR file.
    pause
    exit /b 1
)

echo Using JAR: %JAR_FILE%
echo.

:: Run the JAR file with assertions enabled, developer mode, and debug flags
java -ea -cp "%JAR_FILE%" com.gimserenity.launcher.GemstoneCrabPluginDebug --developer-mode --debug

if %ERRORLEVEL% NEQ 0 (
    echo Failed to start RuneLite! Please check the error messages above.
    pause
    exit /b %ERRORLEVEL%
)

pause
