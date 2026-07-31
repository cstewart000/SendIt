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
| `star-5.dxf` | Five-pointed star |
| `l-shape.dxf` | Simple L profile |
| `t-shape.dxf` | Simple T profile |
| `rect-with-hole.dxf` | Rectangle with internal rectangular cutout |
| `l-bracket.dxf` | L-shape with sharp internal corner (dog-bone) |
| `u-channel.dxf` | U-channel, two internal corners |
| `t-slot-plate.dxf` | T-slot notch, multiple sharp insides |
| `picture-frame.dxf` | Outer + inner window (frame cutout) |
| `corner-box.dxf` | Panel with L-shaped pocket |
| `nest-side-a.dxf` / `nest-shelf.dxf` / `nest-cleat.dxf` | Multi-part nesting kit |
| `bracket.dxf` | Simple closed plate + holes |
| `demo-r2000.dwg` | Sample AutoCAD R2000 DWG (native parse) |

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
Payments, shipping, pocketing/drill CAM, and public API are out of scope.
DWG upload is supported (R2000–R2018 best-effort via native parse); complex entities may need DXF export.

## Manufacturing notes (current)
- Nesting **fails closed**: if any piece cannot fit the sheet, nest returns `422` (no partial nest).
- Machine **kerf** (admin) sets minimum part-to-part gap and sheet edge margin (also at least tool Ø).
- G-code is **offline tool-radius offset** (G40), holes before outer profile, multi-pass depth, machine safe-Z.
- Job page: **toolpath preview** after nest (screws, tabs, cuts); toggle screws via checklist / click; download `.ngc` after approve.
- Machine admin: **kerf**, **fixing min distance** (default 10 mm), **tab width/height/count**.
- Hobby CNC tool kit is seeded (endmills, compression, ballnose, V-bits, surfacing, drills).
- Quotes use the same multi-pass path metrics as CAM (not single-pass contour length).
- Set `JWT_SECRET` in production/Railway — startup refuses the dev default when Railway env is detected.

## Backend tests
```bash
cd backend && ./gradlew test
```
