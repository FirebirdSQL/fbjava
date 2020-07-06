@echo off
setlocal

if "%1"=="" (
	echo "Syntax: %0 <target-dir>" 1>&2
	exit /b 1
)

set THIS_DIR=%~dp0

set BASE_DIR=%THIS_DIR%
for /f %%i in ("%1") do set TARGET_DIR=%%~fi

REM May be set by caller with values "Release" (default) or "Debug".
if not defined FBJAVA_CONFIG set FBJAVA_CONFIG=Release

if exist "%TARGET_DIR%\nul" (
	echo "Install directory should not exist" 1>&2
	exit /b 1
)

REM FIXME: Can we use mklink when FBJAVA_LINK=1 ?
set CP=copy
set CPR=xcopy /e

cd "%BASE_DIR%"

if exist "build\nul" (
	rd build /s
)

call run-maven.bat --batch-mode clean package dependency:copy-dependencies javadoc:javadoc
call gen-cmake.bat %FBJAVA_CONFIG%

msbuild -p:Configuration=%FBJAVA_CONFIG% build/fbjava.sln

mkdir ^
	"%TARGET_DIR%\bin" ^
	"%TARGET_DIR%\conf" ^
	"%TARGET_DIR%\jar" ^
	"%TARGET_DIR%\docs" ^
	"%TARGET_DIR%\lib" ^
	"%TARGET_DIR%\scripts" ^
	"%TARGET_DIR%\examples"

%CP% "%BASE_DIR%\src\etc\bin\setenv.bat" "%TARGET_DIR%\bin"
%CP% "%BASE_DIR%\src\etc\bin\fbjava-deployer.bat" "%TARGET_DIR%\bin"
%CP% "%BASE_DIR%\build\java\fbjava\*.jar" "%TARGET_DIR%\jar"
%CP% "%BASE_DIR%\build\java\fbjava-impl\*.jar" "%TARGET_DIR%\jar"
%CP% "%BASE_DIR%\build\java\fbjava-impl\dependency\*.jar" "%TARGET_DIR%\jar"
%CPR% "%BASE_DIR%\build\java\fbjava\site\apidocs" "%TARGET_DIR%\docs"
%CP% "%BASE_DIR%\src\etc\doc\fbjava.pdf" "%TARGET_DIR%\docs"

%CP% "%BASE_DIR%\build\src\native\fbjava\%FBJAVA_CONFIG%\libfbjava.dll" "%TARGET_DIR%\lib"

%CP% "%BASE_DIR%\src\fbjava-impl\src\main\resources\org\firebirdsql\fbjava\*.sql" "%TARGET_DIR%\scripts"
%CP% "%BASE_DIR%\src\etc\conf\fbjava.conf" "%TARGET_DIR%\conf"
%CP% "%BASE_DIR%\src\etc\conf\jvm.args" "%TARGET_DIR%\conf"
%CP% "%BASE_DIR%\src\etc\scripts\java-security.sql" "%TARGET_DIR%\scripts"
%CPR% "%BASE_DIR%\examples\*" "%TARGET_DIR%\examples"

if exist "%TARGET_DIR%\conf\java-security.fdb" (
	del "%TARGET_DIR%\conf\java-security.fdb"
)

echo create database '%TARGET_DIR%\conf\java-security.fdb' default character set utf8; | isql -q -user sysdba
isql "%TARGET_DIR%\conf\java-security.fdb" -q -user sysdba -i "%TARGET_DIR%\scripts\java-security.sql"

REM Transform file name to lower case.
ren "%TARGET_DIR%\conf\java-security.fdb" java-security.fdb
