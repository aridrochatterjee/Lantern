# Lantern v1.1

Lantern is a local network observatory for homelabs and networks you own or are authorized to administer. **v1.1 adds Admin Mode**: an explicitly paired Lantern Agent can expose deeper host telemetry and optional, permission-scoped administration capabilities.



# Tech Stack
- Java 26
- Java HTTP Server
- HTML / CSS / JavaScript
- Linux networking tools
- TCP / ICMP / ARP
- JSON APIs
# Key Features
- LAN device discovery
- IP, MAC & hostname detection
- Port & service scanning
- Latency monitoring
- Live network dashboard
- Device history & events
- Lantern Agent for system telemetry
- Authenticated Agent pairing
- CPU, RAM, disk, uptime & process telemetry
- Optional Admin capabilities for explicitly authorized machines

## v1.1 highlights

- Black + lime command-center web UI, preserving the supplied Lantern observatory frontend.
- LAN discovery, device inventory, services, banners, history and network events.
- Cross-platform Lantern Core: Linux, Windows and macOS.
- Cross-platform Lantern Agent: Linux, Windows and macOS.
- Secure bearer-token pairing for Agent access.
- Agent binds to the LAN so a Lantern Core on another machine can connect; every Agent endpoint requires the token.
- Deep read-only telemetry: OS, kernel, architecture, hardware, CPU, GPU, RAM, disk, uptime, battery, user, interfaces and processes.
- **Admin Mode is opt-in and permission-scoped.**
- Optional filesystem access is restricted to explicitly configured allowed roots.
- Optional screen snapshots use the operating system display APIs.
- Optional clipboard text access.
- Optional Chromium/Chrome/Edge history metadata through a local browser-history database copy when `sqlite3` is available.
- Dashboard exposes the enabled Admin Mode capabilities for each paired device and provides same-origin controls for enabled modules.
- No credential, password, private-key, keystroke or private-message collection.
- No arbitrary remote shell or command execution.
- No third-party Java dependencies.

## Architecture

```text
                 Lantern Core
                      |
        +-------------+-------------+
        |                           |
   Network discovery          Paired Agents
        |                           |
 IP/MAC/DNS/services       System + Admin Mode
        |                           |
        +-------------+-------------+
                      |
                Device profile
                      |
               Black/Lime UI
```

## Build Lantern

```bash
rm -rf out
mkdir out
javac -d out $(find src/main/java -name '*.java')
```

Run:

```bash
./run-lantern.sh
```

Windows PowerShell:

```powershell
./run-lantern.ps1
```

Windows CMD:

```bat
run-lantern.bat
```

Useful options:

```text
--ports=22,80,443,445,3389,8080
--web=8765
--oui=/path/to/oui.txt
--agent=192.168.0.20:8786:YOUR_TOKEN
```

Multiple `--agent=` options may be supplied.

## Agent

Start an Agent on a machine you own/manage:

```bash
./run-agent.sh --bind=0.0.0.0 --port=8786
```

The first run creates:

```text
data/agent.token
```

Copy that token into Lantern Core's `--agent=` configuration. Keep it secret.

The Agent defaults to **no Admin Mode permissions**. System telemetry is available after authentication; optional Admin Mode capabilities must be explicitly enabled with flags.

### Enable Admin Mode permissions

Filesystem access:

```bash
./run-agent.sh --bind=0.0.0.0 --port=8786 \
  --admin-files \
  --admin-root=/home/your-user/Documents
```

Multiple allowed roots can be supplied:

```bash
./run-agent.sh \
  --admin-files \
  --admin-root=/home/your-user/Documents \
  --admin-root=/home/your-user/projects
```

Screen snapshots:

```bash
./run-agent.sh --admin-screen
```

Clipboard text:

```bash
./run-agent.sh --admin-clipboard
```

Browser history metadata:

```bash
./run-agent.sh --admin-browser-history
```

You can combine them:

```bash
./run-agent.sh \
  --admin-files \
  --admin-root=/home/your-user/Documents \
  --admin-screen \
  --admin-clipboard \
  --admin-browser-history
```

### Admin permissions

| Permission | What it exposes |
|---|---|
| `FILESYSTEM` | Directory listings and UTF-8 text from explicitly allowed roots |
| `SCREEN` | On-demand PNG screenshot of the local display |
| `CLIPBOARD` | Current clipboard text |
| `BROWSER_HISTORY` | Recent Chrome/Chromium/Edge history metadata when a supported local SQLite database and `sqlite3` are available |

Admin endpoints are token-protected and only exist for enabled permissions.

The filesystem module does **not** allow path traversal outside the configured roots. File reads are capped at 1 MB.

## Agent API

Authenticated endpoints:

```text
GET /api/v1/health
GET /api/v1/system
GET /api/v1/resources
GET /api/v1/network
GET /api/v1/processes
GET /api/v1/admin/permissions
GET /api/v1/admin/files?path=/allowed/path&mode=list
GET /api/v1/admin/files?path=/allowed/file.txt&mode=read
GET /api/v1/admin/screen
GET /api/v1/admin/clipboard
GET /api/v1/admin/browser-history
```

Authentication header:

```text
Authorization: YOUR_AGENT_TOKEN
```

## Dashboard

Start Lantern and open:

```text
http://127.0.0.1:8765
```

The dashboard is served from `web/index.html` and keeps the supplied black/lime design.

A paired Agent now reports its enabled Admin Mode permissions alongside ordinary telemetry. Device details can expose controls for enabled screen, clipboard, browser-history and filesystem modules through Lantern Core's authenticated proxy.

The dashboard remains local-only by default; the Agent is the component that can listen on the LAN.

## Terminal commands

- `d` — inspect a device and available network/Agent information.
- `r` — rescan and compare with the previous inventory.
- `m` — monitor every 10 seconds.
- `h` — show recent history.
- `w` — print dashboard URL.
- `q` — quit.

## What Lantern can know

### Network-only device

Depending on what the target exposes, Lantern can observe:

- IP address
- MAC address
- vendor/OUI
- hostname
- device classification
- latency/reachability
- open TCP ports
- service names
- best-effort banners
- first/last discovery information
- network changes over time

Network-only scanning cannot reliably reveal private host state such as CPU, RAM, files or processes.

### Agent-enabled device

The authorized Agent can add:

- OS/version
- kernel
- architecture
- hardware model
- CPU model/cores/load
- GPU information
- RAM usage
- disk usage
- uptime
- battery
- logged-in user
- network interfaces
- top processes
- enabled Admin Mode permissions

Optional Admin Mode can additionally expose the explicitly enabled capabilities described above.

## Security boundaries

Lantern v1.1 intentionally does **not** implement:

- password collection
- credential/token/private-key extraction
- keystroke logging
- private-message collection
- arbitrary remote shell execution
- covert persistence
- hidden surveillance modules

Use Lantern only on networks and machines you own or are authorized to administer.

## Version

**1.1.0**
# Lantern
