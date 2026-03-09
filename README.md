# 🗄️ DecentraVault — Decentralized Storage System

> *"Build your own Google Drive — but no Google, no server, no middleman. You own every byte, forever. Share only within the vault — cryptographically secured."*

---

## 🌍 The Problem

Every file you store today lives on **someone else's computer:**

```
Google Drive  →  Google can read, delete, suspend your account
Dropbox       →  Dropbox servers get hacked → your files leak
iCloud        →  Apple can hand your data to government
OneDrive      →  Microsoft owns the infrastructure
```

**Real incidents:**
```
2014  →  iCloud hack      →  celebrities' private photos leaked
2012  →  Dropbox breach   →  68 million passwords stolen
2022  →  Google Drive bug →  files silently deleted
2023  →  OneDrive outage  →  millions lost access for days
```

> The root problem? **You don't actually own your files.** You rent space on their servers.

---

## 💡 What DecentraVault Solves

```
YOU upload a file
      ↓
Nobody else can read it        (AES encryption per chunk)
Nobody else can delete it      (blockchain ownership proof)
Nobody controls it             (decentralized nodes)
Only YOU can share it          (cryptographic permission)
Only VAULT users can receive   (closed ecosystem)
Every access is identity-proof (receiver's public key used)
```

---

## 🧠 Core Idea — How It Actually Works

### Step 1 — File Gets Chunked
```
You upload → "movie.mp4"  (500MB)
                 ↓
    Split into small chunks
    ┌─────────────────────┐
    │  chunk_001  (10MB)  │
    │  chunk_002  (10MB)  │
    │  chunk_003  (10MB)  │
    │  ...                │
    │  chunk_050  (10MB)  │
    └─────────────────────┘
    Each chunk gets a SHA-256 fingerprint hash
```

### Step 2 — Every Chunk Gets Encrypted
```
chunk_001  →  AES encrypted  →  🔒 encrypted_chunk_001
chunk_002  →  AES encrypted  →  🔒 encrypted_chunk_002
...
Only YOUR RSA private key can decrypt these
Even node owners cannot read your files!
```

### Step 3 — Chunks Spread Across Nodes
```
NOT stored in one place!

Node A  →  chunk_001, chunk_007, chunk_023
Node B  →  chunk_002, chunk_008, chunk_031
Node C  →  chunk_003, chunk_009, chunk_045

Each chunk replicated on 2 nodes for safety
Even if Node A crashes → file still safe on B and C
```

### Step 4 — Blockchain Records Ownership
```
New block added to chain:
┌─────────────────────────────────────────┐
│  Owner:     did:vault:rahul_xyz         │
│  File:      movie.mp4                   │
│  FileHash:  a3f9b2c7d1e8...            │
│  Chunks:    50 chunks                   │
│  Nodes:     [NodeA, NodeB, NodeC]       │
│  Timestamp: 2024-01-15 10:30 AM        │
│  PrevHash:  8e2a1f4c9b3d...            │
└─────────────────────────────────────────┘
This record CANNOT be tampered — ever!
```

### Step 5 — Download = Reassemble
```
You request "movie.mp4"
      ↓
System fetches all 50 chunks from nodes
      ↓
Decrypts each chunk with your private key
      ↓
Reassembles in correct order via chunk index
      ↓
Verifies final hash matches original
      ↓
You get back perfect "movie.mp4" ✅
```

---

## 👤 What You Can Do As Owner

| Action | Description |
|--------|-------------|
| 📤 **UPLOAD** | pdf, image, video, audio, docs — any file |
| 👁️ **OPEN / VIEW** | open and view file directly inside the vault |
| 📥 **DOWNLOAD** | fetch + decrypt + reassemble instantly |
| 🗑️ **DELETE** | removes ALL chunks from ALL nodes + blockchain record marked DELETED |
| 🔗 **SHARE** | enter receiver's VAULT email only — encrypted with THEIR public key |
| 📋 **MY FILES** | list all files, size, date, who has access |
| 🔔 **ACTIVITY** | real-time "xyz@vault accessed your file" |
| 🚫 **REVOKE** | cancel any share instantly, anytime |

---

## 👁️ Open / View Feature — Inside Vault

```
YOU click "Open" on "report.pdf"
          ↓
System fetches all chunks from nodes
          ↓
Decrypts chunks in memory
(never written to disk unencrypted!)
          ↓
Streams content directly to vault viewer
          ↓
┌─────────────────────────────┐
│   🔒 DecentraVault Viewer   │
│                             │
│   [  report.pdf content  ]  │
│                             │
│   Page 1 of 10    🔐 secure │
└─────────────────────────────┘
          ↓
File NEVER leaves the vault
No temporary files on server
No browser cache exposure
```

