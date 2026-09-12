# Multyfi -> Kotak Neo Approval Assistant v2.2

Android Java app that listens for Multyfi advisory notifications and prepares Kotak Neo NSE MIS intraday orders.

## Entry flow
1. Capture Multyfi BUY/SELL alert.
2. Parse symbol, advisory entry/range, target and stop-loss.
3. Resolve Kotak Neo NSE instrument and fetch live LTP.
4. Fetch Kotak Neo RMS limits.
5. Effective capital = lower of the user-entered capital cap and Kotak live available funds.
6. Calculate quantity from effective capital, utilization %, and prepared execution price.
7. Show APPROVE & SEND.
8. On the approval tap, re-check open MIS position, latest LTP and live available funds; re-run price movement logic and recalculate quantity before sending.
9. Send only an NSE MIS order.

### Price movement protection
- BUY inside range + tolerance -> MKT.
- BUY above allowed range -> L at advisory high (do not chase).
- BUY materially below range -> block/manual review.
- SELL inside range + tolerance -> MKT.
- SELL below allowed range -> L at advisory low.
- SELL materially above range -> block/manual review.

## Exit flow
For exit/book-profit/cost-to-cost/reduce-loss alerts, the app checks the actual Kotak Neo MIS position. If already closed, no order is sent. If partly open, only remaining quantity is used. Active duplicate exit orders are blocked. EXIT NOW & SEND is still one explicit user approval.

## Safety defaults
- MIS only.
- NSE equity only.
- Live execution OFF by default.
- Test tickets are always dry-run.
- TOTP/MPIN are not persisted by the app.
- Live Kotak order APIs require the whitelisted static-IP setup required by Kotak Neo.

## Build
Push this project to GitHub. `.github/workflows/build-apk.yml` builds `app-debug.apk` and uploads artifact `MultyfiKotakNeoApproval-debug-apk`.
