# SendIt (Phase 1)

Hobbyist CNC timber web app: upload DXF → guided repair → nest → quote → LinuxCNC G-code.

## Stack
- Backend: Java 17, Spring Boot 4, JWT auth, SpringDoc OpenAPI, PostgreSQL
- Frontend: React + TypeScript + Vite, Canvas 2D + Three.js
- Local files under `./storage` (backend working directory)

## Quick start

```bash
# 1) Database (host port 5435)
docker compose up -d

# 2) API (port 8081)
cd backend && ./gradlew bootRun

# 3) SPA (port 5173)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173

### Seed users
| Email | Password | Role |
|-------|----------|------|
| hobby@sendit.local | hobby12345 | USER |
| admin@sendit.local | admin12345 | ADMIN |

### Demo DXFs (`samples/`)
| File | Purpose |
|------|---------|
| `l-bracket.dxf` | L-shape with sharp internal corner (dog-bone) |
| `u-channel.dxf` | U-channel, two internal corners |
| `t-slot-plate.dxf` | T-slot notch, multiple sharp insides |
| `picture-frame.dxf` | Outer + inner window (frame cutout) |
| `corner-box.dxf` | Panel with L-shaped pocket |
| `nest-side-a.dxf` / `nest-shelf.dxf` / `nest-cleat.dxf` | Multi-part nesting kit |
| `bracket.dxf` | Simple closed plate + holes |

### API docs
http://localhost:8081/swagger-ui.html

## Happy path
1. Sign in as hobby user
2. Upload `samples/bracket.dxf`
3. Confirm repair actions → machinability / optional dog-bones
4. Create job → nest → lock → quote → approve
5. Download `.ngc` G-code + setup sheet
6. Admin can edit pricing and advance job status

## Production (Railway)
- App: https://frontend-production-17e71.up.railway.app
- API: https://backend-production-df5b.up.railway.app
- Project services: `frontend` (Nginx + SPA), `backend` (Spring Boot), `Postgres`
- Frontend proxies `/api` to backend over Railway private networking
- Redeploy from CLI:
  ```bash
  railway up ./backend --path-as-root --service backend --ci -y
  railway up ./frontend --path-as-root --service frontend --ci -y
  ```

## Phase 1 limits
DWG native parse, payments, shipping, pocketing/drill CAM, and public API are out of scope.
