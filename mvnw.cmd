@echo off
setlocal enabledelayedexpansion

set "MVNW_PROJECTBASEDIR=%~dp0"
set "MVNW_USERHOME=%USERPROFILE%\.m2\wrapper"
set "MAVEN_HOME=%MVNW_USERHOME%\dists\apache-maven-3.9.9"
set "MAVEN_OPTS=-Xmx1024m -XX:MaxMetaspaceSize=256m"

:: Find Java
set "JAVACMD=java"
if defined JAVA_HOME set "JAVACMD=%JAVA_HOME%\bin\java.exe"

"%JAVACMD%" -version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Java not found. Please install JDK 17+ or set JAVA_HOME.
    exit /b 1
)

:: Download & install Maven if not present
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
    echo Downloading Apache Maven 3.9.9...
    mkdir "%MVNW_USERHOME%\dists" 2>nul

    powershell -Command ^
        "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
        $progressPreference='silentlyContinue'; ^
        Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile '%MVNW_USERHOME%\dists\maven-3.9.9-bin.zip'"

    if errorlevel 1 (
        echo ERROR: Failed to download Maven.
        exit /b 1
    )

    echo Extracting Maven...
    powershell -Command "Expand-Archive -Path '%MVNW_USERHOME%\dists\maven-3.9.9-bin.zip' -DestinationPath '%MVNW_USERHOME%\dists' -Force"
    del "%MVNW_USERHOME%\dists\maven-3.9.9-bin.zip" 2>nul
    echo Maven installed successfully.
)

:: Run Maven
set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
set "MAVEN_CMD_LINE_ARGS=%*"
set "MAVEN_PROJECTBASEDIR=%MVNW_PROJECTBASEDIR%"

call "%MVN_CMD%" %MAVEN_CMD_LINE_ARGS%
exit /b %errorlevel%
