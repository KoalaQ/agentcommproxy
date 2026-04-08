# agentproxy-status

Check message status by request ID.

## Usage

```
/apstatus <request-id>
```

## Examples

```bash
/apstatus f6377a41-35b7-45ac-b098-2d32ce1fccef
```

## Implementation

Executes:
```bash
agentproxy status --request-id <request-id>
```

## Output

```
Request ID: f6377a41-35b7-45ac-b098-2d32ce1fccef
Status: DONE
Sender: AgentA
Target Agent: AgentB
Message: hello
Execute Retry: 0
Callback Retry: 0
Response: 在线。🧪
```