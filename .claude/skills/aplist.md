# agentproxy-list

List message records from database.

## Usage

```
/aplist [--limit N] [--status <status>] [--full]
```

## Flags

- `--limit N`: Limit number of records (default: 10)
- `--status <status>`: Filter by status (PENDING/EXECUTING/EXECUTE_SUCCESS/DONE/ERROR etc.)
- `--full`: Show full message content

## Examples

```bash
# List recent messages
/aplist

# List 20 messages
/aplist --limit 20

# List only errors
/aplist --status ERROR

# Show full content
/aplist --full
```

## Implementation

Executes:
```bash
agentproxy list [--limit N] [--status <status>] [--full]
```