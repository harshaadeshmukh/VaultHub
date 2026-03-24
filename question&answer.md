# 🗂️ VaultHub — Interview Questions & Answers

> Simple English answers to every question someone might ask about your project.
> Covers concepts, architecture, security, and design decisions.

---

## 📋 Categories

1. [Basic Project Questions](#1-basic-project-questions)
2. [Microservices Questions](#2-microservices-questions)
3. [Spring Boot Questions](#3-spring-boot-questions)
4. [Security Questions](#4-security-questions)
5. [Database Questions](#5-database-questions)
6. [File Upload & Storage Questions](#6-file-upload--storage-questions)
7. [Redis & Session Questions](#7-redis--session-questions)
8. [Email & OTP Questions](#8-email--otp-questions)
9. [API & Gateway Questions](#9-api--gateway-questions)
10. [Design Decision Questions](#10-design-decision-questions)
11. [Tricky / Deep Questions](#11-tricky--deep-questions)

---

## 1. Basic Project Questions

---

**Q: Tell me about your project in simple words.**

> VaultHub is like a personal Google Drive that I built myself.
> Users can register, upload files, share files with other users, and track every action they take.
> It is built using Spring Boot and follows a microservices architecture — meaning different parts of the app run as separate programs that talk to each other.

---

**Q: Why did you build this project?**

> I built it to learn how real-world applications work — things like microservices, secure login, file handling, session management, and email sending. Instead of just reading theory, I wanted to build something end-to-end and solve real problems like large file uploads, shared sessions across services, and password security.

---

**Q: What are the main features of VaultHub?**

> - User registration and login with secure password hashing
> - File upload (supports files up to 500MB)
> - File download and in-browser viewer
> - File sharing with other registered users, with optional expiry time
> - Activity log — every action (upload, download, share, view, delete) is recorded
> - Admin panel to manage all users and see their storage usage
> - Forgot password using Gmail OTP — 6-digit code sent to email, expires in 10 minutes

---

**Q: What technologies did you use?**

> - **Java + Spring Boot** — main backend framework
> - **Spring Security** — login, logout, access control
> - **MySQL** — to store users and file records
> - **Redis (Upstash)** — to store user sessions in the cloud
> - **Thymeleaf** — to generate HTML pages on the server
> - **Spring Cloud Gateway** — single entry point that routes traffic
> - **JavaMail + Gmail SMTP** — to send OTP emails
> - **Bootstrap 5** — for styling the UI

---

**Q: How many services does VaultHub have?**

> Five services total:
> - **Gateway** (port 8080) — routes all requests
> - **Auth Service** (port 8081) — handles users, login, password reset
> - **File Service** (port 8082) — handles file upload, download, sharing
> - **Notify Service** (port 8086) — planned for notifications
> - **Discovery Service** (port 8761) — Eureka registry, currently disabled

---

## 2. Microservices Questions

---

**Q: What is a microservice? Why did you use it here?**

> A microservice is a small, independent program that does one specific job.
> Instead of building one giant application that does everything, you build many small apps.
>
> I used it because:
> - Auth and File are separate concerns — they should be separate services
> - If the file service crashes, login still works
> - Each service can be updated independently
> - It reflects how real companies like Netflix and Amazon structure their apps

---

**Q: How do your microservices communicate with each other?**

> They communicate using **HTTP REST calls** — just like a browser calls a server.
>
> Example: When the dashboard loads, the Auth service calls the File service:
> ```
> GET http://localhost:8082/api/files/stats?ownerId=12345
> ```
> The File service returns file count, storage used, etc.
> The Auth service adds that data to the dashboard page.

---

**Q: What happens if the File service is down when the dashboard loads?**

> I handled this with a **try-catch block**.
> If the HTTP call to the file service fails or times out (4 second timeout), the dashboard still loads — it just shows 0 files, 0 storage instead of crashing.
>
> This is called **graceful degradation** — the app keeps working even when one part fails.

---

**Q: Why is there a Gateway? Can't the browser call the services directly?**

> The browser could call them directly, but that causes problems:
> - Users would need to remember different port numbers (8081, 8082)
> - You can't share sessions easily across different origins
> - There is no central place to add security or logging
>
> The Gateway solves all this — the browser only ever talks to port 8080, and the Gateway decides where to send each request based on the URL path.

---

**Q: What is service discovery? Is it used here?**

> Service discovery is a system where services automatically find each other — they register themselves and others look them up by name instead of by `localhost:8081`.
>
> I included Eureka (Spring Cloud's discovery service) but it is currently **disabled**. Right now services call each other using hardcoded localhost URLs, which is fine for a local development setup.

---

## 3. Spring Boot Questions

---

**Q: What is Spring Boot and why use it?**

> Spring Boot is a framework that makes it easy to build Java web applications.
> Without it, you would need to write hundreds of lines of configuration.
> Spring Boot auto-configures most things — you just add a dependency and it works.
>
> For example, adding `spring-boot-starter-mail` automatically sets up the email sender — no manual setup needed.

---

**Q: What is Spring Security?**

> Spring Security is a library that handles login, logout, and access control automatically.
>
> In VaultHub I configured it to:
> - Allow anyone to access `/login`, `/register`, `/forgot-password` without being logged in
> - Require login for everything else (`/dashboard`, `/files`, `/settings`)
> - Redirect to `/login` if someone tries to access a protected page without being logged in

---

**Q: What is Thymeleaf?**

> Thymeleaf is a template engine — it takes an HTML file and fills in dynamic data from Java before sending it to the browser.
>
> Example:
> ```html
> <p th:text="${user.fullName}">Default Name</p>
> ```
> This shows the actual user's name from the Java model, not "Default Name".

---

**Q: What is dependency injection? How is it used in your project?**

> Dependency injection means you don't create objects yourself — Spring creates them and gives them to you.
>
> In VaultHub, instead of writing:
> ```java
> UserRepository repo = new UserRepository(); // ❌ manual
> ```
> I write:
> ```java
> @RequiredArgsConstructor         // ✅ Spring injects it
> public class AuthService {
>     private final UserRepository userRepository;
> }
> ```
> Spring Boot automatically creates `UserRepository` and passes it in. This makes the code clean and easy to test.

---

**Q: What is `@Transactional`? Where did you use it?**

> `@Transactional` means — run all the database operations in this method together. If any one fails, undo all of them (rollback).
>
> I used it on the `register()` method in `AuthService`. When registering a user, we save the user to the database. If anything goes wrong mid-way, the entire registration is cancelled — no half-saved user in the database.

---

## 4. Security Questions

---

**Q: How do you store passwords? Is plain text safe?**

> No, plain text is never safe. If someone steals the database, they would see everyone's password directly.
>
> In VaultHub, passwords are hashed using **BCrypt**:
> ```
> "mypassword123"  →  BCrypt  →  "$2a$10$xK9mN3p..."
> ```
> The hash is stored, not the real password. BCrypt is a one-way function — you cannot reverse it to get the original password back.
>
> When someone logs in, BCrypt hashes what they typed and compares it to the stored hash. If they match, login succeeds.

---

**Q: What is BCrypt? Why not use MD5 or SHA-256?**

> BCrypt is specifically designed for hashing passwords. MD5 and SHA-256 are designed to be fast — that's actually bad for passwords because attackers can try billions of guesses per second.
>
> BCrypt is deliberately **slow** — it takes ~100ms to hash. That means an attacker would take years to brute-force a BCrypt hash.
>
> BCrypt also adds a **salt** (random data) automatically, so two users with the same password get different hashes. This prevents rainbow table attacks.

---

**Q: What is a session? How does login work in VaultHub?**

> A session is a way to remember who you are between requests.
>
> HTTP is stateless — every request is independent. Sessions solve this:
> 1. You log in → server creates a session ID
> 2. Browser stores the session ID in a cookie
> 3. Every future request sends that cookie
> 4. Server looks up the session ID → knows who you are
>
> In VaultHub, sessions are stored in **Redis** (not in server memory), so both the Auth and File services can read the same session.

---

**Q: What is JWT? Did you use it?**

> JWT (JSON Web Token) is a way to send user information securely between services without hitting the database every time.
>
> I have JWT configured in `application.yml` and the dependency is added, but the main authentication in VaultHub uses **Spring Session + Redis** (cookie-based sessions).
> JWT is set up as groundwork for future inter-service authentication where services might need to verify requests from each other.

---

**Q: What is CSRF? Why did you disable it?**

> CSRF (Cross-Site Request Forgery) is an attack where a malicious website tricks your browser into making a request to another site where you're logged in.
>
> I disabled CSRF protection in `SecurityConfig` (`csrf.disable()`).
> For a learning/development project this is acceptable. In production, CSRF should be enabled for forms, or the app should use stateless JWT instead of sessions.

---

**Q: How do you protect admin routes?**

> Two layers of protection:
>
> **Layer 1 — Route level:** The `/forgot-password` and public pages are permitted without login. Everything else requires authentication (`.anyRequest().authenticated()`).
>
> **Layer 2 — Controller level:** Even if an authenticated user manually visits `/admin`, the `AdminController` checks:
> ```java
> if (!ADMIN_EMAIL.equals(userDetails.getUsername())) {
>     return "redirect:/dashboard";
> }
> ```
> Only `harshad@gmail.com` can access the admin panel. Anyone else gets redirected to their dashboard.

---

**Q: Is your app vulnerable to SQL injection?**

> No. VaultHub uses **Spring Data JPA with Hibernate**, which uses parameterized queries automatically.
>
> Instead of building SQL strings like:
> ```sql
> "SELECT * FROM users WHERE email = '" + email + "'"  // ❌ dangerous
> ```
> JPA uses:
> ```java
> userRepository.findByEmail(email)  // ✅ parameterized internally
> ```
> The email value is never concatenated into the SQL — it is passed as a parameter, so injection is not possible.

---

## 5. Database Questions

---

**Q: How many databases does VaultHub use?**

> Two separate databases in the same MySQL instance:
> - `vaulthub_auth` — stores the `users` table
> - `vaulthub_file` — stores `files`, `chunk_records`, `file_shares`, `activity_logs`
>
> Separating them follows microservices best practice — each service owns its own data.

---

**Q: What is JPA / Hibernate?**

> JPA is a standard Java API for talking to databases.
> Hibernate is the most popular implementation of JPA.
>
> Instead of writing raw SQL, I define Java classes (entities) and Hibernate automatically creates the tables and translates Java operations into SQL.
>
> Example:
> ```java
> userRepository.save(user);   // Hibernate → INSERT INTO users ...
> userRepository.findByEmail(email);  // Hibernate → SELECT * FROM users WHERE email = ?
> ```

---

**Q: What is `ddl-auto: update`? Is it safe?**

> `ddl-auto: update` tells Hibernate to automatically update the database schema when the app starts — adding new columns if you added them to your entity class.
>
> It is convenient for development but **not safe for production**. In production you should use `ddl-auto: validate` (just check the schema matches, don't change anything) and use proper database migration tools like Flyway or Liquibase.

---

**Q: What is the relationship between FileRecord and ChunkRecord?**

> It's a **one-to-many** relationship:
> - One `FileRecord` → many `ChunkRecord`s
> - A 35MB file creates 1 FileRecord and 4 ChunkRecords (4 × 10MB chunks, last one smaller)
>
> The ChunkRecord stores the actual disk path for each chunk:
> ```
> FileRecord  id=42,  fileName="report.pdf",  totalChunks=4
>   ChunkRecord  fileId=42, index=0, path="C:/vault-storage/abc_0.chunk"
>   ChunkRecord  fileId=42, index=1, path="C:/vault-storage/abc_1.chunk"
>   ChunkRecord  fileId=42, index=2, path="C:/vault-storage/abc_2.chunk"
>   ChunkRecord  fileId=42, index=3, path="C:/vault-storage/abc_3.chunk"
> ```

---

**Q: How does user ID work across the two services?**

> The Auth service uses a MySQL auto-generated `id` column for users.
> The File service needs to identify the owner of a file without directly querying the Auth database.
>
> The solution used is `email.hashCode()` — a numeric hash of the user's email:
> ```java
> long ownerId = (long) userDetails.getUsername().hashCode();
> ```
> Both services compute this the same way, so they agree on who owns which files without sharing a database.
>
> A better production approach would be to pass the real user ID via JWT claims.

---

## 6. File Upload & Storage Questions

---

**Q: How does file upload work in VaultHub?**

> When a user uploads a file:
> 1. The file arrives as a multipart HTTP request (max 500MB)
> 2. A `FileRecord` is saved in the database with status `UPLOADING`
> 3. The file is read in 10MB chunks — never fully loaded into RAM
> 4. Each 10MB chunk is written to disk as a `.chunk` file
> 5. A `ChunkRecord` is saved in the database for each chunk
> 6. After all chunks are saved, the status changes to `READY`

---

**Q: Why split files into chunks? Why not save the whole file at once?**

> If you load a 300MB file fully into Java memory (RAM), you use 300MB of RAM for one request. With 10 users uploading simultaneously that's 3GB of RAM — the server would crash.
>
> By reading 10MB at a time (streaming), the file goes directly from the HTTP request to the disk without ever fully existing in RAM. Memory usage stays constant no matter how large the file is.

---

**Q: How does file download work?**

> When a user downloads a file:
> 1. The server loads all ChunkRecords for that file, ordered by index
> 2. It opens each `.chunk` file from disk in sequence
> 3. It streams the bytes directly to the HTTP response
> 4. The browser receives it as a continuous file download
>
> Again, the file is never fully loaded into RAM — it is streamed chunk by chunk.

---

**Q: What is MIME type? Why do you store it?**

> MIME type tells the browser what kind of file it is receiving.
> Examples: `application/pdf`, `image/png`, `video/mp4`, `text/plain`
>
> VaultHub stores the MIME type when uploading so that:
> - The file viewer knows whether to show a PDF viewer, image, or video player
> - The download response sets the correct `Content-Type` header so the browser handles it properly

---

**Q: What happens if an upload fails halfway through?**

> The `FileRecord` stays in `UPLOADING` status — it never reaches `READY`.
> Files with `UPLOADING` status are not shown to the user in their file list.
> The partial `.chunk` files stay on disk but are never served.
>
> A production improvement would be a scheduled cleanup job that deletes stuck `UPLOADING` records and their orphaned chunk files after a timeout.

---

**Q: Where are files stored physically?**

> Files are stored on the local disk of the machine running the File service:
> ```
> C:/vault-storage/{fileUuid}_0.chunk
> C:/vault-storage/{fileUuid}_1.chunk
> ...
> ```
> The path is configurable in `application.yml`:
> ```yaml
> storage:
>   path: C:/vault-storage
>   chunk-size-bytes: 10485760   # 10MB
> ```

---

## 7. Redis & Session Questions

---

**Q: What is Redis? Why use it for sessions?**

> Redis is an in-memory database — it stores data in RAM, making it extremely fast to read and write.
>
> I use Redis for sessions because:
> - The Auth service (8081) and File service (8082) are separate programs
> - If sessions were stored inside the Auth service's memory, the File service would not know about them
> - By storing sessions in Redis (a shared external store), both services can read the same session using the same cookie
>
> This is called a **shared session store**.

---

**Q: What is Upstash? Why not run Redis locally?**

> Upstash is a cloud Redis service — it gives you a Redis database accessible over the internet without installing anything.
>
> I used it to avoid setting up Redis locally, which requires extra installation steps. The connection is secured with SSL and password authentication.

---

**Q: What is stored in the Redis session?**

> When a user logs in, the session stores:
> - Spring Security's authentication object (username, roles)
> - `fullName` — user's display name
> - `vaultId` — their unique vault identifier
> - `isAdmin` — whether they have admin access
>
> TTL (time to live) is 24 hours — after that the session expires and the user must log in again.

---

**Q: What happens if Redis goes down?**

> All logged-in users would be logged out immediately — their sessions exist only in Redis.
> Any request requiring authentication would be redirected to the login page.
>
> For production, Redis should have replication (a backup Redis instance) so if one goes down, the other takes over. Upstash handles this automatically.

---

## 8. Email & OTP Questions

---

**Q: How does the forgot password feature work?**

> It works in 3 steps:
> 1. User enters their email → server finds them in the database and sends a 6-digit OTP to that email
> 2. User enters the OTP → server checks if it matches, is not expired, and has not exceeded 5 wrong attempts
> 3. If verified → user sets a new password → it's BCrypt hashed and saved to the database

---

**Q: How is the OTP generated?**

> Using `SecureRandom` — Java's cryptographically secure random number generator:
> ```java
> String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
> ```
> This gives a 6-digit number like `048291` (zero-padded).
>
> `SecureRandom` is used instead of regular `Random` because regular Random is predictable — an attacker could potentially guess the next value. `SecureRandom` is not predictable.

---

**Q: Where is the OTP stored?**

> In a `ConcurrentHashMap` in memory (inside the `OtpService` bean):
> ```java
> Map<String, OtpEntry> store = new ConcurrentHashMap<>();
> // store.put(email, { otp, expiryTime, attemptCount })
> ```
> It is not stored in the database — OTPs are temporary and there is no need to persist them.
>
> `ConcurrentHashMap` is used because multiple requests could access the map at the same time — it is thread-safe.

---

**Q: What security measures are on the OTP?**

> Four protections:
> 1. **10 minute expiry** — OTP automatically becomes invalid after 10 minutes
> 2. **5 attempt limit** — after 5 wrong attempts the user is locked out and must request a new OTP
> 3. **Single use** — once the correct OTP is entered, it is immediately deleted from the map
> 4. **Email delivery** — only someone with access to that email inbox can see the OTP

---

**Q: What is a Gmail App Password? Why use it?**

> Google does not allow normal Gmail passwords to be used by third-party apps for security reasons.
> An App Password is a special 16-character password that Google generates just for one specific app.
>
> You create it in Google Account → Security → App Passwords.
> It bypasses Google's "less secure app" block and lets Spring Boot send emails through Gmail SMTP.

---

**Q: What is SMTP?**

> SMTP (Simple Mail Transfer Protocol) is the standard protocol for sending emails.
> It works like a postal service — you connect to an SMTP server, authenticate, give it the message, and it delivers it to the recipient's mail server.
>
> VaultHub connects to `smtp.gmail.com` on port 587, authenticates with the App Password, and sends the OTP email.

---

## 9. API & Gateway Questions

---

**Q: What is an API? What APIs does VaultHub have?**

> An API (Application Programming Interface) is a way for two programs to communicate.
> In web development, an API is usually a URL that returns data (typically JSON).
>
> VaultHub's internal APIs:
> - `GET /api/files/stats?ownerId=x` — returns file count and storage used
> - `GET /api/files/analytics?ownerId=x` — returns chart data for the dashboard
> - `GET /api/share/lookup?email=x` — checks if an email belongs to a registered user
> - `GET /api/admin/user-stats` — returns stats for all users (admin only)

---

**Q: What is the difference between a REST API and a webpage?**

> A webpage returns HTML — a full page that the browser renders visually.
> A REST API returns data (JSON) — no HTML, just raw data that code can use.
>
> In VaultHub, most routes return HTML pages (rendered by Thymeleaf).
> The `/api/**` routes return JSON — used by JavaScript in the browser or by other services.

---

**Q: What is `@RestController` vs `@Controller`?**

> `@Controller` — returns the name of an HTML template to render. Used for pages.
> `@RestController` — returns data directly as JSON. Used for APIs.
>
> In VaultHub, `FileController` uses `@Controller` (returns HTML pages) and `FileApiController` uses `@RestController` (returns JSON data).

---

**Q: What does the Gateway's X-Forwarded header do?**

> When the browser logs in through the Gateway (port 8080), the Auth service (port 8081) handles the request. After login, Spring Security redirects the user.
>
> Without X-Forwarded headers, Spring Security thinks the redirect should go to `localhost:8081/dashboard` — which the user cannot access directly.
>
> With `X-Forwarded-Host: localhost:8080`, Spring Security knows the real host is port 8080 and redirects to `localhost:8080/dashboard` — which goes through the Gateway correctly.

---

## 10. Design Decision Questions

---

**Q: Why did you use sessions instead of JWT for user authentication?**

> Sessions with Redis are simpler to implement for a server-rendered (Thymeleaf) web app.
> JWT is better for stateless REST APIs or mobile apps.
>
> Since VaultHub serves HTML pages (not a React/Vue frontend), sessions are the natural fit.
> JWT is prepared in the codebase for future inter-service communication.

---

**Q: Why does the file service use `email.hashCode()` as ownerId instead of the real user ID?**

> The Auth service and File service have separate databases.
> The File service cannot query the Auth database directly (that would break microservice isolation).
>
> `email.hashCode()` gives a consistent number that both services can calculate independently from the same email string — no cross-database call needed.
>
> The limitation is that `hashCode()` can theoretically produce the same number for two different emails (hash collision), though this is extremely rare in practice. A better solution would be to pass the real user ID via JWT.

---

**Q: Why store chunk files on local disk instead of a cloud storage like S3?**

> For this project, local disk is simpler — no cloud account needed, no extra cost, works offline.
>
> The code is designed so switching to S3 would only require changing `FileService` — everything else stays the same. The storage path is already configurable in `application.yml`.
>
> In production, cloud storage (AWS S3, Google Cloud Storage) would be better because local disk is lost if the server crashes.

---

**Q: Why is Eureka disabled?**

> Eureka is a service registry — services register themselves and find each other by name.
> For this project running locally, hardcoded `localhost:8081` and `localhost:8082` are simpler.
>
> Eureka becomes valuable when:
> - Services run on different machines
> - Multiple instances of a service run simultaneously (load balancing)
> - IP addresses change dynamically (like in Kubernetes)
>
> It's kept in the project as groundwork for future scaling.

---

**Q: What would you improve if this were a production application?**

> Several things:
> 1. **Replace `email.hashCode()` with JWT** for passing user identity between services
> 2. **Use S3 or similar** for file storage instead of local disk
> 3. **Enable CSRF protection** in Spring Security
> 4. **Use Flyway** for database migrations instead of `ddl-auto: update`
> 5. **Add rate limiting** in the Gateway to prevent abuse
> 6. **Use environment variables** for all passwords and secrets — not hardcoded in yml files
> 7. **Add a cleanup job** to delete stuck `UPLOADING` files
> 8. **Move OTP storage to Redis** so it survives server restarts
> 9. **Enable Eureka** for proper service discovery if deploying to multiple servers
> 10. **Add HTTPS** (SSL certificate) so all traffic is encrypted

---

## 11. Tricky / Deep Questions

---

**Q: Two users have the same password. Are their database hashes the same?**

> No. BCrypt automatically generates a random **salt** for each password and includes it in the hash.
>
> So `"password123"` for user A and `"password123"` for user B produce completely different hashes:
> ```
> User A: $2a$10$xK9mN3pQrS...
> User B: $2a$10$7Tz2aLmKpW...
> ```
> This means even if an attacker sees the database, they cannot tell two users have the same password, and they cannot use a precomputed table of common password hashes.

---

**Q: What happens to active sessions when a user resets their password?**

> Currently, active sessions are **not invalidated** after a password reset.
> If someone was logged in on another device, they stay logged in even after the password changed.
>
> The proper fix (which is a known improvement) is to invalidate all sessions for that user in Redis after a password reset. This can be done by finding all session keys for that user and deleting them.

---

**Q: Can two users share the same file, and what happens when the owner deletes it?**

> Yes — one file can be shared with multiple users via multiple `FileShare` records.
>
> Currently, if the owner deletes a file, the `FileRecord` status changes to `DELETED` and the chunk files are removed from disk. The `FileShare` records still exist in the database but the file is gone — the recipient would see a broken entry.
>
> The proper fix is to also delete or deactivate all `FileShare` records when a file is deleted (cascading logic).

---

**Q: Is the OTP stored securely?**

> The OTP is stored in plain text in a `ConcurrentHashMap` in memory.
> Since it is in server memory (not a database or logs), it is not directly accessible to a database attacker.
>
> However, a more secure approach would be to hash the OTP before storing it — just like passwords. Then when the user enters the OTP, you hash what they entered and compare hashes.

---

**Q: What is a race condition? Could it happen in VaultHub?**

> A race condition is when two requests happen at the same time and interfere with each other — causing unexpected results.
>
> Possible example in VaultHub: Two requests try to increment the OTP attempt counter at the same time — they both read `attempts = 2`, both set `attempts = 3`, resulting in only one increment instead of two.
>
> I used `ConcurrentHashMap` which is thread-safe for put/get operations. For production, atomic operations or database-level locking would be more robust.

---

**Q: What is the difference between `@GetMapping` and `@PostMapping`?**

> They correspond to HTTP methods:
> - `@GetMapping` — handles GET requests. Used for loading pages (no data changes).
> - `@PostMapping` — handles POST requests. Used for submitting forms (creates or changes data).
>
> Example in VaultHub:
> ```java
> @GetMapping("/forgot-password")   // Show the forgot password page
> @PostMapping("/forgot-password/send-otp")  // Process the email submission
> ```

---

**Q: What is `@Transactional` and what happens without it?**

> Without `@Transactional`, if you save a `FileRecord` and then the app crashes before saving all `ChunkRecord`s, you'd have a `FileRecord` in the database pointing to chunks that don't exist.
>
> With `@Transactional`, all database operations in that method are wrapped in one transaction — if anything fails, all of them are rolled back. The database stays consistent.

---

**Q: Why is there a `vaulthub-notify` service that isn't fully built?**

> It shows architectural thinking — separating notification logic from business logic.
> Currently, OTP emails are sent directly from the Auth service.
>
> In a proper design, the Auth service would publish an event ("send OTP to this email") and the Notify service would handle the actual sending. This way, if you want to add push notifications or SMS later, you only change the Notify service — not the Auth service.

---

*Prepared from the actual VaultHub source code — every answer reflects how the project is actually built.*