### What Opens Where

| File Type | Opens In |
|-----------|----------|
| 📄 PDF | In-vault PDF viewer |
| 🖼️ Image (jpg / png / gif) | In-vault image viewer |
| 🎬 Video (mp4 / mkv) | In-vault streaming player |
| 🎵 Audio (mp3 / wav) | In-vault audio player |
| 📝 Docs (txt / md) | In-vault text viewer |

---

## 🔐 Share Feature — Within Vault Only

```
YOU share "report.pdf" → friend@vault.com
                ↓
System checks:
"Is friend@vault.com a registered DecentraVault user?"
                ↓
      YES ✅                      NO ❌
        ↓                            ↓
Fetch their PUBLIC KEY        "User not found
from blockchain               in DecentraVault.
        ↓                     Ask them to register."
Encrypt share token
using THEIR public key
        ↓
Only THEIR private key
can decrypt this token
        ↓
They login to vault →
notification appears:
"Rahul shared report.pdf"
        ↓
They open or download inside vault
using their decrypted token
        ↓
YOU get real-time alert via WebSocket:
"🔔 friend@vault.com opened report.pdf — just now"
        ↓
Blockchain logs this access permanently:
"friend@vault.com accessed report.pdf at 10:32 AM"
```

### Why Within-Vault Only Makes It More Secure

| Random Email Link Sharing | DecentraVault Share |
|---------------------------|---------------------|
| Anyone with link can access | Only verified vault user |
| No identity verification | Receiver has cryptographic DID identity |
| Link can be forwarded freely | Token tied to receiver's private key only |
| No real audit trail | Every access recorded on blockchain |
| Hard to revoke properly | Owner can revoke instantly anytime |
| Receiver identity unknown | Receiver identity cryptographically proven |

---

## 🏗️ System Architecture

```
┌──────────────────────────────────────────────┐
│                 API GATEWAY                  │
│       Route + Rate Limit + Auth Verify       │
└──────────────────┬───────────────────────────┘
                   │
      ┌────────────┼─────────────┐
      │            │             │
 ┌────▼────┐  ┌────▼────┐  ┌────▼────┐
 │  auth   │  │  file   │  │  node   │
 │ service │  │ service │  │ service │
 │         │  │         │  │         │
 │Register │  │ Upload  │  │  Store  │
 │ Login   │  │Download │  │ chunks  │
 │ KeyGen  │  │ Delete  │  │ Health  │
 │   JWT   │  │  Share  │  │  check  │
 │   DID   │  │  Open   │  │  Sync   │
 │         │  │  List   │  │         │
 └─────────┘  └────┬────┘  └─────────┘
                   │
      ┌────────────┼─────────────┐
      │            │             │
 ┌────▼────┐  ┌────▼─────┐ ┌────▼────┐
 │  chunk  │  │blockchain│ │ notify  │
 │ service │  │ service  │ │ service │
 │         │  │          │ │         │
 │  Split  │  │Add block │ │  Email  │
 │ Encrypt │  │  Verify  │ │WebSocket│
 │ Decrypt │  │Ownership │ │Activity │
 │Reassmbly│  │Audit log │ │  feed   │
 │ Stream  │  │          │ │         │
 └─────────┘  └──────────┘ └─────────┘
                   │
      ┌────────────┼─────────────┐
      │            │             │
 ┌────▼──┐    ┌────▼──┐    ┌────▼──┐
 │ NODE  │    │ NODE  │    │ NODE  │
 │   A   │    │   B   │    │   C   │
 │       │    │       │    │       │
 │chunks │    │chunks │    │chunks │
 │ stored│    │ stored│    │ stored│
 └───────┘    └───────┘    └───────┘
```

---

## 🎨 Frontend — Thymeleaf + Bootstrap 5

### Why Thymeleaf?

```
React / Angular          Thymeleaf
───────────────          ─────────────────
Separate project    vs   Lives INSIDE Spring Boot
Need Node.js        vs   No extra setup needed
JavaScript heavy    vs   Mostly HTML + little JS
2 projects to run   vs   1 project runs everything
Complex setup       vs   Just add dependency — done!
```

> You focus **90% on Java backend** — Thymeleaf handles the UI with simple HTML! 🎯

### UI Layout

