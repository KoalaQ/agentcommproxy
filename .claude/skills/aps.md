# agentproxy-sync

Send message to target agent in sync mode (wait for response).

## Usage

```
/aps --agent <target-agent> --sender <my-agent> <message> [--timeout N]
```

## Required Parameters

- `--agent <name>`: Target agent name (required)
- `--sender <name>`: Your agent name (required)

## Optional Flags

- `--timeout <seconds>`: Timeout in seconds (default: 300)

## Examples

```bash
# Quick sync message
/aps --agent AgentB --sender AgentA "what's your status?"

# With timeout
/aps --agent AgentB --sender AgentA "long task" --timeout 600
```

## Implementation

Same as `/agentproxy` but with `--sync` flag enabled by default.

Executes:
```bash
agentproxy agent --agent <target-agent> --sender <my-agent> --message "<message>" --sync [--timeout N]
```