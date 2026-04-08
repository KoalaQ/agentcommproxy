@echo off
REM AgentCommProxy Windows 安装脚本
REM 使用方式: 以管理员身份运行 install.bat

echo ==========================================
echo   AgentCommProxy 快速安装
echo ==========================================
echo.

REM 检查管理员权限
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo 请以管理员身份运行此脚本
    echo 右键点击此文件，选择"以管理员身份运行"
    pause
    exit /b 1
)

REM 设置安装目录
set INSTALL_DIR=C:\Program Files\agentproxy
set BIN_NAME=agentproxy.bat

echo [1/4] 创建安装目录: %INSTALL_DIR%
if not exist "%INSTALL_DIR%" mkdir "%INSTALL_DIR%"

echo [2/4] 复制程序文件...
if exist "%~dp0agentcommproxy-1.0.0.jar" (
    copy "%~dp0agentcommproxy-1.0.0.jar" "%INSTALL_DIR%\" >nul
) else (
    echo 错误: 未找到 agentcommproxy-1.0.0.jar
    echo 请确保 jar 文件与此脚本在同一目录
    pause
    exit /b 1
)

echo [3/4] 创建系统命令...
echo @echo off > "%INSTALL_DIR%\%BIN_NAME%"
echo java -jar "%INSTALL_DIR%\agentcommproxy-1.0.0.jar" %%* >> "%INSTALL_DIR%\%BIN_NAME%"

echo [4/4] 注册系统命令...
REM 添加到系统 PATH
setx PATH "%PATH%;%INSTALL_DIR%" /M >nul 2>&1
if %errorLevel% neq 0 (
    REM 如果 setx 失败，手动添加
    reg add "HKLM\SYSTEM\CurrentControlSet\Control\Session Manager\Environment" /v Path /t REG_EXPAND_SZ /d "%PATH%;%INSTALL_DIR%" /f >nul
)

echo.
echo ==========================================
echo   安装完成!
echo ==========================================
echo.
echo 请重新打开命令提示符窗口以使用 agentproxy 命令
echo.
echo 验证安装:
echo   agentproxy --help
echo.
echo 使用示例:
echo   agentproxy agent --agent AgentB --message "hello" --sender AgentA
echo   agentproxy daemon start
echo   agentproxy status --request-id ^<id^>
echo.
echo 日志位置: %%USERPROFILE%%\.agentcommproxy\logs\
echo.
pause