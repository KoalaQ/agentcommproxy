
# agentproxy

Send message to target agent via agentproxy CLI.

## Usage

```
/agentproxy <message>
```

## Parameters

- `message`: The message to send to the agent

## Optional Flags

- `--agent <name>`: Target agent name (default: from config or prompt)
- `--sender <name>`: Sender agent name (default: current agent name or "claude")
- `--sync`: Send in sync mode (wait for response)
- `--timeout <seconds>`: Timeout in seconds (default: 300)

## Examples

```bash
# Send async message
/agentproxy hello

# Send to specific agent
/agentproxy hello --agent AgentB

# Sync mode
/agentproxy "please check the status" --agent AgentB --sync

# With timeout
/agentproxy "long running task" --agent AgentB --timeout 600
```

## Configuration

Set default agent in `.claude/settings.json`:
```json
{
  "agentproxy": {
    "defaultAgent": "pijiang-test",
    "defaultSender": "claude",
    "defaultTimeout": 300
  }
}
```

## Implementation

When this skill is invoked:

1. Parse the message and optional flags from user input
2. Get default agent/sender from config if not specified
3. Build the command:
   ```bash
   agentproxy agent --agent <agent> --message "<message>" --sender <sender> [--sync] [--timeout N]
   ```
4. Execute the command and return the result

## Output

**Async mode:**
```
已收到、稍后回复您，消息唯一标识: <request-id>
```

**Sync mode:**
```
<agent response>
```