# QuickBite — Real-use canteen ordering website

## Features
- Mobile-friendly customer menu
- Search and food categories
- Cart with quantity controls
- Server-side price calculation
- Checkout with name, roll number, phone, pickup time and note
- Unique order token (QB1001, QB1002, ...)
- Customer order tracking without exposing the staff dashboard
- Staff PIN protected dashboard
- Staff can move orders through New → Preparing → Ready → Collected
- Orders persist in `orders.tsv` across restarts
- No external Java libraries required

## Run
1. Install Java 11+.
2. Keep `Server.java` and the `public` folder together.
3. Open a terminal in this folder.
4. Run:
   `java Server.java`
5. Open:
   `http://localhost:8080`

## Environment variables
Windows PowerShell:
`$env:PORT="8080"`
`$env:STAFF_PIN="change-this-pin"`
`java Server.java`

Linux/macOS:
`PORT=8080 STAFF_PIN=change-this-pin java Server.java`

## Important before public deployment
This version is suitable for a college/campus deployment behind a trusted network or reverse proxy, but for an internet-facing commercial service add HTTPS, proper staff accounts/password hashing, a database, rate limiting, backups, CSRF protection, and a restricted CORS policy.

## Food images
All menu items include local SVG food illustrations in `public/images`, so the menu does not depend on external image hosting.
