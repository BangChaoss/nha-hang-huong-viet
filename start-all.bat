@echo off
setlocal
set "ROOT=%~dp0"
set "BACKEND_DIR=%ROOT%backend"
set "FRONTEND_DIR=%ROOT%frontend"
set "MAVEN_CMD=%LOCALAPPDATA%\Programs\Apache\Maven\apache-maven-3.9.11\bin\mvn.cmd"

echo.
echo ============================================
echo   Khoi dong Huong Viet
echo ============================================
echo   Backend : http://localhost:8080
echo   Frontend: http://localhost:5173
echo.

if not exist "%BACKEND_DIR%\pom.xml" (
  echo [LOI] Khong tim thay backend\pom.xml.
  exit /b 1
)
if not exist "%FRONTEND_DIR%\node_modules\vite\bin\vite.js" (
  echo [LOI] Chua co dependency frontend.
  echo Hay chay: cd frontend ^&^& npm install --legacy-peer-deps
  exit /b 1
)

if not exist "%MAVEN_CMD%" (
  where mvn.cmd >nul 2>nul
  if errorlevel 1 (
    echo [LOI] Khong tim thay Maven. Hay cai Maven hoac cap nhat MAVEN_CMD trong file nay.
    exit /b 1
  )
  set "MAVEN_CMD=mvn.cmd"
)
where npx.cmd >nul 2>nul
if errorlevel 1 (
  echo [LOI] Khong tim thay npx. Hay cai Node.js.
  exit /b 1
)

call :port_listening 3306
if errorlevel 1 echo [CANH BAO] MySQL chua lang nghe cong 3306; backend co the khong ket noi duoc.

call :port_listening 8080
if errorlevel 1 (
  echo Dang mo backend...
  start "Huong Viet - Backend" /D "%BACKEND_DIR%" cmd /k ""%MAVEN_CMD%" spring-boot:run"
) else (
  echo Backend da chay tren cong 8080; bo qua.
)

call :port_listening 5173
if errorlevel 1 (
  echo Dang mo frontend...
  start "Huong Viet - Frontend" /D "%FRONTEND_DIR%" cmd /k "npx --yes node@22.12.0 .\node_modules\vite\bin\vite.js dev --host localhost --port 5173"
) else (
  echo Frontend da chay tren cong 5173; bo qua.
)

echo.
echo Trang web: http://localhost:5173
start "" "http://localhost:5173/"
exit /b 0

:port_listening
powershell -NoProfile -Command "if (Get-NetTCPConnection -State Listen -LocalPort %~1 -ErrorAction SilentlyContinue) { exit 0 } else { exit 1 }" >nul 2>nul
exit /b %errorlevel%
