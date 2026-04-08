package org.openclaw.agentcommproxy.proxy;

/**
 * 命令执行结果
 */
public class CommandResult {
    private boolean success;
    private String output;
    private String error;
    private int exitCode;

    public CommandResult(boolean success, String output, String error, int exitCode) {
        this.success = success;
        this.output = output;
        this.error = error;
        this.exitCode = exitCode;
    }

    public static CommandResult success(String output) {
        return new CommandResult(true, output, null, 0);
    }

    public static CommandResult failure(String error, int exitCode) {
        return new CommandResult(false, null, error, exitCode);
    }

    public static CommandResult timeout() {
        return new CommandResult(false, null, "Timeout", -1);
    }

    // Getters
    public boolean isSuccess() { return success; }
    public String getOutput() { return output; }
    public String getError() { return error; }
    public int getExitCode() { return exitCode; }
    public boolean isTimeout() { return exitCode == -1; }
}