```
┌─────────────────────────────────────────────┐
│  🔐 DecentraVault        [👤 Rahul] [Logout] │
├─────────────────────────────────────────────┤
│  📤 Upload File                             │
├─────────────────────────────────────────────┤
│  MY FILES                                   │
│  ┌──────────────────────────────────────┐   │
│  │ 📄 report.pdf   2MB   👁️ 🔗 📥 🗑️    │   │
│  │ 🖼️ photo.jpg    1MB   👁️ 🔗 📥 🗑️    │   │
│  │ 🎬 movie.mp4  500MB   👁️ 🔗 📥 🗑️    │   │
│  │ 🎵 song.mp3    5MB    👁️ 🔗 📥 🗑️    │   │
│  └──────────────────────────────────────┘   │
├─────────────────────────────────────────────┤
│  🔔 ACTIVITY FEED (WebSocket live)          │
│  • friend@vault.com opened report.pdf       │
│  • You uploaded movie.mp4 — 2 mins ago      │
└─────────────────────────────────────────────┘
```

### Pages / Templates

| Page | Template File | Purpose |
|------|--------------|---------|
| Login | `login.html` | Vault login with DID |
| Register | `register.html` | Create vault account + key generation |
| Dashboard | `dashboard.html` | Overview + activity feed |
| My Files | `files.html` | List, upload, delete, share |
| Viewer | `viewer.html` | Open PDF / image / video / audio in vault |
| Share | `share.html` | Share file with vault user |
| Activity | `activity.html` | Full access history |

### Frontend Stack

| Layer | Technology |
|-------|-----------|
| Templates | Thymeleaf (built into Spring Boot) |
| Styling | Bootstrap 5 (copy-paste beautiful UI) |
| Live Updates | WebSocket + vanilla JavaScript |
| File Viewer | Browser-native viewers inside Thymeleaf |

---

## 🗄️ Database Design

```
┌──────────────────────────────────────────────┐
│                   users                      │
│  id, email, publicKey, privateKeyHash,       │
│  did, vaultId, createdAt                     │
├──────────────────────────────────────────────┤
│                   files                      │
│  id, ownerId, fileName, fileSize,            │
│  mimeType, totalChunks, fileHash,            │
│  status (ACTIVE / DELETED), createdAt        │
├──────────────────────────────────────────────┤
│                  chunks                      │
│  id, fileId, chunkIndex, chunkHash,          │
│  nodeId, encryptedSize, createdAt            │
├──────────────────────────────────────────────┤
│                   nodes                      │
│  id, nodeUrl, nodePort, storageUsed,         │
│  storageTotal, isActive, lastPing            │
├──────────────────────────────────────────────┤
│               share_tokens                   │
│  id, fileId, sharedBy, sharedWith,           │
│  encryptedToken, expiresAt,                  │
│  isRevoked, accessedAt                       │
├──────────────────────────────────────────────┤
│            blockchain_blocks                 │
│  id, blockIndex, previousHash, hash,         │
│  transactionType, transactionData,           │
│  timestamp, nonce                            │
├──────────────────────────────────────────────┤
│              activity_logs                   │
│  id, fileId, actorDID, actionType,           │
│  (UPLOAD/DOWNLOAD/OPEN/SHARE/REVOKE/DELETE)  │
│  timestamp, ipAddress                        │
└──────────────────────────────────────────────┘
```

---

## 🗂️ Project Structure

```
decentravault/
├── src/
│   ├── main/
│   │   ├── java/com.decentravault/
│   │   │   ├── gateway/           ← API Gateway
│   │   │   ├── auth/              ← Auth service
│   │   │   ├── file/              ← File service
│   │   │   ├── chunk/             ← Chunk engine
│   │   │   ├── node/              ← Node service
│   │   │   ├── blockchain/        ← Blockchain (pure Java)
│   │   │   ├── notify/            ← WebSocket + Email
│   │   │   └── shared/            ← Common utilities
│   │   └── resources/
│   │       ├── templates/         ← Thymeleaf HTML pages
│   │       │   ├── login.html
│   │       │   ├── register.html
│   │       │   ├── dashboard.html
│   │       │   ├── files.html
│   │       │   ├── viewer.html
│   │       │   ├── share.html
│   │       │   └── activity.html
│   │       ├── static/
│   │       │   ├── css/           ← Bootstrap 5 + custom
│   │       │   └── js/            ← WebSocket JS
│   │       └── application.yml
├── docker-compose.yml
├── Dockerfile
└── README.md
```

---

## 📚 Full Tech Stack

