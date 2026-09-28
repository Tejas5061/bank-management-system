# Deploying the public demo

The live demo runs entirely on free tiers:

```
browser ──► Vercel (React build, kosh-bank.vercel.app)
               │  /api/*  (rewrite in vercel.json: a server-side proxy, not a redirect)
               ▼
            Render (Spring Boot Docker image, free web service, Singapore)
               │  JDBC over TLS
               ▼
            Aiven (MySQL 8, free plan)
```

Because Vercel proxies `/api`, the browser only ever talks to one origin. The refresh-token cookie stays first-party with `SameSite=Strict`, and there are no CORS preflights, exactly like the nginx setup in `docker-compose.yml`.

## The `demo` profile

The API runs with `SPRING_PROFILES_ACTIVE=prod,demo`. It keeps everything from `prod` (secure cookie, no default secrets, trusted-proxy handling, ECS-capable logging) and adds, in `application-demo.yml`:

| Setting | Why |
|---|---|
| Demo seed (`db/seed`) and `DemoResetConfig` | Drops every table and re-runs all migrations on startup. Render stops the app after 15 idle minutes, so each visitor after a quiet spell gets a clean bank with fresh dates, whatever the last visitor changed. |
| `DB_HOST` / `DB_PORT` / `DB_NAME` build a JDBC URL with `sslMode=REQUIRED` | Managed MySQL requires TLS; three plain values are easier to paste than a JDBC URL. `DB_URL` still wins if set. |
| `server.port: ${PORT}` | Render tells the app which port to bind. |
| Pool of 5, 40 Tomcat threads | Fits the free database's connection limit and the 512 MB instance. |
| Mail off | Free hosts block outbound SMTP. In-app notifications still work. |
| Swagger on | So reviewers can explore the API. |

`JDK_JAVA_OPTIONS` in `render.yaml` sizes the JVM for 512 MB and 0.1 CPU (serial GC, C1-only JIT). The Dockerfile also builds a **Class Data Sharing** archive: it starts the app once at build time without a database and archives the loaded classes, which cut startup CPU by about 25% in local measurements (12.4 s to 9.4 s of CPU time). On a 0.1-CPU instance, CPU time is most of the cold start.

## One-time setup

### 1. Database: Aiven for MySQL (free plan)

1. Sign up at [aiven.io](https://aiven.io) (no card needed) and create a service: **MySQL**, **Free plan**. Pick a region close to Singapore if one is offered.
2. When it's running, open the service's **Overview** page and note **Host**, **Port** and **Password** (user `avnadmin`, database `defaultdb`).

Aiven may power off a free service after a long spell with no activity. It emails first, and you can power it back on from the console.

### 2. API: Render (free web service)

1. Sign up at [render.com](https://render.com) with GitHub.
2. **New → Blueprint**, pick this repository. Render reads `render.yaml` and asks for `DB_HOST`, `DB_PORT` and `DB_PASSWORD`: paste the Aiven values. `JWT_SECRET` is generated for you.
3. Apply. The first build takes several minutes. When the service is live, `https://kosh-bank-api.onrender.com/actuator/health` returns `{"status":"UP"}`.

If Render gives the service a different URL (the name was taken), update the `/api` rewrite destination in `vercel.json`.

Later deploys are automatic: `autoDeployTrigger: checksPass` redeploys after a push to `main` touches `backend/` and GitHub Actions CI passes.

### 3. Frontend: Vercel

The `kosh-bank` Vercel project is connected to this repository. Every push to `main` that touches `frontend/` or `vercel.json` deploys to production (`ignoreCommand` skips the rest). Root `vercel.json` holds the build commands, the `/api` rewrite, security headers and long-lived caching for hashed assets. To deploy by hand from the repository root:

```bash
vercel deploy --prod
```

## Cold starts

The free Render instance sleeps after 15 minutes without traffic, and waking it (container start plus Spring Boot on 0.1 CPU) takes a minute or two. In demo builds (`VITE_DEMO_MODE=true`) the frontend pings `/api/v1/public/branches` on load and, if nothing has answered after 3 seconds, explains the wait on the sign-in page and the session spinner (`lib/wakeup.ts`, `components/WakeNotice.tsx`).
