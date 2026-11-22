# Windows desktop package (planned, not implemented)

This folder is a placeholder so a Windows `.exe` installer can be added later
**without changing the Spring Boot backend**.

## The idea in one sentence

Electron is only a window. It opens the same BackTrack website that a browser
opens, so the backend, PostgreSQL database, login, sessions, CSRF protection
and every business rule stay exactly as they are.

```
Windows .exe (Electron window)
        │  HTTPS
        ▼
Spring Boot + PostgreSQL   <-- unchanged, one server, one database
        ▲
        │  HTTPS
Android / Chrome PWA
```

## What the backend already provides for this

- Configurable port and database through environment variables
  (`PORT`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`).
- The PWA manifest, icons and service worker are plain static files, so a
  desktop window can load them from the same server.
- Every page and asset uses root-relative URLs (`/css/...`, `/icons/...`), so
  nothing depends on the host name or a reverse proxy.
- Specification-compliant sessions and CSRF tokens, unchanged by packaging.

## What an Electron wrapper would contain (for later)

1. `desktop/package.json` with `electron` and `electron-builder`.
2. `desktop/main.js`:
   - `loadURL(process.env.BACKTRACK_URL || "https://your-backtrack-server")`
   - `nodeIntegration: false`, `contextIsolation: true` (no Node access in the
     page, because the page is the real web application).
   - External links open in the system browser.
   - Remember window size/position.
3. `electron-builder` target `nsis` to produce `BackTrack-Setup.exe`.

Because the window loads the **remote** URL, no CORS configuration and no
duplicated business logic are needed. The desktop app is therefore always in
sync with the server, and users still log in normally.

## Recommendation

Ship the PWA first (Chrome/Edge → "Install BackTrack"). It installs on Windows
and Android from the same build. Add the Electron installer only if you need a
classic `.exe`, auto-start on login, or distribution to users who cannot use a
browser.
