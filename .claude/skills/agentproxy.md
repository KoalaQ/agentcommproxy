# agentproxy

Send message to target agent via agentproxy CLI.

## Usage

```
/agentproxy --agent <target-agent> --sender <my-agent> <message> [--sync] [--timeout N]
```

## Required Parameters

- `--agent <name>`: Target agent name (required)
- `--sender <name>`: Your agent name (required)

## Optional Flags

- `--sync`: Send in sync mode (wait for response)
- `--timeout <seconds>`: Timeout in seconds (default: 300)

## Examples

```bash
# Async message
/agentproxy --agent AgentB --sender AgentA "hello"

# Sync message (wait for response)
/agentproxy --agent AgentB --sender AgentA "check status" --sync

# With timeout
/agentproxy --agent AgentB --sender AgentA "long task" --timeout 600 --sync
```

## Implementation

When this skill is invoked:

1. Parse required parameters: `--agent` and `--sender`
2. Parse message content
3. Parse optional flags: `--sync`, `--timeout`
4. Build and execute command:
   ```bash
   agentproxy agent --agent <target-agent> --sender <my-agent> --message "<message>" [--sync] [--timeout N]
   ```
5. Return the result to user

## Output

**Async mode:**
```
已收到、稍后回复您，消息唯一标识: <request-id>
```

**Sync mode:**
```
Request ID: <request-id>
Sender: <my-agent>
Target Agent: <target-agent>
Message: <message>
Response: <response>
```

## Error Handling

- If `--agent` not specified: Prompt user "Please specify target agent with --agent <name>"
- If `--sender` not specified: Prompt user "Please specify your agent name with --sender <name>"
- If command fails: Show error message