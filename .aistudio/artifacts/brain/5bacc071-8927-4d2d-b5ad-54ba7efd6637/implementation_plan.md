# Implementation Plan: OTT Hub Multi-Supabase Router & Master Control

## 1. Overview
The OTT Hub router provides complete multi-Supabase proxy routing, master API key authentication, credential fallbacks, and intelligent single-account data routing.

## 2. Key Features Implemented

### A. Permanent Master Key Deletion & Zero Resurrection
- **Removed Default Key Auto-Seeding**: Removed `INSERT ... ON CONFLICT` for `omni_live_master_default_2026` in `worker.js` (`ensureMasterDatabaseSchema`).
- **Eliminated Hardcoded Fallback Validation**: `validateMasterKey` in `worker.js` now verifies against database records and rejects deleted keys immediately.
- **Permanent Frontend Deletion**: `index.html` deletes records by both `master_key` and `key` in `supabase_master_keys`, logs deleted tokens to `ott_deleted_master_keys`, and no longer resurrects placeholder keys when the key list is empty.

### B. Master Key Account Management (`[+] Add / Manage Accounts`)
- **Interactive UI Button**: Each master key card features a `+ Add / Manage Accounts for this Key` button.
- **Account Selector Modal**: Allows viewing all available Supabase accounts with routing tags and easily checking/unchecking accounts to attach or detach.
- **Real-Time Persistence**: Updates are saved immediately via `PATCH /api/keys` and `/api/control` (`update_key_accounts`), updating both frontend state and remote database schemas.

### C. Single-Account Write Routing & Smart Read Tracking
- **Single-Account Target Writes**: Write actions (`insert`, `write`, `update`, `delete`) never fan-out to all accounts. The router selects **exactly one account** (prioritizing `target_account_tag` / `x-account-tag`, or falling back to the key's primary account).
- **Routing Indexing**: Every insert logs the record ID and account destination in both the in-memory cache and `data_routing_log`.
- **Intelligent Read & Locate**:
  - `read` and `query` actions check the routing index to jump directly to the account that actually stores the record.
  - New `locate` / `find_record` action allows querying where any record is stored.
  - All write and read responses include `stored_in_account: { id, name, routing_tag, project_url }`.