| Layer | Technology |
|-------|-----------|
| Framework | Spring Boot 3.x |
| Language | Java 17+ |
| Frontend | Thymeleaf + Bootstrap 5 |
| Database | MySQL + Spring Data JPA |
| Security | Spring Security + JWT + RSA |
| Real-time | WebSocket (Spring) |
| Reactive | Spring WebFlux + R2DBC |
| Email | Spring Mail (JavaMail) |
| DevOps | Docker + Kubernetes |
| Gateway | Spring Cloud Gateway |
| Discovery | Eureka |

---

## 📚 What You'll Learn — Phase by Phase

### Phase 1 — Foundation
```
✅ Spring Boot 3.x project setup
✅ MySQL + Spring Data JPA
✅ File chunking engine in pure Java
✅ SHA-256 hashing per chunk
✅ Thymeleaf basic templates
```

### Phase 2 — Encryption
```
✅ Java Cryptography deep dive
✅ AES encryption per chunk
✅ RSA key pair generation
✅ Secure key storage patterns
```

### Phase 3 — Node Distribution
```
✅ Multi-node architecture
✅ Consistent hashing algorithm
✅ Node health check system
✅ Chunk replication strategy
```

### Phase 4 — Blockchain
```
✅ Build blockchain from scratch in Java
✅ Block + chain data structure
✅ Ownership proof per file
✅ Tamper detection logic
```

### Phase 5 — Auth & Identity
```
✅ Spring Security deep dive
✅ JWT token generation + validation
✅ RSA-based passwordless login
✅ DID identity per vault user
```

### Phase 6 — Share System
```
✅ Share within vault only
✅ Encrypt token with receiver public key
✅ Token revocation mechanism
✅ Spring Mail integration
```

### Phase 7 — Open / View
```
✅ Stream file chunks in memory
✅ In-vault PDF viewer (Thymeleaf)
✅ In-vault video / audio player
✅ In-vault image viewer
✅ Zero disk exposure during view
```

### Phase 8 — Real-time
```
✅ WebSocket with Spring Boot
✅ Live upload progress bar
✅ Real-time activity feed
✅ "File accessed / opened" instant alerts
```

### Phase 9 — Reactive
```
✅ Spring WebFlux (reactive programming)
✅ Stream large video files reactively
✅ Non-blocking chunk distribution
✅ Mono & Flux concepts
```

### Phase 10 — DevOps
```
✅ Docker + Kubernetes
✅ Each node = Docker container
✅ Scale storage nodes dynamically
✅ Kubernetes pod management
```

---

## 🗺️ Week by Week Roadmap

| Week | Focus |
|------|-------|
| Week 1 | Project setup + file chunking engine |
| Week 2 | AES + RSA encryption layer |
| Week 3 | Multi-node chunk distribution |
| Week 4 | Pure Java blockchain ownership |
| Week 5 | Spring Security + JWT + DID auth |
| Week 6 | Vault-only share + email + revoke |
| Week 7 | Open / view inside vault |
| Week 8 | WebSocket real-time alerts |
| Week 9 | Spring WebFlux reactive streaming |
| Week 10 | Docker + Kubernetes deployment |

---

## 🔥 Why This Project Is Unique

```
✅ Nobody builds this from scratch — only you
✅ Combines cryptography + blockchain + distributed systems
✅ Closed vault ecosystem — no random link sharing
✅ Open files securely inside vault — zero exposure
✅ Every share cryptographically tied to receiver identity
✅ Real alternative to Google Drive / Dropbox
✅ You understand HOW IPFS works by building it yourself
✅ Resume / portfolio → no interviewer has seen this before
✅ Every concept learnt = industry-level backend skill
✅ Can be turned into a real product / startup 🚀
```

---

## 🆚 DecentraVault vs Others

| Feature | Google Drive | IPFS Project (Node.js) | **DecentraVault** |
|---------|-------------|----------------------|-------------------|
| Language | — | JavaScript | **Java / Spring Boot** |
| Decentralization | ❌ | Uses IPFS library | **Built from scratch** |
| Encryption | Partial | Basic | **AES + RSA per chunk** |
| Blockchain | ❌ | ❌ | **Pure Java blockchain** |
| Share control | Link-based | Link-based | **Vault users only** |
| Open in browser | ✅ | ❌ | **✅ In-vault secure viewer** |
| Real-time alerts | ❌ | ❌ | **WebSocket** |
| Reactive streaming | ❌ | ❌ | **Spring WebFlux** |
| Identity (DID) | ❌ | ❌ | **Cryptographic DID** |
| Frontend | Web app | None | **Thymeleaf + Bootstrap 5** |

---

*Built with ❤️ using Spring Boot — from scratch, no shortcuts.*
