@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
cd /d "%~dp0"

rem ============================================================
rem  Khoi dong toan bo du an Nha Hang Huong Viet
rem  - Kiem tra Java 22, Node.js, MySQL
rem  - Tu tao database neu chua co
rem  - Mo cua so backend va frontend
rem ============================================================

echo.
echo ============================================
echo   NHA HANG HUONG VIET - Khoi dong du an
echo ============================================
echo.

rem ------------------------------------------------------------
rem  1. Java 22
rem ------------------------------------------------------------
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
)

set "JAVA_RAW="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_RAW=%%~v"

if not defined JAVA_RAW goto no_java

set "JAVA_MAJOR=0"
set "JAVA_MINOR=0"
for /f "tokens=1,2 delims=." %%a in ("%JAVA_RAW%") do (
  set "JAVA_MAJOR=%%a"
  set "JAVA_MINOR=%%b"
)

set "JAVA_OK="
if "%JAVA_MAJOR%"=="1" (
  if %JAVA_MINOR% GEQ 8 set "JAVA_OK=1"
) else (
  if %JAVA_MAJOR% GEQ 22 set "JAVA_OK=1"
)
if not defined JAVA_OK goto no_java

echo [1/4] Java %JAVA_RAW% - OK.
goto step_node

:no_java
echo.
echo [LOI] Du an can Java 22. May dang co Java %JAVA_RAW%.
echo.
echo   Cai Java 22 - Temurin - tai:
echo     https://adoptium.net/temurin/releases/?version=22
echo.
echo   Sau khi cai, dat JAVA_HOME tro toi thu muc JDK 22
echo   va them %JAVA_HOME%\bin vao PATH, roi chay lai file nay.
echo.
pause
exit /b 1

rem ------------------------------------------------------------
rem  2. Node.js
rem ------------------------------------------------------------
:step_node
where node >nul 2>nul
if errorlevel 1 goto no_node
for /f "tokens=*" %%v in ('node -v') do set "NODE_VER=%%v"
echo [2/4] Node.js !NODE_VER! - OK.
goto step_mysql

:no_node
echo.
echo [LOI] Khong tim thay Node.js.
echo.
echo   Cai Node.js 20 tro len tai: https://nodejs.org/
echo   Sau khi cai, mo lai terminal roi chay lai file nay.
echo.
pause
exit /b 1

rem ------------------------------------------------------------
rem  3. MySQL
rem ------------------------------------------------------------
:step_mysql
rem --- Doc cau hinh MySQL truc tiep tu application.properties ---
rem File nay la nguon suy nhat: sua o do thi ca ung dung lan script deu doi theo.
set "PROPS=%~dp0backend\src\main\resources\application.properties"
if not exist "%PROPS%" goto no_props

rem Gia tri trong file chi la fallback - bien moi truong da dat thi giu nguyen.
for %%K in (host port name username password) do (
  for /f "tokens=1,* delims==" %%a in ('findstr /b /c:"app.db.%%K=" "%PROPS%"') do (
    if not defined DB_%%K set "DB_%%K=%%b"
  )
)

if not defined DB_HOST set "DB_HOST=127.0.0.1"
if not defined DB_PORT set "DB_PORT=3306"
if not defined DB_NAME set "DB_NAME=huong_viet_restaurant"
if not defined DB_USERNAME set "DB_USERNAME=huongviet"
if not defined DB_PASSWORD set "DB_PASSWORD=huongviet123"
set "MYSQL_ARGS=-h %DB_HOST% -P %DB_PORT%"

rem KHONG set bien DB_URL o day.
rem URL JDBC co dau "&" trong chuoi se bi cmd cat khi truyen sang JVM con,
rem khien backend khong tim thay driver MySQL.
rem Ung dung tu doc app.db.* trong application.properties de ghep URL.

set "MYSQL="
where mysql >nul 2>nul && set "MYSQL=mysql"
if not defined MYSQL (
  if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
)
if not defined MYSQL (
  if exist "C:\Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe" set "MYSQL=C:\Program Files\MySQL\MySQL Server 8.1\bin\mysql.exe"
)
if not defined MYSQL (
  if exist "C:\xampp\mysql\bin\mysql.exe" set "MYSQL=C:\xampp\mysql\bin\mysql.exe"
)
if not defined MYSQL goto no_mysql

rem --- Database da co san chua ---
"%MYSQL%" %MYSQL_ARGS% -u %DB_USERNAME% -p%DB_PASSWORD% -e "USE %DB_NAME%; SELECT 1;" >nul 2>&1
if not errorlevel 1 goto db_ready

echo [3/4] Chua co database '%DB_NAME%'. Nhap mat khau root MySQL de tao.
set /p "MYSQL_ROOT_PW=      Mat khau root MySQL: "
echo.

