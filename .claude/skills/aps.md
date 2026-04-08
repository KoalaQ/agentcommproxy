# agentproxy-sync

Send message to target agent in sync mode (wait for response).

## Usage

```
/aps <message> [--agent <name>]
```

## Examples

```bash
# Quick sync message
/aps "what's your status?"

# To specific agent
/aps "hello" --agent AgentB
```

## Implementation

Same as `/agentproxy` but with `--sync` flag enabled by default.

Executes:
```bash
agentproxy agent --agent <agent> --message "<message>" --sender <sender> --sync
```