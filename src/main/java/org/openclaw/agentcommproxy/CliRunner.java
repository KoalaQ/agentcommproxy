package org.openclaw.agentcommproxy;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import org.openclaw.agentcommproxy.command.AgentCommand;
import org.openclaw.agentcommproxy.command.DaemonCommand;
import org.openclaw.agentcommproxy.command.ListCommand;
import org.openclaw.agentcommproxy.command.StatusCommand;

@Command(
    name = "agentproxy",
    version = "1.0.0",
    description = "Agent Communication Proxy CLI",
    subcommands = {
        AgentCommand.class,
        DaemonCommand.class,
        ListCommand.class,
        StatusCommand.class
    },
    mixinStandardHelpOptions = true
)
public class CliRunner implements Runnable {

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new CliRunner()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        spec.commandLine().usage(System.out);
    }
}