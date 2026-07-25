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

## Railway notes
- Add PostgreSQL plugin; set `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`
- Set `JWT_SECRET`, `PORT`, `STORAGE_PATH` (or migrate to S3-compatible storage)
- Deploy backend + static frontend (or separate frontend service with `VITE_API_BASE_URL`)

## Phase 1 limits
DWG native parse, payments, shipping, pocketing/drill CAM, and public API are out of scope.
