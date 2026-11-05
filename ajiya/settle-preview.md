<!-- Written by ajiya. Do not edit by hand: use ajiya commands. -->
# UD5: Offline settle-up preview

Goal: Settle-up preview runs offline on the cached ledger and reconciles with the server

| ID | App | Ticket | Done when | Depends | Tags | Status |
|---|---|---|---|---|---|---|
| RM-0030 | android | Port debt-simplification to Kotlin with unit tests matching server settle_up | Kotlin tests produce identical results to settle_up for shared fixtures | RM-0010 | - | 🟥 Pending |
| RM-0031 | android | Offline settle-up preview from Room ledger, marked provisional, reconciled on sync | In airplane mode Settle-up shows provisional payments; after reconnect it matches server | RM-0016, RM-0030 | - | 🟥 Pending |
