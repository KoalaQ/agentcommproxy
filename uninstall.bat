@echo off
REM AgentCommProxy Windows 卸载脚本
REM 使用方式: 以管理员身份运行 uninstall.bat

echo ==========================================
echo   AgentCommProxy 卸载
echo ==========================================
echo.

REM 检查管理员权限
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo 请以管理员身份运行此脚本
    pause
    exit /b 1
)

set INSTALL_DIR=C:\Program Files\agentproxy

echo [1/2] 移除系统 PATH...
REM 从 PATH 中移除（需要手动处理，这里简化处理）
reg delete "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v Path /f >nul 2>&1
echo 请手动检查系统 PATH 是否需要调整

echo [2/2] 删除安装目录...
if exist "%INSTALL_DIR%" rmdir /s /q "%INSTALL_DIR%"

echo.
set /p confirm="是否删除用户数据和日志? (y/N): "
if /i "%confirm%"=="y" (
    rmdir /s /q "%USERPROFILE%\.agentcommproxy" 2>nul
    echo 用户数据已删除
)

echo.
echo 卸载完成
echo.
pause