"%MYSQL%" %MYSQL_ARGS% -u root -p%MYSQL_ROOT_PW% -e "CREATE DATABASE IF NOT EXISTS %DB_NAME% CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; CREATE USER IF NOT EXISTS '%DB_USERNAME%'@'localhost' IDENTIFIED BY '%DB_PASSWORD%'; CREATE USER IF NOT EXISTS '%DB_USERNAME%'@'127.0.0.1' IDENTIFIED BY '%DB_PASSWORD%'; CREATE USER IF NOT EXISTS '%DB_USERNAME%'@'::1' IDENTIFIED BY '%DB_PASSWORD%'; CREATE USER IF NOT EXISTS '%DB_USERNAME%'@'%%' IDENTIFIED BY '%DB_PASSWORD%'; GRANT ALL PRIVILEGES ON %DB_NAME%.* TO '%DB_USERNAME%'@'localhost'; GRANT ALL PRIVILEGES ON %DB_NAME%.* TO '%DB_USERNAME%'@'127.0.0.1'; GRANT ALL PRIVILEGES ON %DB_NAME%.* TO '%DB_USERNAME%'@'::1'; GRANT ALL PRIVILEGES ON %DB_NAME%.* TO '%DB_USERNAME%'@'%%'; FLUSH PRIVILEGES;" >nul 2>&1
if errorlevel 1 goto db_fail

"%MYSQL%" %MYSQL_ARGS% -u root -p%MYSQL_ROOT_PW% --default-character-set=utf8mb4 < "database\schema.sql" >nul 2>&1
if errorlevel 1 goto db_fail
echo     Da nap cau truc bang.

"%MYSQL%" %MYSQL_ARGS% -u root -p%MYSQL_ROOT_PW% --default-character-set=utf8mb4 < "database\seed.sql" >nul 2>&1
if errorlevel 1 (
  echo     Bo qua du lieu mau - ung dung se tu tao khi khoi dong lan dau.
) else (
  echo     Da nap du lieu mau.
)
goto db_ready

:db_fail
echo.
echo [LOI] Khong tao duoc database. Kiem tra lai mat khau root MySQL.
echo.
pause
exit /b 1

:db_ready
echo [3/4] MySQL %DB_HOST%:%DB_PORT% - OK.
goto step_frontend

:no_mysql
echo.
echo [LOI] Khong tim thay lenh mysql.
echo.
echo   Cai MySQL 8.0 tai: https://dev.mysql.com/downloads/installer/
echo   Sau khi cai xong, chay lai file nay.
echo.
pause
exit /b 1

:no_props
echo.
echo [LOI] Khong tim thay file cau hinh:
echo   %PROPS%
echo.
pause
exit /b 1

rem ------------------------------------------------------------
rem  4. Cai dependency frontend roi mo ca hai cua so
rem ------------------------------------------------------------
:step_frontend
if not exist "frontend\node_modules" (
  echo [4/4] Cai dependency frontend. Lan dau se mat mot chut thoi gian...
  echo.
  pushd "frontend"
  call npm install
  set "NPM_FAILED=!ERRORLEVEL!"
  popd
  if not "!NPM_FAILED!"=="0" goto npm_fail
) else (
  echo [4/4] Dependency frontend da co san.
)
echo.

echo ============================================
echo   Dang mo cua so backend va frontend...
echo ============================================
echo.

rem Dung duong dan tuyet doi - Windows co the khong tim lenh trong thu muc hien tai.
call :port_listening 8080
if errorlevel 1 (
  echo   Backend : dang mo cua so moi...
  start "Huong Viet - Backend" /D "%~dp0backend" cmd /k "%~dp0backend\mvnw.cmd" spring-boot:run
) else (
  echo   Backend : da chay tren cong 8080, bo qua.
)

rem Vite mac dinh chay port 8080 - trung voi backend nen ep sang 5173.
call :port_listening 5173
if errorlevel 1 (
  echo   Frontend: dang mo cua so moi...
  start "Huong Viet - Frontend" /D "%~dp0frontend" cmd /k npm run dev -- --port 5173 --strictPort
) else (
  echo   Frontend: da chay tren cong 5173, bo qua.
)

echo.
echo ============================================
echo   Backend  se len sau khoang 15-30 giay:
echo     http://localhost:8080
echo.
echo   Frontend - mo trinh duyet vao:
echo     http://localhost:5173
echo.
echo   Tai khoan demo:
echo     quanly    / QuanLy@12345     Quan ly
echo     nhanvien  / NhanVien@12345   Nhan vien phuc vu
echo     dau_bep   / DauBep@12345     Dau bep
echo     khachhang / KhachHang@12345  Khach hang
echo     admin     / Admin@123456     Quan tri
echo ============================================
echo.
echo   Dong cua so backend se dung backend. Ctrl+C trong cua so do la dung ca hai.
echo.
start "" "http://localhost:5173/"
pause
endlocal
exit /b 0

rem Kiem tra cong co dang lang nghe hay khong.
:port_listening
powershell -NoProfile -Command "if (Get-NetTCPConnection -State Listen -LocalPort %~1 -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>nul
exit /b %errorlevel%

:npm_fail
echo.
echo [LOI] npm install that bai. Kiem tra ket noi mang roi chay lai.
echo.
pause
exit /b 1
