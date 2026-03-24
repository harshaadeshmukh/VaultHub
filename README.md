# 🗂️ VaultHub — Secure File Storage & Sharing Platform

> A full-stack microservices application built with Spring Boot, MySQL, Redis, and Thymeleaf.
> Upload, manage, share, and track files — secured with JWT, BCrypt, and Gmail OTP authentication.

---

## 🎥 Live Demo

🔗 [Watch Demo on YouTube](https://youtu.be/_fMQCxxMIoI?si=rJWAs1i_f3y0NYWF)

---


## 📋 Table of Contents

1. [What is VaultHub?](#what-is-vaulthub)
2. [System Architecture](#system-architecture)
3. [Microservices Overview](#microservices-overview)
4. [How Each Service Works](#how-each-service-works)
5. [Database Design](#database-design)
6. [Security & Authentication](#security--authentication)
7. [File Storage — Chunked Upload System](#file-storage--chunked-upload-system)
8. [File Sharing System](#file-sharing-system)
9. [Password Reset — OTP via Gmail](#password-reset--otp-via-gmail)
10. [Activity Logging](#activity-logging)
11. [Admin Panel](#admin-panel)
12. [Session Management with Redis](#session-management-with-redis)
13. [Request Flow — Step by Step](#request-flow--step-by-step)
14. [How to Run VaultHub](#how-to-run-vaulthub)
15. [Port Reference](#port-reference)
16. [Tech Stack](#tech-stack)

---

## What is VaultHub?

VaultHub is a personal cloud file storage system — think of it like a self-hosted Google Drive. It lets users:

- **Register** and get a unique Vault ID
- **Upload files** of any type (up to 500MB)
- **View and download** their files
- **Share files** with other registered users, with optional expiry
- **Track activity** — every upload, download, share, and view is logged
- **Reset their password** securely via a Gmail OTP email

The system is built as a **microservices architecture** — multiple small Spring Boot applications, each responsible for one job, all connected through a single API Gateway.

---

## System Architecture

```
                        ┌─────────────────────────────────────────┐
                        │            BROWSER / CLIENT              │
                        │         localhost:8080                   │
                        └──────────────────┬──────────────────────┘
                                           │  All requests go here first
                                           ▼
                        ┌─────────────────────────────────────────┐
                        │         🌐 API GATEWAY (8080)            │
                        │         vaulthub-gateway                 │
                        │                                          │
                        │  Routes requests to the right service:   │
                        │  /login, /register  → Auth (8081)        │
                        │  /files, /upload    → File (8082)        │
                        │  /admin             → Auth (8081)        │
                        │  /activity          → File (8082)        │
                        └──────┬─────────────────────┬────────────┘
                               │                     │
               ┌───────────────▼───────┐   ┌─────────▼─────────────┐
               │  🔐 AUTH SERVICE       │   │  📁 FILE SERVICE       │
               │  vaulthub-auth (8081)  │   │  vaulthub-file (8082)  │
               │                        │   │                        │
               │  - Register / Login    │   │  - Upload files        │
               │  - Sessions / JWT      │   │  - Download files      │
               │  - Admin panel         │   │  - Share files         │
               │  - Forgot password     │   │  - Activity log        │
               │  - Settings            │   │  - File viewer         │
               └──────────┬────────────┘   └──────────┬────────────┘
                          │                            │
               ┌──────────▼────────────────────────────▼────────────┐
               │                  MySQL (localhost:3306)              │
               │   vaulthub_auth DB          vaulthub_file DB        │
               │   - users table             - files table           │
               │                             - file_shares table     │
               │                             - activity_logs table   │
               │                             - chunk_records table   │
               └─────────────────────────────────────────────────────┘
                          │                            │
               ┌──────────▼────────────────────────────▼────────────┐
               │               Redis (Upstash Cloud)                  │
               │         Shared session store for both services       │
               │         Namespace: vaulthub:session                  │
               │         TTL: 24 hours                                │
               └─────────────────────────────────────────────────────┘
```

---

## Microservices Overview

| Service | Port | Responsibility |
|---|---|---|
| `vaulthub-gateway` | 8080 | Routes all incoming requests to the right service |
| `vaulthub-auth` | 8081 | User registration, login, sessions, admin panel, password reset |
| `vaulthub-file` | 8082 | File upload/download, sharing, activity logging, file viewer |
| `vaulthub-notify` | 8086 | (Notification service — email notifications, planned) |
| `vaulthub-discovery` | 8761 | (Eureka service registry — currently disabled) |

---

## How Each Service Works

### 🌐 vaulthub-gateway (Port 8080)

The gateway is the **single entry point** for all traffic. The browser only ever talks to port 8080 — it never directly calls 8081 or 8082.

The gateway uses **Spring Cloud Gateway** to route requests based on URL path:

```yaml
routes:
  - id: auth-login
    uri: http://localhost:8081
    predicates:
      - Path=/login, /logout, /register, /forgot-password, /forgot-password/**

  - id: auth-dashboard
    uri: http://localhost:8081
    predicates:
      - Path=/dashboard, /dashboard/**

  - id: file-service
    uri: http://localhost:8082
    predicates:
      - Path=/files, /files/**
```

The gateway also passes `X-Forwarded-*` headers so that when the Auth service redirects the user after login, it redirects back to port 8080 (not 8081 directly).

---

### 🔐 vaulthub-auth (Port 8081)

Handles everything related to users and identity.

**Key responsibilities:**
- **Register** — creates a new user with a BCrypt-hashed password and a unique `vaultId` (e.g. `vault-6a3f2c1b`)
- **Login** — Spring Security validates credentials, creates a session, stores `fullName`, `vaultId`, `isAdmin` in Redis
- **Dashboard** — fetches file stats from the file service via internal HTTP call
- **Settings** — change name, email, password; delete account
- **Admin panel** — view all users, their storage usage, file counts; delete users
- **Forgot password** — 3-step OTP flow via Gmail SMTP

**Technology used:**
- Spring Security with form login
- BCrypt password hashing
- JWT tokens (for inter-service use)
- Thymeleaf for HTML rendering
- Spring Session + Redis for shared sessions

---

### 📁 vaulthub-file (Port 8082)

Handles everything related to files.

**Key responsibilities:**
- **Upload** — chunks large files into 10MB pieces, saves them to disk (`C:/vault-storage/`)
- **Download** — reassembles chunks on the fly and streams to browser
- **File viewer** — renders PDFs, images, videos, text directly in the browser
- **Share** — lets users share any file with another registered user, with optional expiry
- **Activity log** — records every user action (upload, download, share, view, delete)
- **Stats API** — provides file count, storage used, shared file count to the auth dashboard

---

## Database Design

### `vaulthub_auth` database

**`users` table**

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated primary key |
| `full_name` | VARCHAR | User's display name |
| `email` | VARCHAR (unique) | Login identifier |
| `password` | VARCHAR | BCrypt hashed password |
| `vault_id` | VARCHAR (unique) | Unique vault identifier, e.g. `vault-6a3f2c1b` |
| `role` | ENUM | `USER` or `ADMIN` |
| `created_at` | DATETIME | Account creation timestamp |

---

### `vaulthub_file` database

**`files` table**

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated primary key |
| `file_name` | VARCHAR | Original file name |
| `file_size` | BIGINT | Size in bytes |
| `mime_type` | VARCHAR | e.g. `application/pdf`, `image/png` |
| `total_chunks` | INT | How many 10MB chunks the file was split into |
| `owner_id` | BIGINT | The `hashCode()` of the owner's email |
| `file_uuid` | VARCHAR (unique) | UUID used in chunk filenames on disk |
| `status` | ENUM | `UPLOADING` → `READY` → `DELETED` |
| `created_at` | DATETIME | Upload timestamp |

**`chunk_records` table**

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated primary key |
| `file_id` | BIGINT (FK) | References `files.id` |
| `chunk_index` | INT | Order of this chunk (0, 1, 2, ...) |
| `chunk_size` | BIGINT | Bytes in this specific chunk |
| `storage_path` | VARCHAR | Full path on disk, e.g. `C:/vault-storage/abc123_0.chunk` |

**`file_shares` table**

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated primary key |
| `file_uuid` | VARCHAR | Which file is being shared |
| `owner_id` | BIGINT | Who is sharing |
| `owner_email` | VARCHAR | Owner's email |
| `shared_with_email` | VARCHAR | Recipient's email |
| `shared_with_owner_id` | BIGINT | Recipient's owner ID |
| `shared_with_name` | VARCHAR | Recipient's display name |
| `expires_at` | DATETIME | Nullable — if set, share auto-expires |
| `active` | BOOLEAN | `true` = share is active |
| `shared_at` | DATETIME | When it was shared |

**`activity_logs` table**

| Column | Type | Description |
|---|---|---|
| `id` | BIGINT (PK) | Auto-generated primary key |
| `owner_id` | BIGINT | Which user performed the action |
| `type` | ENUM | `UPLOAD`, `DOWNLOAD`, `DELETE`, `SHARE_SENT`, `SHARE_RECEIVED`, `VIEW` |
| `file_name` | VARCHAR | Name of the file involved |
| `file_uuid` | VARCHAR | UUID of the file involved |
| `detail` | VARCHAR | Extra info, e.g. "Shared with user@email.com" |
| `created_at` | DATETIME | When the action happened |

---

## Security & Authentication

### How login works

```
1. User submits email + password on /login

2. Spring Security intercepts the POST /login request

3. DaoAuthenticationProvider loads the user from MySQL by email
   → calls UserDetailsServiceImpl.loadUserByUsername(email)

4. BCryptPasswordEncoder.matches(rawPassword, hashedPassword)
   → If match: authentication succeeds
   → If no match: redirects to /login?error=true

5. On success, customSuccessHandler fires:
   → Stores fullName, vaultId, isAdmin into Redis session
   → Redirects admin → /admin, regular user → /dashboard

6. Browser gets a SESSION cookie (stored in Redis via Upstash)
   → All future requests carry this cookie
   → Both auth and file services share the same Redis session
```

### How BCrypt hashing works

```
Registration:
  "mypassword123"  →  BCrypt  →  "$2a$10$xK9mN3p..."  (stored in DB)

Login:
  "mypassword123"  →  BCrypt.matches()  →  compares with DB hash
  ✅ match = login success
  ❌ no match = login failure

BCrypt is one-way — the original password can NEVER be recovered from the hash.
This is why password reset requires the user to set a NEW password,
not recover the old one.
```

### VaultId

Every user gets a unique vault ID generated at registration:
```java
String vaultId = "vault-" + UUID.randomUUID()
    .toString().replace("-", "").substring(0, 12);
// Example: vault-6a3f2c1bd9e4
```
This is shown in the dashboard and used as a user identifier across the system.

---

## File Storage — Chunked Upload System

VaultHub splits every uploaded file into **10MB chunks** and saves them individually to disk. This prevents memory issues with large files — the file is never fully loaded into RAM.

### Upload flow

```
User selects file on /upload
           ↓
POST /files/upload (multipart form, max 500MB)
           ↓
FileService.uploadFile():
  1. Creates a FileRecord in DB with status = UPLOADING
  2. Opens InputStream from the multipart data
  3. Reads 10MB at a time into a buffer
  4. For each 10MB chunk:
     - Writes chunk to disk: C:/vault-storage/{uuid}_0.chunk
                                               {uuid}_1.chunk
                                               {uuid}_2.chunk
     - Saves a ChunkRecord in DB (file_id, chunk_index, path)
  5. When all chunks written: sets FileRecord.status = READY
           ↓
ActivityLog entry created: type=UPLOAD
```

### Download / Stream flow

```
User clicks Download on /files
           ↓
GET /files/stream/{fileUuid}
           ↓
FileService reassembles the file:
  1. Loads all ChunkRecords for this fileUuid, ordered by chunk_index
  2. Opens a SequenceInputStream across all chunk files
  3. Streams bytes directly to the HTTP response
  4. Sets Content-Disposition: attachment; filename="original.pdf"
  → File downloads directly, never fully loaded into RAM
           ↓
ActivityLog entry created: type=DOWNLOAD
```

### Chunk files on disk

```
C:/vault-storage/
├── a1b2c3d4e5f6_0.chunk   (10MB)
├── a1b2c3d4e5f6_1.chunk   (10MB)
├── a1b2c3d4e5f6_2.chunk   (4.7MB)  ← last chunk is smaller
├── f9e8d7c6b5a4_0.chunk   (8.2MB)  ← small file, only 1 chunk
└── ...
```

---

## File Sharing System

Users can share any of their files with any other registered VaultHub user.

### Share flow

```
1. Owner opens a file and clicks "Share"

2. Owner types a recipient email in the share modal

3. JS calls GET /api/share/lookup?email=... 
   → File service calls Auth service's user API
   → Returns { found: true, name: "...", email: "..." } or { found: false }

4. If found, owner confirms and clicks "Share"

5. POST /files/share/send
   → Creates a FileShare record in DB
   → Optional: expiresAt set if owner chose an expiry time
   → ActivityLog: SHARE_SENT for owner, SHARE_RECEIVED for recipient

6. Recipient sees the file appear in their "Shared with me" section
   → /shared page lists all FileShare records where sharedWithOwnerId = user

7. Expired shares are automatically filtered out (isExpired() check)
```

---

## Password Reset — OTP via Gmail

A 3-step secure password reset flow using Gmail SMTP.

### Full flow

```
Step 1 — /forgot-password
  User enters their registered email address
           ↓
  POST /forgot-password/send-otp
  → User found in DB?
     ✅ Yes: OtpService.sendOtp(email, fullName)
        - Generates a random 6-digit OTP (e.g. 482910)
        - Stores in memory: { email → { otp, expiry (10 min), attempts: 0 } }
        - JavaMailSender connects to smtp.gmail.com:587
        - Sends HTML email with the OTP to the user
        - Session: fp_email = user's email
        - Redirects to /forgot-password/verify
     ❌ No: Shows error "No account found"

Step 2 — /forgot-password/verify
  User enters the 6-digit OTP from their email
           ↓
  POST /forgot-password/verify-otp
  → OtpService.verify(email, otp)
     ✅ OK: session fp_verified = true → redirect to Step 3
     ❌ WRONG: attempts++ → show error (max 5 attempts)
     ❌ EXPIRED: clear session → back to Step 1
     ❌ TOO_MANY: locked out → back to Step 1

Step 3 — /forgot-password/reset
  User sets a new password (min 6 chars)
           ↓
  POST /forgot-password/reset
  → New password BCrypt hashed → saved to DB
  → fp_email and fp_verified cleared from session
  → Redirect to /login?passwordReset=true ✅
```

### How the email is sent

```java
// Spring Boot auto-creates JavaMailSender from application.yml config
MimeMessage msg = mailSender.createMimeMessage();
MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");

helper.setTo("user@email.com");
helper.setFrom("harshad.deshmukh82004@gmail.com", "VaultHub Security");
helper.setSubject("🔐 Your VaultHub Password Reset OTP");
helper.setText(htmlEmailString, true);  // true = send as HTML

mailSender.send(msg);  // connects to smtp.gmail.com:587, authenticates, sends
```

The email is a full **dark-themed HTML template** — inline CSS, gradient header, large styled OTP display. Gmail renders it exactly like a designed web page.

---

## Activity Logging

Every significant user action is automatically recorded in the `activity_logs` table.

| Action | When it's logged |
|---|---|
| `UPLOAD` | When a file finishes uploading (status = READY) |
| `DOWNLOAD` | When a file stream is requested |
| `DELETE` | When a file is deleted |
| `SHARE_SENT` | When owner shares a file with someone |
| `SHARE_RECEIVED` | When someone receives a shared file |
| `VIEW` | When a file is opened in the viewer |

Users can see their full activity history at `/activity` — sorted by most recent, showing file name, action type, and timestamp.

---

## Admin Panel

The admin panel is accessible only to `harshad@gmail.com` (hardcoded in `SecurityConfig` and `AdminController`).

**Admin can:**
- See all registered users in a table
- View each user's file count and storage usage (fetched live from file service)
- See total platform-wide stats (total users, total files, total storage)
- Click into a user to see their individual files
- Delete any user account

**How admin gets file stats:**

The auth service calls the file service via HTTP to get per-user stats:
```java
RestTemplate rt = new RestTemplate();
ResponseEntity<Map> resp = rt.exchange(
    "http://localhost:8082/api/admin/user-stats",
    HttpMethod.GET, null, Map.class
);
// Returns { "ownerId1": { fileCount, totalBytes }, "ownerId2": { ... }, ... }
```

---

## Session Management with Redis

Both the auth service (8081) and file service (8082) share the **same Redis session store** on Upstash. This is what makes login work across both services without re-authenticating.

```
Browser logs in at localhost:8080/login
           ↓
Auth service creates session, stores in Redis:
  Key:   vaulthub:session:{sessionId}
  Value: { fullName, vaultId, isAdmin, Spring Security context }
  TTL:   24 hours

Browser gets SESSION cookie = sessionId

Browser visits localhost:8080/files (routed to File service)
           ↓
File service reads the same Redis session using the cookie
→ Knows who the user is without a separate login
```

---

## Request Flow — Step by Step

### Example: User uploads a file

```
1. Browser → GET localhost:8080/files
   Gateway → proxies to localhost:8082/files
   File service → checks Redis session → authenticated ✅
   Returns files.html (Thymeleaf rendered)

2. User selects a file and submits the upload form

3. Browser → POST localhost:8080/files/upload
   Gateway → proxies to localhost:8082/files/upload

4. FileController receives the MultipartFile
   → calls FileService.uploadFile(file, ownerId)

5. FileService:
   → Creates FileRecord in vaulthub_file DB (status=UPLOADING)
   → Reads file in 10MB chunks
   → Writes each chunk to C:/vault-storage/
   → Creates ChunkRecord for each chunk
   → Sets FileRecord status = READY

6. ActivityLogService.log(ownerId, UPLOAD, fileName, fileUuid)
   → Inserts row in activity_logs table

7. File service returns success response
   → Browser shows the new file in the file list
```

### Example: User logs in

```
1. Browser → POST localhost:8080/login (email + password)
   Gateway → proxies to localhost:8081/login

2. Spring Security:
   → Loads user from MySQL by email
   → BCrypt.matches(enteredPassword, storedHash)
   → Match found ✅

3. customSuccessHandler fires:
   → Saves fullName, vaultId, isAdmin to Redis session
   → Detects admin email → redirects to /admin
   → Regular user → redirects to /dashboard

4. Browser → GET localhost:8080/dashboard
   Gateway → proxies to localhost:8081/dashboard

5. HomeController.dashboard():
   → Reads user from MySQL
   → Makes 2 parallel HTTP calls to localhost:8082/api/files/stats and /analytics
   → Populates model with file count, storage used, charts data

6. Returns dashboard.html with all data rendered by Thymeleaf
```

---

## How to Run VaultHub

### Prerequisites

- Java 17+
- Maven 3.8+
- MySQL 8.0 running on port 3306
- Internet access (for Upstash Redis and Gmail SMTP)

### Step 1 — Set up MySQL

Just start MySQL. VaultHub auto-creates the databases:
- `vaulthub_auth` (for the auth service)
- `vaulthub_file` (for the file service)

Default credentials in `application.yml`:
```
username: root
password: harshad@123#
```

### Step 2 — Build all services

```bash
cd VaultHub
mvn clean package -DskipTests
```

### Step 3 — Start services (in this order)

```bash
# 1. Start Auth Service
cd vaulthub-auth
mvn spring-boot:run

# 2. Start File Service (new terminal)
cd vaulthub-file
mvn spring-boot:run

# 3. Start Gateway (new terminal)
cd vaulthub-gateway
mvn spring-boot:run
```

### Step 4 — Open the app

```
http://localhost:8080
```

### Create storage directory

Make sure this folder exists for file storage:
```
C:/vault-storage/
```
Or change the path in `vaulthub-file/application.yml`:
```yaml
storage:
  path: ./vault-storage   # relative path, works on any OS
```

---

## Port Reference

| Port | Service | URL |
|---|---|---|
| 8080 | Gateway (use this) | http://localhost:8080 |
| 8081 | Auth Service | http://localhost:8081 (direct) |
| 8082 | File Service | http://localhost:8082 (direct) |
| 8086 | Notify Service | http://localhost:8086 |
| 8761 | Eureka Discovery | http://localhost:8761 (disabled) |
| 3306 | MySQL | jdbc:mysql://localhost:3306 |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend framework | Spring Boot 3.x |
| Security | Spring Security 6, BCrypt, JWT |
| Database | MySQL 8.0 + Spring Data JPA / Hibernate |
| Session store | Redis (Upstash cloud) via Spring Session |
| Frontend | Thymeleaf + Bootstrap 5 + vanilla JS |
| Email | Spring Mail + JavaMail + Gmail SMTP |
| File storage | Local disk (chunked, streamed) |
| Routing | Spring Cloud Gateway |
| Build tool | Maven |
| Java version | Java 17 |

---

## Project Structure

```
VaultHub/
├── vaulthub-gateway/          # API Gateway — routes all traffic
│   └── application.yml        # Route definitions
│
├── vaulthub-auth/             # Auth Service
│   ├── controller/
│   │   ├── HomeController         # /, /login, /register, /dashboard
│   │   ├── ForgotPasswordController # Password reset OTP flow
│   │   ├── SettingsController     # /settings
│   │   ├── AdminController        # /admin panel
│   │   └── UserApiController      # Internal API for user lookup
│   ├── service/
│   │   ├── AuthService            # Register, find user
│   │   └── OtpService             # OTP generate, verify, send email
│   ├── entity/User.java           # User DB model
│   ├── config/SecurityConfig.java # Spring Security setup
│   └── templates/                 # Thymeleaf HTML pages
│
├── vaulthub-file/             # File Service
│   ├── controller/
│   │   ├── FileController         # /files, /upload, /viewer
│   │   ├── FileApiController      # /api/files/stats, /analytics
│   │   ├── ShareController        # File sharing
│   │   └── ActivityController     # /activity log page
│   ├── service/
│   │   ├── FileService            # Chunked upload/download
│   │   └── ShareService           # Share logic
│   ├── entity/
│   │   ├── FileRecord             # File metadata
│   │   ├── ChunkRecord            # Per-chunk disk path
│   │   ├── FileShare              # Share records
│   │   └── ActivityLog            # User action logs
│   └── templates/                 # Thymeleaf HTML pages
│
├── vaulthub-notify/           # Notification Service (planned)
└── vaulthub-discovery/        # Eureka registry (disabled)
```

---

*Built with ❤️ — VaultHub is a learning project demonstrating Spring Boot microservices, secure authentication, chunked file handling, and modern web UI design.*
