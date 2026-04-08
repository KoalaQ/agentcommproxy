#!/bin/bash
# AgentProxy Daemon 控制脚本
# 使用方式: ./daemon.sh [start|stop|status|restart]

DAEMON_CMD="agentproxy daemon start --foreground"
PID_DIR="$HOME/.agentcommproxy"
PID_FILE="$PID_DIR/daemon.pid"
LOG_DIR="$PID_DIR/logs"
LOG_FILE="$LOG_DIR/daemon.log"

# 确保目录存在
ensure_dirs() {
    mkdir -p "$PID_DIR"
    mkdir -p "$LOG_DIR"
}

start() {
    # 确保目录存在
    ensure_dirs

    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "Daemon is already running (PID: $PID)"
            return 1
        fi
    fi

    echo "Starting daemon..."
    nohup $DAEMON_CMD >> "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    sleep 1

    if ps -p $(cat "$PID_FILE") > /dev/null 2>&1; then
        echo "Daemon started (PID: $(cat $PID_FILE))"
        echo "Log: $LOG_FILE"
    else
        echo "Failed to start daemon"
        cat "$LOG_FILE"
        rm -f "$PID_FILE"
        return 1
    fi
}

stop() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "Stopping daemon (PID: $PID)..."
            kill $PID
            sleep 2
            if ps -p $PID > /dev/null 2>&1; then
                kill -9 $PID
            fi
            rm -f "$PID_FILE"
            echo "Daemon stopped"
        else
            echo "Daemon process not found, cleaning up..."
            rm -f "$PID_FILE"
        fi
    else
        echo "Daemon is not running"
        # 尝试通过进程名查找并杀掉
        pkill -f 'agentproxy daemon start' && echo "Killed daemon process by name"
    fi
}

status() {
    if [ -f "$PID_FILE" ]; then
        PID=$(cat "$PID_FILE")
        if ps -p $PID > /dev/null 2>&1; then
            echo "Daemon is running (PID: $PID)"
            echo "Log: $LOG_FILE"
            return 0
        fi
    fi

    # 检查是否有进程运行
    if pgrep -f 'agentproxy daemon start' > /dev/null; then
        echo "Daemon process found but PID file missing"
        pgrep -f 'agentproxy daemon start'
        return 0
    fi

    echo "Daemon is not running"
    return 1
}

restart() {
    stop
    sleep 1
    start
}

case "$1" in
    start)
        start
        ;;
    stop)
        stop
        ;;
    status)
        status
        ;;
    restart)
        restart
        ;;
    *)
        echo "Usage: $0 {start|stop|status|restart}"
        exit 1
        ;;
esac