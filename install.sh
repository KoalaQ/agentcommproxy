#!/bin/bash
# AgentCommProxy 安装脚本
# 使用方式: sudo ./install.sh

set -e

INSTALL_DIR="/opt/agentproxy"
BIN_NAME="agentproxy"

echo "=========================================="
echo "  AgentCommProxy 快速安装"
echo "=========================================="

# 检查 root 权限
if [ "$EUID" -ne 0 ]; then
    echo "请使用 sudo 运行此脚本"
    echo "  sudo ./install.sh"
    exit 1
fi

# 创建安装目录
echo "[1/5] 创建安装目录: $INSTALL_DIR"
mkdir -p "$INSTALL_DIR"

# 复制 JAR 文件
echo "[2/5] 复制程序文件..."
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/agentcommproxy-1.0.0.jar" ]; then
    cp "$SCRIPT_DIR/agentcommproxy-1.0.0.jar" "$INSTALL_DIR/"
else
    echo "错误: 未找到 agentcommproxy-1.0.0.jar"
    echo "请确保 jar 文件与此脚本在同一目录"
    exit 1
fi

# 创建启动脚本
echo "[3/5] 创建系统命令..."
cat > "$INSTALL_DIR/$BIN_NAME" << 'EOF'
#!/bin/bash
java -jar /opt/agentproxy/agentcommproxy-1.0.0.jar "$@"
EOF

chmod +x "$INSTALL_DIR/$BIN_NAME"

# 复制 daemon 控制脚本
if [ -f "$SCRIPT_DIR/daemon.sh" ]; then
    cp "$SCRIPT_DIR/daemon.sh" "$INSTALL_DIR/"
    chmod +x "$INSTALL_DIR/daemon.sh"
    echo "[4/5] 复制 daemon 控制脚本..."
else
    echo "[4/5] 跳过 daemon.sh (未找到)"
fi

# 创建软链接到系统命令
echo "[5/5] 注册系统命令..."
ln -sf "$INSTALL_DIR/$BIN_NAME" "/usr/local/bin/$BIN_NAME"

# 验证安装
echo ""
echo "=========================================="
echo "  安装完成!"
echo "=========================================="
echo ""
echo "验证安装:"
echo "  agentproxy --help"
echo ""
echo "启动 daemon (后台运行):"
echo "  $INSTALL_DIR/daemon.sh start"
echo "  $INSTALL_DIR/daemon.sh status"
echo "  $INSTALL_DIR/daemon.sh stop"
echo ""
echo "或前台运行:"
echo "  agentproxy daemon start --foreground"
echo ""
echo "日志位置: ~/.agentcommproxy/logs/"
echo ""