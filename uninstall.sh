#!/bin/bash
# AgentCommProxy 卸载脚本
# 使用方式: sudo ./uninstall.sh

set -e

INSTALL_DIR="/opt/agentproxy"
BIN_NAME="agentproxy"

echo "=========================================="
echo "  AgentCommProxy 卸载"
echo "=========================================="

# 检查 root 权限
if [ "$EUID" -ne 0 ]; then
    echo "请使用 sudo 运行此脚本"
    exit 1
fi

# 删除系统命令链接
echo "[1/2] 移除系统命令..."
rm -f "/usr/local/bin/$BIN_NAME"

# 删除安装目录
echo "[2/2] 删除安装目录..."
rm -rf "$INSTALL_DIR"

# 删除用户数据（可选）
read -p "是否删除用户数据和日志? (y/N): " confirm
if [ "$confirm" = "y" ] || [ "$confirm" = "Y" ]; then
    rm -rf "$HOME/.agentcommproxy"
    echo "用户数据已删除"
fi

echo ""
echo "卸载完成"
echo ""