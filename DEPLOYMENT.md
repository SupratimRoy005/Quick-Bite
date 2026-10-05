# QuickBite Public Deployment

This build is prepared for a public HTTPS deployment using Docker + Render.

## What is included
- Java 21 Docker image
- Render Blueprint (`render.yaml`)
- Persistent `/data/orders.tsv` storage
- `/health` health endpoint
- Production security headers
- Required `STAFF_PIN` secret (no unsafe default)
- Local SVG food images
- Existing customer ordering, tracking, and staff dashboard

## Deploy
1. Create a Git repository and upload this entire QuickBite folder.
2. Push the repository to GitHub.
3. In Render, create a new Blueprint from the repository.
4. When prompted, set `STAFF_PIN` to a private PIN that only canteen staff know.
5. Deploy the service.
6. Render gives the service a public `https://...onrender.com` address.
7. Open that address on any phone or computer.

The service listens on Render's `PORT` and stores orders under `/data`, which is attached to a persistent disk.

## Important
- Do not put `STAFF_PIN` into GitHub, Java source code, or screenshots.
- The attached persistent disk is intended for this single-instance build. If you later need multiple servers, move orders to a managed database.
- Online payments are not enabled in this build.
- Before commercial use, add proper staff accounts, audit logs, rate limiting, backups, and a managed database.
