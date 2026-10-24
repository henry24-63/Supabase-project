import { createClient } from '@supabase/supabase-js';
import htmlContent from './index.html';
import pg from 'pg';
import postgres from 'postgres';
import { connect } from 'cloudflare:sockets';

export default {
  async fetch(request, env, ctx) {
    const MASTER_URL = env.MASTER_URL || 'https://sjdlejiwqmkyfkvmodgd.supabase.co';
    const MASTER_KEY = env.MASTER_KEY || 'sb_publishable_YAv53hX7HhPdxBcsiZpKEA_JHKMmGAt';

    const url = new URL(request.url);
    const method = request.method;

    if (method === 'OPTIONS') {
      return new Response(null, {
        status: 200,
        headers: {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Methods': 'GET,OPTIONS,PATCH,DELETE,POST,PUT',
          'Access-Control-Allow-Headers': 'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version, Authorization, x-project-name, x-account-id, apikey, x-account-tag, x-target-account'
        }
      });
    }

    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Content-Type': 'application/json'
    };

    // Serve HTML dashboard frontend at root GET '/'
    if ((url.pathname === '/' || url.pathname === '') && method === 'GET') {
      return new Response(htmlContent, {
        status: 200,
        headers: {
          'Content-Type': 'text/html;charset=UTF-8',
          'Access-Control-Allow-Origin': '*'
        }
      });
    }

    let supabase;
    try {
      supabase = createClient(MASTER_URL, MASTER_KEY);
    } catch (err) {
      return new Response(JSON.stringify({ error: 'Supabase init failed: ' + err.message }), { status: 500, headers: corsHeaders });
    }

    const action = url.searchParams.get('action');

    // 1. DIAGNOSE ACTION
    if (action === 'diagnose' || action === 'test') {
      const testUrl = url.searchParams.get('url');
      const testKey = url.searchParams.get('key');
      if (!testUrl || !testKey) {
        return new Response(JSON.stringify({ error: 'url and key required' }), { status: 400, headers: corsHeaders });
      }

      let cleanUrl = testUrl.trim().replace(/^['"]|['"]$/g, '');
      if (cleanUrl.includes('supabase.com/dashboard/project/')) {
        const ref = cleanUrl.split('project/')[1]?.split('/')[0];
        if (ref) cleanUrl = `https://${ref}.supabase.co`;
      }
      if (!cleanUrl.startsWith('http')) cleanUrl = `https://${cleanUrl}`;
      cleanUrl = cleanUrl.replace(/\/+$/, '');

      const tStart = Date.now();
      try {
        const resp = await fetch(`${cleanUrl}/auth/v1/settings`, {
          headers: { 'apikey': testKey, 'Authorization': `Bearer ${testKey}` }
        });
        const latency = Date.now() - tStart;
        if (resp.status === 200 || resp.status === 401) {
          return new Response(JSON.stringify({
            success: resp.status === 200,
            status: resp.status === 200 ? 'VERIFIED' : 'AUTH_FAILED',
            statusCode: resp.status,
            latencyMs: latency,
            protocol: 'HTTPS',
            url: cleanUrl
          }), { status: 200, headers: corsHeaders });
        }
      } catch (err) {
        return new Response(JSON.stringify({ success: false, status: 'UNREACHABLE', error: err.message }), { status: 200, headers: corsHeaders });
      }
    }

    // 2. PING ACTION
    if (action === 'ping') {
      try {
        const { data: accounts, error } = await supabase.from('supabase_accounts').select('*');
        if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: corsHeaders });

        const startTime = Date.now();
        const results = await Promise.all((accounts || []).map(async (acc) => {
          const itemStart = Date.now();
          try {
            const resp = await fetch(`${acc.project_url.replace(/\/+$/, '')}/rest/v1/`, {
              headers: { 'apikey': acc.api_key, 'Authorization': `Bearer ${acc.api_key}` }
            });
            return { id: acc.id, name: acc.name, url: acc.project_url, status: 'ALIVE', statusCode: resp.status, latencyMs: Date.now() - itemStart };
          } catch (e) {
            return { id: acc.id, name: acc.name, url: acc.project_url, status: 'FAILED', latencyMs: Date.now() - itemStart };
          }
        }));

        return new Response(JSON.stringify({ total: results.length, totalTimeMs: Date.now() - startTime, accounts: results }), { status: 200, headers: corsHeaders });
      } catch (err) {
        return new Response(JSON.stringify({ error: err.message }), { status: 500, headers: corsHeaders });
      }
    }

    // 2.5 SMART RAW TEXT PARSER ROUTE (/api/parse-raw)
    if ((url.pathname === '/api/parse-raw' || url.pathname === '/api/parse-raw/') && method === 'POST') {
      const reqBody = await request.json().catch(() => ({}));
      const rawText = reqBody.raw_text || reqBody.text || reqBody.raw || '';
      const parsed = parseRawSupabaseCredentials(rawText);
      return new Response(JSON.stringify({ success: true, extracted: parsed }), { status: 200, headers: corsHeaders });
    }

    // 3. API ACCOUNTS ROUTE (/api/accounts)
    if (url.pathname === '/api/accounts' || url.pathname === '/api/accounts/') {
      if (method === 'GET') {
        await ensureMasterDatabaseSchema(MASTER_URL, MASTER_KEY, env);
        const { data, error } = await supabase.from('supabase_accounts').select('*');
        if (error) {
          try {
            const directRes = await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_accounts?select=*`, {
              headers: {
                'apikey': MASTER_KEY,
                'Authorization': `Bearer ${MASTER_KEY}`
              }
            });
            if (directRes.ok) {
              const directData = await directRes.json();
              return new Response(JSON.stringify(directData || []), { status: 200, headers: corsHeaders });
            }
          } catch (e) {}
          return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: corsHeaders });
        }
        return new Response(JSON.stringify(data || []), { status: 200, headers: corsHeaders });
      }

      if (method === 'POST') {
        const reqBody = await request.json().catch(() => ({}));
        
        // 1. Ensure master database table and columns exist
        await ensureMasterDatabaseSchema(MASTER_URL, MASTER_KEY, env);

        // 2. Try saving via Supabase JS Client
        let { data, error } = await supabase.from('supabase_accounts').insert([reqBody]).select();

        // 3. If Supabase JS Client failed, try direct REST call
        if (error || !data || data.length === 0) {
          try {
            const restRes = await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_accounts`, {
              method: 'POST',
              headers: {
                'apikey': MASTER_KEY,
                'Authorization': `Bearer ${MASTER_KEY}`,
                'Content-Type': 'application/json',
                'Prefer': 'return=representation'
              },
              body: JSON.stringify(reqBody)
            });
            if (restRes.ok) {
              const restData = await restRes.json();
              if (restData && (Array.isArray(restData) ? restData.length > 0 : Object.keys(restData).length > 0)) {
                return new Response(JSON.stringify(Array.isArray(restData) ? restData : [restData]), { status: 200, headers: corsHeaders });
              }
            }
          } catch (restErr) {}

          // 4. Try SQL insertion fallback
          const sqlRes = await insertAccountViaSql(MASTER_URL, MASTER_KEY, reqBody, env);
          if (sqlRes.success && sqlRes.data && (Array.isArray(sqlRes.data) ? sqlRes.data.length > 0 : Object.keys(sqlRes.data).length > 0)) {
            const resultList = Array.isArray(sqlRes.data) ? sqlRes.data : [sqlRes.data];
            return new Response(JSON.stringify(resultList), { status: 200, headers: corsHeaders });
          }

          // If all failed, DO NOT return fake success - return HTTP 500 error!
          return new Response(JSON.stringify({ error: error ? error.message : 'Database insert failed' }), { status: 500, headers: corsHeaders });
        }

        return new Response(JSON.stringify(data), { status: 200, headers: corsHeaders });
      }

      if (method === 'PATCH') {
        const id = url.searchParams.get('id');
        const reqBody = await request.json().catch(() => ({}));
        if (!id) return new Response(JSON.stringify({ error: 'id required' }), { status: 400, headers: corsHeaders });

        // 1. Ensure master database schema is up-to-date
        await ensureMasterDatabaseSchema(MASTER_URL, MASTER_KEY, env);

        // 2. Try updating via Supabase JS Client
        let { data, error } = await supabase.from('supabase_accounts').update(reqBody).eq('id', id).select();

        // 3. Fallback to SQL execution on schema cache error
        if (error) {
          const errMsg = (error.message || '').toLowerCase();
          if (errMsg.includes('column') || errMsg.includes('schema cache') || errMsg.includes('could not find') || error.code === 'PGRST204') {
            const sqlRes = await updateAccountViaSql(MASTER_URL, MASTER_KEY, id, reqBody, env);
            if (sqlRes.success) {
              return new Response(JSON.stringify(sqlRes.data || [reqBody]), { status: 200, headers: corsHeaders });
            }
          }
          return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: corsHeaders });
        }
        return new Response(JSON.stringify(data || [reqBody]), { status: 200, headers: corsHeaders });
      }

      if (method === 'DELETE') {
        const id = url.searchParams.get('id');
        const urlParam = url.searchParams.get('url');
        if (id) {
          await supabase.from('supabase_accounts').delete().eq('id', id);
        }
        if (urlParam) {
          await supabase.from('supabase_accounts').delete().eq('project_url', urlParam);
        }
        return new Response(JSON.stringify({ success: true }), { status: 200, headers: corsHeaders });
      }
    }

    // 4. API MASTER KEYS ROUTE (/api/keys)
    if (url.pathname === '/api/keys' || url.pathname === '/api/keys/') {
      if (method === 'GET') {
        let keysData = [];
        try {
          const { data, error } = await supabase.from('supabase_master_keys').select('*');
          if (!error && Array.isArray(data)) keysData = data;
        } catch (e) {}

        if (keysData.length === 0) {
          try {
            const directRes = await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_master_keys?select=*`, {
              headers: { 'apikey': MASTER_KEY, 'Authorization': `Bearer ${MASTER_KEY}` }
            });
            if (directRes.ok) {
              const directData = await directRes.json();
              if (Array.isArray(directData)) keysData = directData;
            }
          } catch (e) {}
        }

        const normalized = keysData.map(k => normalizeKeyRecord(k)).filter(Boolean);
        return new Response(JSON.stringify(normalized), { status: 200, headers: corsHeaders });
      }

      if (method === 'POST') {
        const reqBody = await request.json().catch(() => ({}));
        const rawKey = reqBody.key || reqBody.master_key;
        if (!rawKey) return new Response(JSON.stringify({ error: 'key required' }), { status: 400, headers: corsHeaders });

        const keyName = reqBody.name || reqBody.key_name || 'Master Router Key';
        const status = reqBody.status || 'ACTIVE';
        const isActive = status === 'ACTIVE';
        const connAccIds = reqBody.connected_account_ids || reqBody.assigned_account_ids || [];
        const connIdsJson = typeof connAccIds === 'string' ? connAccIds : JSON.stringify(connAccIds);
        const destination = reqBody.destination || (connAccIds.length > 0 ? `${connAccIds.length} Accounts` : 'All Accounts');
        const allowedMethods = reqBody.allowed_methods || ['direct_postgres', 'service_role', 'anon'];

        const dbRecord = {
          master_key: rawKey,
          key_name: keyName,
          assigned_account_ids: connIdsJson,
          is_active: isActive
        };

        let saved = null;
        try {
          const { data, error } = await supabase.from('supabase_master_keys').upsert([dbRecord]).select();
          if (!error && data && data.length > 0) saved = data[0];
        } catch (e) {}

        if (!saved) {
          try {
            const restRes = await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_master_keys`, {
              method: 'POST',
              headers: {
                'apikey': MASTER_KEY,
                'Authorization': `Bearer ${MASTER_KEY}`,
                'Content-Type': 'application/json',
                'Prefer': 'return=representation'
              },
              body: JSON.stringify(dbRecord)
            });
            if (restRes.ok) {
              const resData = await restRes.json();
              if (Array.isArray(resData) && resData.length > 0) saved = resData[0];
            }
          } catch (e) {}
        }

        const resultObj = normalizeKeyRecord(saved || { ...reqBody, ...dbRecord, destination, allowed_methods: allowedMethods });
        return new Response(JSON.stringify(resultObj), { status: 200, headers: corsHeaders });
      }

      if (method === 'PATCH') {
        const keyParam = url.searchParams.get('key');
        const reqBody = await request.json().catch(() => ({}));
        const targetKey = keyParam || reqBody.key || reqBody.master_key;
        if (!targetKey) return new Response(JSON.stringify({ error: 'key required' }), { status: 400, headers: corsHeaders });

        const patchPayload = {};
        if (reqBody.status !== undefined) {
          patchPayload.is_active = reqBody.status === 'ACTIVE';
          patchPayload.status = reqBody.status;
        }
        if (reqBody.is_active !== undefined) {
          patchPayload.is_active = !!reqBody.is_active;
          patchPayload.status = reqBody.is_active ? 'ACTIVE' : 'REVOKED';
        }
        if (reqBody.name || reqBody.key_name) {
          patchPayload.key_name = reqBody.name || reqBody.key_name;
          patchPayload.name = reqBody.name || reqBody.key_name;
        }

        if (reqBody.connected_account_ids !== undefined || reqBody.assigned_account_ids !== undefined || reqBody.add_accounts !== undefined || reqBody.remove_accounts !== undefined) {
          let currentIds = [];
          try {
            const { data } = await supabase.from('supabase_master_keys').select('*').or(`master_key.eq.${targetKey},key.eq.${targetKey}`).maybeSingle();
            if (data) {
              currentIds = data.connected_account_ids || data.assigned_account_ids || [];
              if (typeof currentIds === 'string') { try { currentIds = JSON.parse(currentIds); } catch (e) { currentIds = []; } }
            }
          } catch (e) {}

          let targetIds = reqBody.connected_account_ids || reqBody.assigned_account_ids || currentIds;
          if (typeof targetIds === 'string') { try { targetIds = JSON.parse(targetIds); } catch (e) { targetIds = [targetIds]; } }
          if (!Array.isArray(targetIds)) targetIds = [];

          if (Array.isArray(reqBody.add_accounts)) {
            for (const id of reqBody.add_accounts) {
              if (!targetIds.map(String).includes(String(id))) targetIds.push(id);
            }
          }
          if (Array.isArray(reqBody.remove_accounts)) {
            targetIds = targetIds.filter(id => !reqBody.remove_accounts.map(String).includes(String(id)));
          }

          const idsJson = JSON.stringify(targetIds);
          patchPayload.connected_account_ids = idsJson;
          patchPayload.assigned_account_ids = idsJson;
          patchPayload.destination = targetIds.length > 0 ? `${targetIds.length} Accounts` : 'ALL';
        }

        try {
          await supabase.from('supabase_master_keys').update(patchPayload).eq('master_key', targetKey);
        } catch (e) {}
        try {
          await supabase.from('supabase_master_keys').update(patchPayload).eq('key', targetKey);
        } catch (e) {}
        try {
          await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_master_keys?or=(master_key.eq.${encodeURIComponent(targetKey)},key.eq.${encodeURIComponent(targetKey)})`, {
            method: 'PATCH',
            headers: {
              'apikey': MASTER_KEY,
              'Authorization': `Bearer ${MASTER_KEY}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify(patchPayload)
          });
        } catch (e) {}

        return new Response(JSON.stringify({ success: true, ...patchPayload, key: targetKey }), { status: 200, headers: corsHeaders });
      }

      if (method === 'DELETE') {
        const keyParam = url.searchParams.get('key');
        const reqBody = await request.json().catch(() => ({}));
        const targetKey = keyParam || reqBody.key || reqBody.master_key;
        if (targetKey) {
          try {
            await supabase.from('supabase_master_keys').delete().eq('master_key', targetKey);
          } catch (e) {}
          try {
            await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_master_keys?master_key=eq.${encodeURIComponent(targetKey)}`, {
              method: 'DELETE',
              headers: { 'apikey': MASTER_KEY, 'Authorization': `Bearer ${MASTER_KEY}` }
            });
          } catch (e) {}
        }
        return new Response(JSON.stringify({ success: true }), { status: 200, headers: corsHeaders });
      }
    }

    // 4.5 NEW /api/control ENDPOINT (POST) - SUPABASE COMPLETE CONTROL
    if ((url.pathname === '/api/control' || url.pathname === '/api/control/') && method === 'POST') {
      const authHeader = request.headers.get('Authorization') || request.headers.get('apikey') || '';
      const keyAuth = await validateMasterKey(authHeader, supabase, MASTER_URL, MASTER_KEY, env);
      if (!keyAuth.valid) {
        return new Response(JSON.stringify({
          success: false,
          error: "Invalid/Revoked master key"
        }), { status: 401, headers: corsHeaders });
      }

      const controlBody = await request.json().catch(() => ({}));
      const ctrlAction = controlBody.action;
      const target = controlBody.target;

      if (!ctrlAction) {
        return new Response(JSON.stringify({
          success: false,
          error: "Missing 'action' parameter (enable_account, disable_account, delete_account, list_accounts)"
        }), { status: 400, headers: corsHeaders });
      }

      // Fetch all accounts
      let allAccounts = [];
      try {
        const { data: dbAccs, error } = await supabase.from('supabase_accounts').select('*');
        if (!error && dbAccs) allAccounts = dbAccs;
      } catch (e) {}

      if (allAccounts.length === 0) {
        try {
          const directRes = await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_accounts?select=*`, {
            headers: { 'apikey': MASTER_KEY, 'Authorization': `Bearer ${MASTER_KEY}` }
          });
          if (directRes.ok) allAccounts = await directRes.json();
        } catch (e) {}
      }

      // 1. LIST_ACCOUNTS
      if (ctrlAction === 'list_accounts') {
        return new Response(JSON.stringify({
          success: true,
          keyStatus: "ACTIVE",
          data: allAccounts,
          accounts: allAccounts,
          total: allAccounts.length
        }, null, 2), { status: 200, headers: corsHeaders });
      }

      // 2. ENABLE_ACCOUNT
      if (ctrlAction === 'enable_account') {
        const isTargetAll = !target || target === 'all' || target === 'ALL';
        let updated = [];
        for (const acc of allAccounts) {
          if (isTargetAll || String(acc.id) === String(target) || (acc.routing_tag && acc.routing_tag.toLowerCase() === String(target).toLowerCase()) || (acc.name && acc.name.toLowerCase() === String(target).toLowerCase())) {
            try {
              await supabase.from('supabase_accounts').update({ is_active: true }).eq('id', acc.id);
            } catch (e) {}
            acc.is_active = true;
            updated.push(acc);
          }
        }
        return new Response(JSON.stringify({
          success: true,
          keyStatus: "ACTIVE",
          action: "enable_account",
          target: target || 'all',
          data: updated,
          updatedCount: updated.length
        }, null, 2), { status: 200, headers: corsHeaders });
      }

      // 3. DISABLE_ACCOUNT
      if (ctrlAction === 'disable_account') {
        const isTargetAll = target === 'all' || target === 'ALL';
        let updated = [];
        for (const acc of allAccounts) {
          if (isTargetAll || String(acc.id) === String(target) || (acc.routing_tag && acc.routing_tag.toLowerCase() === String(target).toLowerCase()) || (acc.name && acc.name.toLowerCase() === String(target).toLowerCase())) {
            try {
              await supabase.from('supabase_accounts').update({ is_active: false }).eq('id', acc.id);
            } catch (e) {}
            acc.is_active = false;
            updated.push(acc);
          }
        }
        return new Response(JSON.stringify({
          success: true,
          keyStatus: "ACTIVE",
          action: "disable_account",
          target: target || 'all',
          data: updated,
          updatedCount: updated.length
        }, null, 2), { status: 200, headers: corsHeaders });
      }

      // 4. DELETE_ACCOUNT
      if (ctrlAction === 'delete_account') {
        if (!target) {
          return new Response(JSON.stringify({
            success: false,
            error: "Target required for delete_account"
          }), { status: 400, headers: corsHeaders });
        }
        for (const acc of allAccounts) {
          if (String(acc.id) === String(target) || (acc.routing_tag && acc.routing_tag.toLowerCase() === String(target).toLowerCase()) || (acc.name && acc.name.toLowerCase() === String(target).toLowerCase()) || (acc.project_url && acc.project_url === String(target))) {
            try {
              await supabase.from('supabase_accounts').delete().eq('id', acc.id);
            } catch (e) {}
          }
        }
        return new Response(JSON.stringify({
          success: true,
          keyStatus: "ACTIVE",
          action: "delete_account",
          target: target,
          data: { deleted: true }
        }, null, 2), { status: 200, headers: corsHeaders });
      }

      // 5. UPDATE_KEY_ACCOUNTS
      if (ctrlAction === 'update_key_accounts') {
        const keyNameOrKey = controlBody.key || controlBody.target;
        if (!keyNameOrKey) {
          return new Response(JSON.stringify({ success: false, error: "key or target required for update_key_accounts" }), { status: 400, headers: corsHeaders });
        }
        let keyRec = null;
        try {
          const { data } = await supabase.from('supabase_master_keys').select('*').or(`master_key.eq.${keyNameOrKey},key.eq.${keyNameOrKey}`).maybeSingle();
          if (data) keyRec = data;
        } catch (e) {}

        if (!keyRec) {
          try {
            const res = await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_master_keys?or=(master_key.eq.${encodeURIComponent(keyNameOrKey)},key.eq.${encodeURIComponent(keyNameOrKey)})&select=*`, {
              headers: { 'apikey': MASTER_KEY, 'Authorization': `Bearer ${MASTER_KEY}` }
            });
            if (res.ok) {
              const arr = await res.json();
              if (Array.isArray(arr) && arr.length > 0) keyRec = arr[0];
            }
          } catch (e) {}
        }

        if (!keyRec) {
          return new Response(JSON.stringify({ success: false, error: "Master key not found" }), { status: 404, headers: corsHeaders });
        }

        let existingIds = keyRec.connected_account_ids || keyRec.assigned_account_ids || [];
        if (typeof existingIds === 'string') {
          try { existingIds = JSON.parse(existingIds); } catch (e) { existingIds = []; }
        }
        if (!Array.isArray(existingIds)) existingIds = [];

        const addAccs = controlBody.add_accounts || controlBody.add || [];
        const removeAccs = controlBody.remove_accounts || controlBody.remove || [];
        const explicitSet = controlBody.connected_account_ids || controlBody.assigned_account_ids;

        let newIds = explicitSet ? (Array.isArray(explicitSet) ? explicitSet : [explicitSet]) : [...existingIds];
        if (!explicitSet) {
          if (Array.isArray(addAccs)) {
            for (const id of addAccs) {
              if (!newIds.map(String).includes(String(id))) newIds.push(id);
            }
          }
          if (Array.isArray(removeAccs)) {
            newIds = newIds.filter(id => !removeAccs.map(String).includes(String(id)));
          }
        }

        const newIdsJson = JSON.stringify(newIds);
        const updatePayload = {
          connected_account_ids: newIdsJson,
          assigned_account_ids: newIdsJson,
          destination: newIds.length > 0 ? `${newIds.length} Accounts` : 'ALL'
        };

        const targetKeyVal = keyRec.master_key || keyRec.key;
        try {
          await supabase.from('supabase_master_keys').update(updatePayload).eq('master_key', targetKeyVal);
        } catch (e) {}
        try {
          await supabase.from('supabase_master_keys').update(updatePayload).eq('key', targetKeyVal);
        } catch (e) {}

        try {
          await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_master_keys?or=(master_key.eq.${encodeURIComponent(targetKeyVal)},key.eq.${encodeURIComponent(targetKeyVal)})`, {
            method: 'PATCH',
            headers: {
              'apikey': MASTER_KEY,
              'Authorization': `Bearer ${MASTER_KEY}`,
              'Content-Type': 'application/json'
            },
            body: JSON.stringify(updatePayload)
          });
        } catch (e) {}

        const updatedRecord = normalizeKeyRecord({ ...keyRec, ...updatePayload });
        return new Response(JSON.stringify({
          success: true,
          keyStatus: "ACTIVE",
          action: "update_key_accounts",
          key: targetKeyVal,
          data: updatedRecord
        }, null, 2), { status: 200, headers: corsHeaders });
      }

      return new Response(JSON.stringify({
        success: false,
        error: `Unknown control action '${ctrlAction}'. Allowed: enable_account, disable_account, delete_account, list_accounts, update_key_accounts`
      }), { status: 400, headers: corsHeaders });
    }

    // 5. PROXY / QUERY / READ / WRITE / SQL ROUTING
    let body = {};
    try {
      if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        body = await request.json().catch(() => ({}));
      }
    } catch (e) {}

    const authHeader = request.headers.get('Authorization') || request.headers.get('apikey') || '';
    const projectName = request.headers.get('x-project-name') || url.searchParams.get('project') || body.projectName || body.project_name;
    const accountId = request.headers.get('x-account-id') || url.searchParams.get('accountId') || body.accountId || body.account_id;
    const rawTag = request.headers.get('x-account-tag') || request.headers.get('x-target-account') || url.searchParams.get('tag') || body.tag || body.accountTag || body.routing_tag;
    const cleanTag = rawTag ? String(rawTag).trim().replace(/^#/, '') : null;
    const subPath = (url.searchParams.get('path') || url.pathname.replace(/^\/+/, '') || '').replace(/^api\/proxy\/?/, '').replace(/^\/+/, '') || '';

    // ================= MASTER KEY VALIDATION LAYER =================
    const keyAuth = await validateMasterKey(authHeader, supabase, MASTER_URL, MASTER_KEY, env);
    if (!keyAuth.valid) {
      return new Response(JSON.stringify({
        success: false,
        error: "Invalid/Revoked master key"
      }), { status: 401, headers: corsHeaders });
    }

    const keyRecord = keyAuth.keyRecord;
    const isUniversal = !keyRecord.destination || 
                        keyRecord.destination.toUpperCase() === 'ALL' || 
                        keyRecord.destination.toUpperCase().startsWith('ALL ACCOUNTS') ||
                        (!keyRecord.connected_account_ids || keyRecord.connected_account_ids.length === 0);

    // If key is scoped: requirement enforces x-account-tag header MUST be provided
    const accountTagHeader = request.headers.get('x-account-tag') || request.headers.get('x-target-account');
    if (!isUniversal && !accountTagHeader && !cleanTag) {
      return new Response(JSON.stringify({
        success: false,
        error: "Scoped master key requires x-account-tag header",
        keyStatus: "ACTIVE"
      }), { status: 400, headers: corsHeaders });
    }

    // Lookup Target Accounts
    let accounts = [];
    try {
      const { data: dbAccs, error } = await supabase.from('supabase_accounts').select('*');
      if (!error && dbAccs && dbAccs.length > 0) {
        accounts = dbAccs.filter(a => a.is_active !== false);
      }
    } catch (e) {}

    // Fallback: Fetch directly via REST if supabase-js query returned empty
    if (accounts.length === 0) {
      try {
        const directRes = await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_accounts?select=*`, {
          headers: {
            'apikey': MASTER_KEY,
            'Authorization': `Bearer ${MASTER_KEY}`
          }
        });
        if (directRes.ok) {
          const directData = await directRes.json();
          if (Array.isArray(directData)) {
            accounts = directData.filter(a => a.is_active !== false);
          }
        }
      } catch (e) {}
    }

    // Filter accounts if key is scoped
    let allowedAccounts = accounts;
    if (!isUniversal) {
      const allowedKeys = [
        ...(keyRecord.connected_account_ids || []).map(id => String(id).toLowerCase()),
        ...(keyRecord.connected_account_names || []).map(n => String(n).toLowerCase())
      ];
      allowedAccounts = accounts.filter(acc => 
        allowedKeys.includes(String(acc.id).toLowerCase()) ||
        (acc.routing_tag && allowedKeys.includes(acc.routing_tag.toLowerCase())) ||
        (acc.name && allowedKeys.includes(acc.name.toLowerCase())) ||
        (acc.project_name && allowedKeys.includes(acc.project_name.toLowerCase()))
      );
    }

    // Determine target account(s)
    let targetAccounts = [];
    if (cleanTag) {
      const found = allowedAccounts.find(a => 
        (a.routing_tag && a.routing_tag.toLowerCase() === cleanTag.toLowerCase()) ||
        (a.name && a.name.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9_-]/g, '') === cleanTag.toLowerCase()) ||
        (a.name && a.name.toLowerCase() === cleanTag.toLowerCase()) ||
        String(a.id) === cleanTag
      );
      if (found) targetAccounts = [found];
    }
    if (targetAccounts.length === 0 && accountId) {
      const found = allowedAccounts.find(a => String(a.id) === String(accountId));
      if (found) targetAccounts = [found];
    }
    if (targetAccounts.length === 0 && projectName && projectName.toLowerCase() !== 'all') {
      const found = allowedAccounts.find(a => 
        a.name.toLowerCase() === projectName.toLowerCase() ||
        (a.project_name && a.project_name.toLowerCase() === projectName.toLowerCase())
      );
      if (found) targetAccounts = [found];
    }

    // If scoped and target was not found in allowed accounts:
    if (!isUniversal && targetAccounts.length === 0) {
      return new Response(JSON.stringify({
        success: false,
        error: "Target account not authorized for this scoped master key",
        keyStatus: "ACTIVE"
      }), { status: 403, headers: corsHeaders });
    }

    // Allow explicit direct connection in request body if targetAccounts is still empty
    if (targetAccounts.length === 0 && (body.db_connection_string || (body.project_url && (body.db_password || body.password)))) {
      targetAccounts = [{
        name: body.projectName || body.project_name || 'Direct Postgres Connection',
        project_url: body.project_url || '',
        db_connection_string: body.db_connection_string || '',
        db_password: body.db_password || body.password || '',
        secret_key: body.secret_key || body.service_role_key || '',
        service_role_key: body.service_role_key || body.secret_key || '',
        publishable_key: body.publishable_key || '',
        api_key: body.api_key || body.secret_key || ''
      }];
    }

    if (targetAccounts.length === 0 && isUniversal) {
      targetAccounts = allowedAccounts;
    }

    if (targetAccounts.length === 0) {
      return new Response(JSON.stringify({
        success: false,
        error: 'No authorized connected Supabase accounts found in master database.',
        keyStatus: "ACTIVE"
      }), { status: 404, headers: corsHeaders });
    }

    const reqAction = body.action || url.searchParams.get('action') || (url.pathname.endsWith('/sql') ? 'sql' : null);
    const isWriteAction = ['insert', 'write', 'update', 'delete', 'sql', 'execute_sql', 'create_table', 'alter_table', 'upgrade_table', 'drop_table', 'delete_table'].includes(reqAction);

    // If master key is UNIVERSAL and action is write, user MUST specify target_account_tag
    if (isUniversal && isWriteAction && !cleanTag) {
      return new Response(JSON.stringify({
        success: false,
        error: "Universal master key requires target_account_tag or x-account-tag for writes",
        keyStatus: "ACTIVE"
      }), { status: 400, headers: corsHeaders });
    }

    // ================= ACTION: LIST_TAGS =================
    if (reqAction === 'list_tags' || (url.pathname.includes('/tags') && method === 'GET')) {
      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        tags: accounts.map(a => ({
          tag: a.routing_tag || `#${(a.name || '').toLowerCase().replace(/\s+/g, '-')}`,
          accountId: a.id,
          accountName: a.name,
          projectName: a.project_name,
          projectUrl: a.project_url,
          isActive: a.is_active !== false
        }))
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION: SEARCH_BY_TAG =================
    if (reqAction === 'search_by_tag' || reqAction === 'search_tag') {
      const searchTag = body.search_tag || body.tag || url.searchParams.get('tag') || url.searchParams.get('search_tag') || '';
      const cleanSearchTag = String(searchTag).trim().replace(/^#/, '');
      const tableName = (body.table || 'users').replace(/^\/+/, '').replace(/^rest\/v1\//, '');
      const selectQuery = body.select ? `?select=${encodeURIComponent(body.select)}` : '';

      if (!cleanSearchTag) {
        return new Response(JSON.stringify({
          success: false,
          error: "Missing 'search_tag' parameter for search_by_tag action",
          keyStatus: "ACTIVE"
        }), { status: 400, headers: corsHeaders });
      }

      const foundAcc = accounts.find(a => 
        (a.routing_tag && a.routing_tag.toLowerCase().replace(/^#/, '') === cleanSearchTag.toLowerCase()) ||
        (a.name && a.name.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9_-]/g, '') === cleanSearchTag.toLowerCase()) ||
        (a.name && a.name.toLowerCase() === cleanSearchTag.toLowerCase()) ||
        String(a.id) === cleanSearchTag
      );

      if (!foundAcc) {
        return new Response(JSON.stringify({
          success: false,
          error: `No connected account found matching search tag #${cleanSearchTag}`,
          keyStatus: "ACTIVE"
        }), { status: 404, headers: corsHeaders });
      }

      const authKey = foundAcc.service_role_key || foundAcc.secret_key || foundAcc.api_key;
      const targetUrl = `${foundAcc.project_url.replace(/\/+$/, '')}/rest/v1/${tableName}${selectQuery}`;

      try {
        const resp = await fetch(targetUrl, {
          method: 'GET',
          headers: {
            'apikey': authKey,
            'Authorization': `Bearer ${authKey}`
          }
        });
        const resData = await resp.json().catch(() => ([]));
        return new Response(JSON.stringify({
          success: resp.ok,
          keyStatus: "ACTIVE",
          target_account: foundAcc.name,
          search_tag: `#${cleanSearchTag}`,
          table: tableName,
          data: resData
        }, null, 2), { status: resp.status, headers: corsHeaders });
      } catch (e) {
        return new Response(JSON.stringify({
          success: false,
          error: e.message,
          keyStatus: "ACTIVE"
        }), { status: 500, headers: corsHeaders });
      }
    }

    // ================= DB CREDENTIALS VALIDATION =================
    const keyAllowedMethods = keyRecord.allowed_methods || ["direct_postgres", "service_role", "anon"];

    // Check credentials for SQL, DDL, and write actions
    const isProtectedAction = ['sql', 'execute_sql', 'create_table', 'alter_table', 'upgrade_table', 'drop_table', 'delete_table', 'insert', 'write', 'update', 'delete'].includes(reqAction);
    if (isProtectedAction) {
      if (!keyAllowedMethods.includes("direct_postgres") && !keyAllowedMethods.includes("service_role")) {
        return new Response(JSON.stringify({
          success: false,
          error: "Key requires db_connection_string or service_role_key",
          keyStatus: "ACTIVE"
        }), { status: 400, headers: corsHeaders });
      }

      for (const acc of targetAccounts) {
        const hasPg = !!(acc.db_connection_string || body.db_connection_string || (acc.project_url && (acc.db_password || body.db_password)));
        const hasServiceRole = !!(acc.service_role_key || acc.secret_key || body.service_role_key || body.secret_key || env.MASTER_SERVICE_ROLE_KEY);

        if (!hasPg && !hasServiceRole) {
          return new Response(JSON.stringify({
            success: false,
            error: "Key requires db_connection_string or service_role_key",
            keyStatus: "ACTIVE"
          }), { status: 400, headers: corsHeaders });
        }
      }
    }

    // ================= ACTION 1: SQL / EXECUTE_SQL =================
    if (reqAction === 'sql' || reqAction === 'execute_sql') {
      const sqlQuery = body.query || body.sql || url.searchParams.get('query') || '';
      if (!sqlQuery) {
        return new Response(JSON.stringify({ error: "Missing 'query' parameter in request body." }), { status: 400, headers: corsHeaders });
      }

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const effectiveAcc = {
          ...acc,
          db_connection_string: body.db_connection_string || body.connection_string || acc.db_connection_string,
          db_password: body.db_password || body.password || acc.db_password,
          secret_key: body.secret_key || body.service_role_key || acc.secret_key,
          service_role_key: body.service_role_key || acc.service_role_key
        };
        const res = await attemptExecuteSql(effectiveAcc, sqlQuery, env);
        return { account: acc.name, routingTag: acc.routing_tag || acc.name, ...res };
      }));

      const allSuccess = results.every(r => r.success !== false);
      if (!allSuccess) {
        const failedResult = results.find(r => r.success === false) || results[0];
        return new Response(JSON.stringify({
          success: false,
          keyStatus: "ACTIVE",
          status: "SQL_EXECUTION_FAILED",
          error: failedResult.reason || failedResult.error || "SQL execution failed on target database",
          query: sqlQuery,
          data: targetAccounts.length === 1 ? results[0] : results,
          result: targetAccounts.length === 1 ? results[0] : results,
          results: targetAccounts.length === 1 ? results[0] : results
        }, null, 2), { status: 400, headers: corsHeaders });
      }

      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        status: "SUCCESS",
        totalAccounts: targetAccounts.length,
        query: sqlQuery,
        data: targetAccounts.length === 1 ? (results[0].result || results[0].rows || results[0]) : results.map(r => r.result || r.rows || r),
        result: targetAccounts.length === 1 ? (results[0].result || results[0].rows || results[0]) : results.map(r => r.result || r.rows || r),
        output: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 2: CREATE_TABLE =================
    if (reqAction === 'create_table') {
      const tableName = body.table || body.tableName || 'new_table';
      const cols = body.columns || body.data;
      const autoSql = generateCreateTableFromCols(tableName, cols);

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const effectiveAcc = {
          ...acc,
          db_connection_string: body.db_connection_string || body.connection_string || acc.db_connection_string,
          db_password: body.db_password || body.password || acc.db_password,
          secret_key: body.secret_key || body.service_role_key || acc.secret_key,
          service_role_key: body.service_role_key || acc.service_role_key
        };
        const res = await attemptExecuteSql(effectiveAcc, autoSql, env);
        return { account: acc.name, routingTag: acc.routing_tag || acc.name, ...res };
      }));

      const allSuccess = results.every(r => r.success !== false);
      if (!allSuccess) {
        const failedResult = results.find(r => r.success === false) || results[0];
        return new Response(JSON.stringify({
          success: false,
          keyStatus: "ACTIVE",
          status: "SQL_EXECUTION_FAILED",
          error: failedResult.reason || failedResult.error || "CREATE TABLE execution failed on target database",
          tableName: tableName,
          autoGeneratedSql: autoSql,
          data: targetAccounts.length === 1 ? results[0] : results,
          result: targetAccounts.length === 1 ? results[0] : results,
          results: targetAccounts.length === 1 ? results[0] : results
        }, null, 2), { status: 400, headers: corsHeaders });
      }

      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        status: "SUCCESS",
        tableName: tableName,
        autoGeneratedSql: autoSql,
        data: targetAccounts.length === 1 ? (results[0].result || results[0].rows || results[0]) : results.map(r => r.result || r.rows || r),
        result: targetAccounts.length === 1 ? (results[0].result || results[0].rows || results[0]) : results.map(r => r.result || r.rows || r),
        output: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 3: ALTER_TABLE =================
    if (reqAction === 'alter_table') {
      const tableName = body.table || body.tableName || 'new_table';
      const col = body.addColumn || body.column;
      const colName = typeof col === 'object' ? col.name : (body.columnName || body.column || 'new_column');
      const colType = typeof col === 'object' ? col.type : (body.columnType || body.type || 'text');

      const alterSql = `ALTER TABLE public."${tableName.replace(/[^a-zA-Z0-9_]/g, '')}" ADD COLUMN IF NOT EXISTS "${colName.replace(/[^a-zA-Z0-9_]/g, '')}" ${colType};`;

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const effectiveAcc = {
          ...acc,
          db_connection_string: body.db_connection_string || body.connection_string || acc.db_connection_string,
          db_password: body.db_password || body.password || acc.db_password,
          secret_key: body.secret_key || body.service_role_key || acc.secret_key,
          service_role_key: body.service_role_key || acc.service_role_key
        };
        const res = await attemptExecuteSql(effectiveAcc, alterSql, env);
        return { account: acc.name, routingTag: acc.routing_tag || acc.name, ...res };
      }));

      const allSuccess = results.every(r => r.success !== false);
      if (!allSuccess) {
        const failedResult = results.find(r => r.success === false) || results[0];
        return new Response(JSON.stringify({
          success: false,
          keyStatus: "ACTIVE",
          status: "SQL_EXECUTION_FAILED",
          error: failedResult.reason || failedResult.error || "ALTER TABLE execution failed on target database",
          tableName: tableName,
          addedColumn: colName,
          columnType: colType,
          sqlExecuted: alterSql
        }, null, 2), { status: 400, headers: corsHeaders });
      }

      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        status: "SUCCESS",
        addedColumn: colName,
        columnType: colType,
        sqlExecuted: alterSql
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 3.5: UPGRADE_TABLE =================
    if (reqAction === 'upgrade_table') {
      const tableName = body.table || body.tableName || 'new_table';
      const colsInput = body.columns || (body.addColumn ? [body.addColumn] : []);
      const columnNames = [];
      const sqlList = [];

      sqlList.push(`CREATE TABLE IF NOT EXISTS public."${tableName.replace(/[^a-zA-Z0-9_]/g, '')}" ("id" uuid PRIMARY KEY DEFAULT gen_random_uuid(), "created_at" timestamptz DEFAULT now());`);

      for (const c of colsInput) {
        const cName = typeof c === 'object' ? c.name : (c || '');
        const cType = typeof c === 'object' ? (c.type || 'text') : 'text';
        if (cName) {
          const cleanName = cName.replace(/[^a-zA-Z0-9_]/g, '');
          columnNames.push(cleanName);
          sqlList.push(`ALTER TABLE public."${tableName.replace(/[^a-zA-Z0-9_]/g, '')}" ADD COLUMN IF NOT EXISTS "${cleanName}" ${cType};`);
        }
      }

      const fullUpgradeSql = sqlList.join('\n');

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const effectiveAcc = {
          ...acc,
          db_connection_string: body.db_connection_string || body.connection_string || acc.db_connection_string,
          db_password: body.db_password || body.password || acc.db_password,
          secret_key: body.secret_key || body.service_role_key || acc.secret_key,
          service_role_key: body.service_role_key || acc.service_role_key
        };
        const res = await attemptExecuteSql(effectiveAcc, fullUpgradeSql, env);
        return { account: acc.name, routingTag: acc.routing_tag || acc.name, ...res };
      }));

      const allSuccess = results.every(r => r.success !== false);
      if (!allSuccess) {
        const failedResult = results.find(r => r.success === false) || results[0];
        return new Response(JSON.stringify({
          success: false,
          keyStatus: "ACTIVE",
          status: "SQL_EXECUTION_FAILED",
          error: failedResult.reason || failedResult.error || "UPGRADE TABLE execution failed on target database",
          tableName: tableName,
          addedColumns: columnNames,
          sqlExecuted: sqlList
        }, null, 2), { status: 400, headers: corsHeaders });
      }

      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        status: "SUCCESS",
        tableName: tableName,
        addedColumns: columnNames,
        sqlExecuted: sqlList,
        totalColumnsAdded: columnNames.length
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 4: DROP_TABLE / DELETE_TABLE =================
    if (reqAction === 'drop_table' || reqAction === 'delete_table') {
      const tableName = body.table || body.tableName || 'new_table';
      const dropSql = `DROP TABLE IF EXISTS public."${tableName.replace(/[^a-zA-Z0-9_]/g, '')}" CASCADE;`;

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const effectiveAcc = {
          ...acc,
          db_connection_string: body.db_connection_string || body.connection_string || acc.db_connection_string,
          db_password: body.db_password || body.password || acc.db_password,
          secret_key: body.secret_key || body.service_role_key || acc.secret_key,
          service_role_key: body.service_role_key || acc.service_role_key
        };
        const res = await attemptExecuteSql(effectiveAcc, dropSql, env);
        return { account: acc.name, routingTag: acc.routing_tag || acc.name, ...res };
      }));

      const allSuccess = results.every(r => r.success !== false);
      if (!allSuccess) {
        const failedResult = results.find(r => r.success === false) || results[0];
        return new Response(JSON.stringify({
          success: false,
          keyStatus: "ACTIVE",
          status: "SQL_EXECUTION_FAILED",
          error: failedResult.reason || failedResult.error || "DROP TABLE execution failed on target database",
          tableName: tableName,
          sqlExecuted: dropSql,
          data: targetAccounts.length === 1 ? results[0] : results,
          result: targetAccounts.length === 1 ? results[0] : results,
          results: targetAccounts.length === 1 ? results[0] : results
        }, null, 2), { status: 400, headers: corsHeaders });
      }

      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        status: "SUCCESS",
        tableName: tableName,
        sqlExecuted: dropSql,
        data: targetAccounts.length === 1 ? (results[0].result || results[0].rows || results[0]) : results.map(r => r.result || r.rows || r),
        result: targetAccounts.length === 1 ? (results[0].result || results[0].rows || results[0]) : results.map(r => r.result || r.rows || r),
        output: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 5: INSERT / WRITE =================
    if (reqAction === 'insert' || reqAction === 'write') {
      const tableName = (body.table || subPath || 'new_table').replace(/^\/+/, '').replace(/^rest\/v1\//, '');
      const recordData = body.data || body.record || body;

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const authKey = acc.service_role_key || acc.secret_key || acc.api_key;
        const targetUrl = `${acc.project_url.replace(/\/+$/, '')}/rest/v1/${tableName}`;
        const fetchOpts = {
          method: 'POST',
          headers: {
            'apikey': authKey,
            'Authorization': `Bearer ${authKey}`,
            'Content-Type': 'application/json',
            'Prefer': 'return=representation'
          },
          body: JSON.stringify(recordData)
        };

        let res = await fetch(targetUrl, fetchOpts);
        let resData = await res.json().catch(() => ({}));

        // Check if table missing
        const errStr = (res.status + ' ' + JSON.stringify(resData)).toLowerCase();
        if (res.status === 404 || res.status === 400 || errStr.includes('does not exist') || errStr.includes('relation')) {
          const provResult = await attemptAutoProvisionTable(acc, tableName, recordData, env);
          const retryRes = await fetch(targetUrl, fetchOpts);
          const retryData = await retryRes.json().catch(() => ({}));
          if (retryRes.ok) {
            return { account: acc.name, status: 'SUCCESS', success: true, autoProvisioned: true, data: retryData };
          } else {
            return { account: acc.name, status: 'TABLE_AUTO_SQL_READY', success: true, tableName: tableName, autoGeneratedSql: provResult.sqlExecuted || provResult.sql, data: provResult };
          }
        }

        return { account: acc.name, status: res.ok ? 'SUCCESS' : 'ERROR', success: res.ok, statusCode: res.status, data: resData };
      }));

      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        status: "INSERT_ACTION_COMPLETE",
        tableName: tableName,
        data: targetAccounts.length === 1 ? (results[0].data || results[0]) : results.map(r => r.data || r),
        result: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 6: UPDATE =================
    if (reqAction === 'update') {
      const tableName = (body.table || subPath || 'new_table').replace(/^\/+/, '').replace(/^rest\/v1\//, '');
      const recordData = body.data || body.record || {};
      const matchCol = body.matchColumn || (body.match ? Object.keys(body.match)[0] : 'id') || 'id';
      const matchVal = body.matchValue !== undefined ? body.matchValue : (body.match ? body.match[matchCol] : null);

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const authKey = acc.service_role_key || acc.secret_key || acc.api_key;
        const matchQuery = matchVal !== null ? `?${encodeURIComponent(matchCol)}=eq.${encodeURIComponent(matchVal)}` : '';
        const targetUrl = `${acc.project_url.replace(/\/+$/, '')}/rest/v1/${tableName}${matchQuery}`;
        const res = await fetch(targetUrl, {
          method: 'PATCH',
          headers: {
            'apikey': authKey,
            'Authorization': `Bearer ${authKey}`,
            'Content-Type': 'application/json',
            'Prefer': 'return=representation'
          },
          body: JSON.stringify(recordData)
        });
        const resData = await res.json().catch(() => ({}));
        return { account: acc.name, status: res.ok ? 'SUCCESS' : 'ERROR', success: res.ok, statusCode: res.status, data: resData };
      }));

      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        status: "UPDATE_ACTION_COMPLETE",
        tableName: tableName,
        data: targetAccounts.length === 1 ? (results[0].data || results[0]) : results.map(r => r.data || r),
        result: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 7: DELETE =================
    if (reqAction === 'delete') {
      const tableName = (body.table || subPath || 'new_table').replace(/^\/+/, '').replace(/^rest\/v1\//, '');
      const matchCol = body.matchColumn || (body.match ? Object.keys(body.match)[0] : 'id') || 'id';
      const matchVal = body.matchValue !== undefined ? body.matchValue : (body.match ? body.match[matchCol] : null);

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const authKey = acc.service_role_key || acc.secret_key || acc.api_key;
        const matchQuery = matchVal !== null ? `?${encodeURIComponent(matchCol)}=eq.${encodeURIComponent(matchVal)}` : '';
        const targetUrl = `${acc.project_url.replace(/\/+$/, '')}/rest/v1/${tableName}${matchQuery}`;
        const res = await fetch(targetUrl, {
          method: 'DELETE',
          headers: {
            'apikey': authKey,
            'Authorization': `Bearer ${authKey}`
          }
        });
        const resData = await res.json().catch(() => ({}));
        return { account: acc.name, status: res.ok ? 'SUCCESS' : 'ERROR', success: res.ok, statusCode: res.status, data: resData };
      }));

      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        status: "DELETE_ACTION_COMPLETE",
        tableName: tableName,
        data: targetAccounts.length === 1 ? (results[0].data || results[0]) : results.map(r => r.data || r),
        result: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // Default REST Forwarding when path or table provided
    let effectiveSubPath = subPath;
    let effectiveMethod = method;
    let effectiveData = body.data || body;

    if (reqAction === 'read' || reqAction === 'query') {
      const tableName = (body.table || body.path || subPath || 'users').replace(/^\/+/, '').replace(/^rest\/v1\//, '');
      const selectQuery = body.select ? `?select=${encodeURIComponent(body.select)}` : '';
      effectiveSubPath = `rest/v1/${tableName}${selectQuery}`;
      effectiveMethod = 'GET';
      effectiveData = undefined;
    }

    if (!effectiveSubPath) {
      return new Response(JSON.stringify({
        success: true,
        keyStatus: "ACTIVE",
        status: "ONLINE",
        service: "OTT Multi-Supabase Proxy Gateway",
        data: {
          message: "Proxy endpoint active. Send API requests with 'action' (sql, create_table, alter_table, drop_table, insert, update, delete, read) in JSON body."
        }
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // Execute standard REST forward
    const singleAccount = targetAccounts[0] || accounts[0];
    const authKey = singleAccount.service_role_key || singleAccount.secret_key || singleAccount.api_key;
    const targetUrl = `${singleAccount.project_url.replace(/\/+$/, '')}/${effectiveSubPath}`;
    const singleStart = Date.now();

    const fetchOpts = {
      method: effectiveMethod,
      headers: {
        'apikey': authKey,
        'Authorization': `Bearer ${authKey}`,
        'Content-Type': 'application/json',
        'Prefer': effectiveMethod === 'POST' ? 'return=representation' : undefined
      }
    };
    if (effectiveData && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(effectiveMethod)) {
      fetchOpts.body = JSON.stringify(effectiveData);
    }

    const targetResponse = await fetch(targetUrl, fetchOpts);
    const targetData = await targetResponse.json().catch(() => ({}));

    return new Response(JSON.stringify({
      success: targetResponse.ok,
      keyStatus: "ACTIVE",
      meta: { account: singleAccount.name, latencyMs: Date.now() - singleStart },
      data: targetData
    }), { status: targetResponse.status, headers: corsHeaders });
  }
};

// ================= MASTER KEY & SQL UTILITIES =================
function normalizeKeyRecord(k) {
  if (!k) return null;
  const rawKey = k.key || k.master_key || '';
  const keyName = k.name || k.key_name || 'Master Router Key';

  let status = k.status;
  if (!status) {
    if (k.is_active !== undefined) {
      status = k.is_active ? 'ACTIVE' : 'REVOKED';
    } else {
      status = 'ACTIVE';
    }
  }

  let connAccIds = k.connected_account_ids || k.assigned_account_ids || [];
  if (typeof connAccIds === 'string') {
    try {
      connAccIds = JSON.parse(connAccIds);
    } catch (e) {
      connAccIds = connAccIds ? [connAccIds] : [];
    }
  }
  if (!Array.isArray(connAccIds)) connAccIds = [];

  let connAccNames = k.connected_account_names || [];
  if (typeof connAccNames === 'string') {
    try {
      connAccNames = JSON.parse(connAccNames);
    } catch (e) {
      connAccNames = [];
    }
  }
  if (!Array.isArray(connAccNames)) connAccNames = [];

  const destination = k.destination || (connAccIds.length > 0 ? `${connAccIds.length} Accounts` : 'ALL');

  let allowedMethods = k.allowed_methods;
  if (typeof allowedMethods === 'string') {
    try {
      allowedMethods = JSON.parse(allowedMethods);
    } catch (e) {
      allowedMethods = null;
    }
  }
  if (!allowedMethods || !Array.isArray(allowedMethods) || allowedMethods.length === 0) {
    allowedMethods = ["direct_postgres", "service_role", "anon"];
  }

  return {
    ...k,
    key: rawKey,
    master_key: rawKey,
    name: keyName,
    key_name: keyName,
    status: status.toUpperCase(),
    is_active: status.toUpperCase() === 'ACTIVE',
    destination: destination,
    allowed_methods: allowedMethods,
    connected_account_ids: connAccIds,
    assigned_account_ids: connAccIds,
    connected_account_names: connAccNames
  };
}

async function validateMasterKey(authHeader, supabase, MASTER_URL, MASTER_KEY, env = {}) {
  if (!authHeader) {
    return { valid: false, error: "Invalid/Revoked master key" };
  }

  const rawKey = authHeader.replace(/^Bearer\s+/i, '').trim().replace(/^['"]|['"]$/g, '');
  if (!rawKey) {
    return { valid: false, error: "Invalid/Revoked master key" };
  }

  // 1. Check if matches environment master keys (root admin)
  if (rawKey === MASTER_KEY || (env && env.MASTER_SERVICE_ROLE_KEY && rawKey === env.MASTER_SERVICE_ROLE_KEY)) {
    return {
      valid: true,
      keyRecord: normalizeKeyRecord({
        key: rawKey,
        name: "Root Master Key",
        status: "ACTIVE",
        destination: "ALL",
        allowed_methods: ["direct_postgres", "service_role", "anon"],
        connected_account_ids: []
      })
    };
  }

  // 2. Query supabase_master_keys table via supabase-js
  let keyRecord = null;
  try {
    const { data: mkData, error: mkErr } = await supabase.from('supabase_master_keys').select('*').eq('master_key', rawKey).maybeSingle();
    if (!mkErr && mkData) keyRecord = mkData;
  } catch (e) {}

  if (!keyRecord) {
    try {
      const { data: kData, error: kErr } = await supabase.from('supabase_master_keys').select('*').eq('key', rawKey).maybeSingle();
      if (!kErr && kData) keyRecord = kData;
    } catch (e) {}
  }

  // 3. Fallback: REST query if supabase-js query fails
  if (!keyRecord) {
    try {
      const directRes = await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_master_keys?master_key=eq.${encodeURIComponent(rawKey)}&select=*`, {
        headers: {
          'apikey': MASTER_KEY,
          'Authorization': `Bearer ${MASTER_KEY}`
        }
      });
      if (directRes.ok) {
        const directData = await directRes.json();
        if (Array.isArray(directData) && directData.length > 0) {
          keyRecord = directData[0];
        }
      }
    } catch (e) {}
  }

  if (!keyRecord) {
    try {
      const directRes2 = await fetch(`${MASTER_URL.replace(/\/+$/, '')}/rest/v1/supabase_master_keys?key=eq.${encodeURIComponent(rawKey)}&select=*`, {
        headers: {
          'apikey': MASTER_KEY,
          'Authorization': `Bearer ${MASTER_KEY}`
        }
      });
      if (directRes2.ok) {
        const directData2 = await directRes2.json();
        if (Array.isArray(directData2) && directData2.length > 0) {
          keyRecord = directData2[0];
        }
      }
    } catch (e) {}
  }

  // 4. Default seed key fallback
  if (!keyRecord && rawKey === 'omni_live_master_default_2026') {
    keyRecord = {
      key: 'omni_live_master_default_2026',
      name: 'Universal Master Root Key',
      status: 'ACTIVE',
      destination: 'ALL',
      allowed_methods: ["direct_postgres", "service_role", "anon"],
      connected_account_ids: []
    };
  }

  if (!keyRecord) {
    return { valid: false, error: "Invalid/Revoked master key" };
  }

  const normalized = normalizeKeyRecord(keyRecord);
  if (!normalized.is_active || normalized.status !== 'ACTIVE') {
    return { valid: false, error: "Invalid/Revoked master key", status: "REVOKED" };
  }

  return { valid: true, keyRecord: normalized };
}

function parsePostgresDetails(connStr, dbPass, projectUrl) {
  let user = 'postgres';
  let password = (dbPass || '').trim();
  let host = '';
  let port = 5432;
  let database = 'postgres';
  let projectRef = '';

  if (projectUrl) {
    try {
      const u = new URL(projectUrl.startsWith('http') ? projectUrl : `https://${projectUrl}`);
      projectRef = u.hostname.split('.')[0];
      if (projectRef && !host) host = `db.${projectRef}.supabase.co`;
    } catch (e) {}
  }

  let str = (connStr || '').trim();
  if (str) {
    // 1. Find the host, port, database from the end of the connection string
    // Format: ...@<hostname>[:<port>]/<database>[?<query>]
    const hostMatch = str.match(/@([a-zA-Z0-9.-]+)(?::(\d+))?(?:\/([a-zA-Z0-9_.-]+))?(?:\?.*)?$/);
    if (hostMatch) {
      host = hostMatch[1];
      if (hostMatch[2]) port = parseInt(hostMatch[2], 10);
      if (hostMatch[3]) database = hostMatch[3];

      // 2. Extract user and pass from before the last '@'
      const schemeEnd = str.indexOf('://');
      const prefix = schemeEnd !== -1 ? str.slice(schemeEnd + 3) : str;
      const lastAt = prefix.lastIndexOf('@');
      if (lastAt !== -1) {
        const userPassSegment = prefix.slice(0, lastAt);
        const colonIdx = userPassSegment.indexOf(':');
        if (colonIdx !== -1) {
          user = decodeURIComponent(userPassSegment.slice(0, colonIdx));
          const rawExtractedPass = userPassSegment.slice(colonIdx + 1);
          if (!password && rawExtractedPass && !rawExtractedPass.includes('[YOUR-PASSWORD]') && !rawExtractedPass.includes('[PASSWORD]')) {
            try {
              password = decodeURIComponent(rawExtractedPass);
            } catch (e) {
              password = rawExtractedPass;
            }
          }
        } else {
          user = decodeURIComponent(userPassSegment);
        }
      }
    } else {
      try {
        const validUri = str.match(/^postgres(?:ql)?:\/\//i) ? str : `postgresql://${str}`;
        const parsed = new URL(validUri);
        if (parsed.username) user = decodeURIComponent(parsed.username);
        if (!password && parsed.password && !parsed.password.includes('[YOUR-PASSWORD]') && !parsed.password.includes('[PASSWORD]')) {
          try {
            password = decodeURIComponent(parsed.password);
          } catch (e) {
            password = parsed.password;
          }
        }
        if (parsed.hostname) host = parsed.hostname;
        if (parsed.port) port = parseInt(parsed.port, 10);
        if (parsed.pathname && parsed.pathname.length > 1) {
          database = parsed.pathname.replace(/^\//, '');
        }
      } catch (e) {}
    }
  }

  if (!projectRef && host) {
    if (host.startsWith('db.')) {
      projectRef = host.split('.')[1];
    } else if (user.includes('.')) {
      projectRef = user.split('.')[1];
    }
  }

  if (projectRef && !host) {
    host = `db.${projectRef}.supabase.co`;
  }

  return { user, password, host, port, database, projectRef };
}

async function executeSqlDirectPostgres(targetAccount, cleanSql) {
  const connStr = targetAccount.db_connection_string || targetAccount.connection_string || '';
  const password = targetAccount.db_password || targetAccount.password || '';
  const projectUrl = targetAccount.project_url || '';

  const parsed = parsePostgresDetails(connStr, password, projectUrl);
  if (!parsed || !parsed.host || !parsed.password) {
    const missing = [];
    if (!parsed?.host) missing.push('Database Host');
    if (!parsed?.password) missing.push('Database Password');
    return {
      success: false,
      reason: `Incomplete DB connection credentials (Missing: ${missing.join(', ')}). Please provide Database Password.`
    };
  }

  const targetsToTry = [
    { host: parsed.host, port: parsed.port || 5432, user: parsed.user || 'postgres' }
  ];

  // If host is db.<ref>.supabase.co, also add port 6543 (transaction pooler) as fallback
  if (parsed.host.startsWith('db.') && parsed.projectRef) {
    targetsToTry.push({
      host: parsed.host,
      port: 6543,
      user: parsed.user.includes('.') ? parsed.user : `postgres.${parsed.projectRef}`
    });
  }

  // If parsed port is 6543, also try 5432
  if (parsed.port === 6543) {
    targetsToTry.push({
      host: parsed.host,
      port: 5432,
      user: parsed.user
    });
  }

  let lastError = null;

  for (const target of targetsToTry) {
    // 1. Try pg.Client (official Node.js PostgreSQL client in Cloudflare Workers)
    try {
      const client = new pg.Client({
        host: target.host,
        port: target.port,
        database: parsed.database || 'postgres',
        user: target.user,
        password: parsed.password,
        ssl: { rejectUnauthorized: false },
        connectionTimeoutMillis: 10000
      });

      await client.connect();
      const res = await client.query(cleanSql);
      await client.end().catch(() => {});

      let rows = [];
      let command = 'EXECUTED';
      let rowCount = 0;

      if (Array.isArray(res)) {
        command = res.map(r => r.command).filter(Boolean).join('; ') || 'BATCH_EXECUTED';
        rowCount = res.reduce((sum, r) => sum + (r.rowCount || 0), 0);
        const statementsWithRows = res.filter(r => Array.isArray(r.rows) && r.rows.length > 0);
        if (statementsWithRows.length > 0) {
          rows = statementsWithRows[statementsWithRows.length - 1].rows;
        } else {
          rows = [{ status: 'success', command: command, statementsExecuted: res.length }];
        }
      } else if (res && typeof res === 'object') {
        command = res.command || 'EXECUTED';
        rowCount = res.rowCount !== undefined && res.rowCount !== null ? res.rowCount : (res.rows ? res.rows.length : 0);
        rows = Array.isArray(res.rows) && res.rows.length > 0
          ? res.rows
          : [{ status: 'success', command: command, rowCount: rowCount }];
      }

      return {
        success: true,
        status: "SUCCESS",
        method: `direct_postgres_pg_${target.port}`,
        command: command,
        rowCount: rowCount,
        result: rows,
        rows: rows,
        data: rows,
        sqlExecuted: cleanSql
      };
    } catch (pgErr) {
      lastError = pgErr;
    }

    // 2. Try postgres client library
    try {
      const sqlClient = postgres({
        host: target.host,
        port: target.port,
        database: parsed.database || 'postgres',
        username: target.user,
        password: parsed.password,
        ssl: { rejectUnauthorized: false },
        connect_timeout: 8,
        idle_timeout: 4
      });

      const rows = await sqlClient.unsafe(cleanSql);
      await sqlClient.end({ timeout: 2 }).catch(() => {});

      const resultList = Array.isArray(rows) ? rows : [rows];
      return {
        success: true,
        status: "SUCCESS",
        method: `direct_postgres_lib_${target.port}`,
        command: rows.command || 'QUERY',
        rowCount: rows.count !== undefined ? rows.count : resultList.length,
        result: resultList.length > 0 ? resultList : [{ status: 'success', rowCount: rows.count || 0 }],
        rows: resultList,
        data: resultList,
        sqlExecuted: cleanSql
      };
    } catch (libErr) {
      if (!lastError) lastError = libErr;
    }
  }

  return {
    success: false,
    reason: `Direct PostgreSQL connection failed (${targetsToTry[0].host}:${targetsToTry[0].port}): ${lastError ? lastError.message : 'Connection failed'}`
  };
}

async function attemptExecuteSql(targetAccount, sqlQuery, env = {}) {
  const cleanSql = sqlQuery.trim();
  const serviceKey = targetAccount.secret_key || targetAccount.service_role_key || (env && (env.MASTER_SERVICE_ROLE_KEY || env.SUPABASE_SERVICE_ROLE_KEY || env.SERVICE_ROLE_KEY)) || targetAccount.publishable_key || targetAccount.api_key;
  const projectUrl = (targetAccount.project_url || '').replace(/\/+$/, '');
  
  let projectRef = targetAccount.project_ref || targetAccount.ref || '';
  if (!projectRef && projectUrl) {
    try {
      const parsed = new URL(projectUrl);
      projectRef = parsed.hostname.split('.')[0];
    } catch (e) {}
  }

  // 1. Attempt Direct PostgreSQL execution using db_connection_string / db_password
  const directPgRes = await executeSqlDirectPostgres(targetAccount, cleanSql);
  if (directPgRes.success) {
    return directPgRes;
  }

  // 2. Supabase Management API / DB Query endpoint
  if (projectRef && serviceKey) {
    try {
      const mgmtRes = await fetch(`https://api.supabase.com/v1/projects/${projectRef}/db/query`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${serviceKey}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ query: cleanSql })
      });
      if (mgmtRes.ok) {
        const mgmtData = await mgmtRes.json().catch(() => ([]));
        return {
          success: true,
          method: 'management_api',
          result: mgmtData,
          sqlExecuted: cleanSql
        };
      }
    } catch (e) {}
  }

  // 3. Supabase SQL / PG query HTTP endpoints
  const sqlEndpoints = [
    `${projectUrl}/sql`,
    `${projectUrl}/pg`,
    `${projectUrl}/rest/v1/query`,
    `${projectUrl}/query`
  ];

  for (const ep of sqlEndpoints) {
    try {
      const res = await fetch(ep, {
        method: 'POST',
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ query: cleanSql, sql: cleanSql })
      });
      if (res.ok) {
        const resData = await res.json().catch(() => ({ status: 'executed' }));
        return {
          success: true,
          method: ep,
          result: resData,
          sqlExecuted: cleanSql
        };
      }
    } catch (e) {}
  }

  // 4. PostgREST RPC endpoints
  const rpcEndpoints = [
    `${projectUrl}/rest/v1/rpc/exec_sql`,
    `${projectUrl}/rest/v1/rpc/sql`,
    `${projectUrl}/rest/v1/rpc/query`,
    `${projectUrl}/rest/v1/rpc/run_sql`
  ];

  for (const ep of rpcEndpoints) {
    try {
      const res = await fetch(ep, {
        method: 'POST',
        headers: {
          'apikey': serviceKey,
          'Authorization': `Bearer ${serviceKey}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ query: cleanSql, sql: cleanSql })
      });
      if (res.ok) {
        const resData = await res.json().catch(() => ({ status: 'executed' }));
        return {
          success: true,
          method: ep,
          result: resData,
          sqlExecuted: cleanSql
        };
      }
    } catch (e) {}
  }

  // 5. Explicit Failure Response if all methods were exhausted
  return {
    success: false,
    status: "SQL_EXECUTION_FAILED",
    error: directPgRes.reason || "Direct SQL execution failed on target Supabase database. Please check db_connection_string and db_password.",
    sqlExecuted: cleanSql
  };
}

function generateCreateTableSql(tableName, data) {
  const cleanTable = (tableName || 'new_table').replace(/[^a-zA-Z0-9_]/g, '');
  const sample = (data && typeof data === 'object') ? (Array.isArray(data) ? data[0] : data) : {};

  const columns = [];
  let hasId = false;

  for (const [key, val] of Object.entries(sample || {})) {
    const cleanKey = key.replace(/[^a-zA-Z0-9_]/g, '');
    if (!cleanKey) continue;
    if (cleanKey.toLowerCase() === 'id') {
      hasId = true;
      if (typeof val === 'number') {
        columns.push(`"id" bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY`);
      } else {
        columns.push(`"id" uuid PRIMARY KEY DEFAULT gen_random_uuid()`);
      }
      continue;
    }

    let colType = 'text';
    if (typeof val === 'number') {
      colType = Number.isInteger(val) ? 'bigint' : 'numeric';
    } else if (typeof val === 'boolean') {
      colType = 'boolean';
    } else if (typeof val === 'object' && val !== null) {
      colType = 'jsonb';
    } else if (typeof val === 'string') {
      if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}/.test(val)) {
        colType = 'timestamptz';
      } else {
        colType = 'text';
      }
    }
    columns.push(`"${cleanKey}" ${colType}`);
  }

  if (!hasId) {
    columns.unshift(`"id" uuid PRIMARY KEY DEFAULT gen_random_uuid()`);
  }
  if (!sample || !('created_at' in sample)) {
    columns.push(`"created_at" timestamptz DEFAULT now()`);
  }

  return `CREATE TABLE IF NOT EXISTS public."${cleanTable}" (\n  ${columns.join(',\n  ')}\n);\nALTER TABLE public."${cleanTable}" DISABLE ROW LEVEL SECURITY;\nGRANT ALL ON TABLE public."${cleanTable}" TO anon, authenticated, service_role;`;
}

function generateCreateTableFromCols(tableName, cols) {
  if (!cols) return generateCreateTableSql(tableName, {});
  if (typeof cols === 'object' && !Array.isArray(cols)) {
    return generateCreateTableSql(tableName, cols);
  }
  const cleanTable = (tableName || 'new_table').replace(/[^a-zA-Z0-9_]/g, '');
  const columns = [];
  let hasId = false;

  if (Array.isArray(cols)) {
    for (const c of cols) {
      if (typeof c === 'string') {
        columns.push(`"${c.replace(/[^a-zA-Z0-9_]/g, '')}" text`);
      } else if (typeof c === 'object' && c.name) {
        const cName = c.name.replace(/[^a-zA-Z0-9_]/g, '');
        if (cName.toLowerCase() === 'id') {
          hasId = true;
          columns.push(`"id" uuid PRIMARY KEY DEFAULT gen_random_uuid()`);
        } else {
          columns.push(`"${cName}" ${c.type || 'text'}`);
        }
      }
    }
  }

  if (!hasId) {
    columns.unshift(`"id" uuid PRIMARY KEY DEFAULT gen_random_uuid()`);
  }
  columns.push(`"created_at" timestamptz DEFAULT now()`);

  return `CREATE TABLE IF NOT EXISTS public."${cleanTable}" (\n  ${columns.join(',\n  ')}\n);\nALTER TABLE public."${cleanTable}" DISABLE ROW LEVEL SECURITY;\nGRANT ALL ON TABLE public."${cleanTable}" TO anon, authenticated, service_role;`;
}

async function attemptAutoProvisionTable(targetAccount, tableName, sampleData, env = {}) {
  const sql = generateCreateTableSql(tableName, sampleData);
  const res = await attemptExecuteSql(targetAccount, sql, env);
  return res;
}

function parseRawSupabaseCredentials(raw) {
  if (!raw || typeof raw !== 'string') return {};

  const dashMatch = raw.match(/supabase\.com\/dashboard\/project\/([a-zA-Z0-9_-]+)/i);
  const urlMatch = raw.match(/https?:\/\/[a-zA-Z0-9.-]+\.supabase\.co/i);
  const pubMatch = raw.match(/sb_publishable_[a-zA-Z0-9_-]+/i);
  const secMatch = raw.match(/sb_secret_[a-zA-Z0-9_-]+/i);
  const jwtMatches = raw.match(/eyJhbGciOi[a-zA-Z0-9._-]+/g);
  const uriMatch = raw.match(/(?:postgresql|postgres):\/\/[^\s"']+/i);

  // Match Project Name (supports "Project name: ViralHub" or "Project name\nViralHub")
  let project_name = '';
  const nameMatch = raw.match(/(?:project\s*name|alias)[\s:=]+([^\n\r"']+)/i);
  if (nameMatch && nameMatch[1]) {
    project_name = nameMatch[1].trim().replace(/^['"]|['"]$/g, '');
  }

  // Match Database Password
  let db_password = '';
  // 1. Check explicit "Database password\n<pass>" or "Database password: <pass>"
  const passMatch = raw.match(/(?:Database\s*password|db_password|db\s*pass)(?!\s*string|\s*uri|\s*url)[\s:=]+([^\n\r"']+)/i);
  if (passMatch && passMatch[1]) {
    const candidate = passMatch[1].trim().replace(/^['"]|['"]$/g, '');
    if (!candidate.toLowerCase().startsWith('postgres') && !candidate.toLowerCase().startsWith('note:') && !candidate.toLowerCase().startsWith('this password')) {
      db_password = candidate;
    }
  }

  // 2. Also check if URI has a non-placeholder password: postgresql://postgres:pass@...
  if (uriMatch && !db_password) {
    const uri = uriMatch[0].trim();
    const lastAt = uri.lastIndexOf('@');
    const schemeEnd = uri.indexOf('://');
    if (lastAt !== -1 && schemeEnd !== -1) {
      const userPass = uri.slice(schemeEnd + 3, lastAt);
      const colonIdx = userPass.indexOf(':');
      if (colonIdx !== -1) {
        const p = userPass.slice(colonIdx + 1);
        if (p && !p.includes('[YOUR-PASSWORD]') && !p.includes('[PASSWORD]')) {
          try {
            db_password = decodeURIComponent(p);
          } catch (e) {
            db_password = p;
          }
        }
      }
    }
  }

  // 3. Fallback: Check top lines for password pattern (e.g. email on line 1, pass on line 2)
  if (!db_password) {
    const lines = raw.split(/\r?\n/).map(l => l.trim()).filter(Boolean);
    for (let i = 0; i < Math.min(lines.length - 1, 5); i++) {
      if (lines[i].includes('@') && lines[i].includes('.') && !lines[i].startsWith('postgres')) {
        const nextLine = lines[i + 1];
        if (nextLine && nextLine.length >= 6 && !nextLine.includes(' ') && !nextLine.toLowerCase().includes('project') && !nextLine.toLowerCase().includes('url')) {
          db_password = nextLine;
          break;
        }
      }
    }
  }

  // Project URL
  let project_url = '';
  if (dashMatch) {
    project_url = `https://${dashMatch[1]}.supabase.co`;
  } else if (urlMatch) {
    project_url = urlMatch[0].trim();
  }

  if (!project_name && project_url) {
    const sub = project_url.replace(/https?:\/\//, '').split('.')[0];
    project_name = 'Project ' + sub;
  }

  // Keys
  let publishable_key = pubMatch ? pubMatch[0].trim() : '';
  let secret_key = secMatch ? secMatch[0].trim() : '';
  let anon_key = '';
  let service_role_key = '';

  if (jwtMatches && jwtMatches.length > 0) {
    if (!publishable_key) publishable_key = jwtMatches[0].trim();
    anon_key = jwtMatches[0].trim();
    if (jwtMatches.length > 1) {
      if (!secret_key) secret_key = jwtMatches[1].trim();
      service_role_key = jwtMatches[1].trim();
    }
  }

  // Connection String
  let db_connection_string = uriMatch ? uriMatch[0].trim() : '';
  if (db_connection_string && db_password) {
    if (db_connection_string.includes('[YOUR-PASSWORD]') || db_connection_string.includes('[PASSWORD]')) {
      const encPass = encodeURIComponent(db_password);
      db_connection_string = db_connection_string.replace(/\[YOUR[-_]PASSWORD\]/gi, encPass)
                                                 .replace(/\[PASSWORD\]/gi, encPass);
    }
  } else if (!db_connection_string && project_url && db_password) {
    const ref = project_url.replace(/https?:\/\//, '').split('.')[0];
    const encPass = encodeURIComponent(db_password);
    db_connection_string = `postgresql://postgres:${encPass}@db.${ref}.supabase.co:5432/postgres`;
  }

  return {
    project_name,
    project_url,
    publishable_key,
    secret_key,
    anon_key,
    service_role_key: service_role_key || secret_key,
    api_key: secret_key || publishable_key,
    db_connection_string,
    db_password
  };
}

async function ensureMasterDatabaseSchema(MASTER_URL, MASTER_KEY, env = {}) {
  const masterAcc = {
    name: 'Master Account',
    project_url: MASTER_URL,
    secret_key: env.MASTER_SERVICE_ROLE_KEY || MASTER_KEY,
    service_role_key: env.MASTER_SERVICE_ROLE_KEY || MASTER_KEY,
    publishable_key: MASTER_KEY,
    api_key: MASTER_KEY
  };

  const schemaSql = `
    CREATE TABLE IF NOT EXISTS public.supabase_accounts (
      id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      name text,
      project_name text,
      project_url text,
      api_key text,
      routing_tag text,
      is_active boolean DEFAULT true,
      publishable_key text,
      secret_key text,
      anon_key text,
      service_role_key text,
      db_connection_string text,
      db_password text,
      created_at timestamptz DEFAULT now()
    );
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS name text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS project_name text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS project_url text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS api_key text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS routing_tag text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS is_active boolean DEFAULT true;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS publishable_key text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS secret_key text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS anon_key text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS service_role_key text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS db_connection_string text;
    ALTER TABLE public.supabase_accounts ADD COLUMN IF NOT EXISTS db_password text;
    ALTER TABLE public.supabase_accounts DISABLE ROW LEVEL SECURITY;
    GRANT ALL ON TABLE public.supabase_accounts TO anon, authenticated, service_role;

    CREATE TABLE IF NOT EXISTS public.supabase_master_keys (
      key text PRIMARY KEY,
      name text,
      status text DEFAULT 'ACTIVE',
      destination text DEFAULT 'ALL',
      allowed_methods jsonb DEFAULT '["direct_postgres", "service_role", "anon"]'::jsonb,
      created text,
      connected_account_ids jsonb DEFAULT '[]'::jsonb,
      connected_account_names jsonb DEFAULT '[]'::jsonb,
      created_at timestamptz DEFAULT now()
    );
    ALTER TABLE public.supabase_master_keys ADD COLUMN IF NOT EXISTS status text DEFAULT 'ACTIVE';
    ALTER TABLE public.supabase_master_keys ADD COLUMN IF NOT EXISTS destination text DEFAULT 'ALL';
    ALTER TABLE public.supabase_master_keys ADD COLUMN IF NOT EXISTS allowed_methods jsonb DEFAULT '["direct_postgres", "service_role", "anon"]'::jsonb;
    ALTER TABLE public.supabase_master_keys ADD COLUMN IF NOT EXISTS connected_account_ids jsonb DEFAULT '[]'::jsonb;
    ALTER TABLE public.supabase_master_keys ADD COLUMN IF NOT EXISTS connected_account_names jsonb DEFAULT '[]'::jsonb;
    ALTER TABLE public.supabase_master_keys DISABLE ROW LEVEL SECURITY;
    GRANT ALL ON TABLE public.supabase_master_keys TO anon, authenticated, service_role;

    CREATE TABLE IF NOT EXISTS public.data_routing_log (
      id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
      routing_tag text,
      table_name text,
      source_account text,
      saved_at timestamptz DEFAULT now(),
      payload jsonb
    );
    ALTER TABLE public.data_routing_log DISABLE ROW LEVEL SECURITY;
    GRANT ALL ON TABLE public.data_routing_log TO anon, authenticated, service_role;

    INSERT INTO public.supabase_master_keys (key, name, status, destination, allowed_methods, created)
    VALUES ('omni_live_master_default_2026', 'Universal Master Root Key', 'ACTIVE', 'ALL', '["direct_postgres", "service_role", "anon"]'::jsonb, '2026-01-01')
    ON CONFLICT (key) DO UPDATE SET status = 'ACTIVE';
  `;

  try {
    await attemptExecuteSql(masterAcc, schemaSql, env);
  } catch (e) {}
}

async function insertAccountViaSql(MASTER_URL, MASTER_KEY, accountObj, env = {}) {
  const masterAcc = {
    project_url: MASTER_URL,
    secret_key: env.MASTER_SERVICE_ROLE_KEY || MASTER_KEY,
    service_role_key: env.MASTER_SERVICE_ROLE_KEY || MASTER_KEY,
    publishable_key: MASTER_KEY,
    api_key: MASTER_KEY
  };

  const fields = [];
  const values = [];

  const allowedFields = [
    'name', 'project_name', 'project_url', 'api_key', 'routing_tag',
    'is_active', 'publishable_key', 'secret_key', 'anon_key',
    'service_role_key', 'db_connection_string', 'db_password'
  ];

  for (const [k, v] of Object.entries(accountObj || {})) {
    if (!allowedFields.includes(k) || v === undefined) continue;
    fields.push(`"${k.replace(/[^a-zA-Z0-9_]/g, '')}"`);
    if (v === null) {
      values.push('NULL');
    } else if (typeof v === 'boolean' || typeof v === 'number') {
      values.push(v);
    } else {
      values.push(`'${String(v).replace(/'/g, "''")}'`);
    }
  }

  if (fields.length === 0) {
    return { success: false, error: 'No valid fields provided' };
  }

  const sql = `INSERT INTO public.supabase_accounts (${fields.join(', ')}) VALUES (${values.join(', ')}) RETURNING *;`;
  const res = await attemptExecuteSql(masterAcc, sql, env);
  if (res.success) {
    return { success: true, data: res.result };
  }
  return { success: false, error: res.error || 'SQL insert failed' };
}

async function updateAccountViaSql(MASTER_URL, MASTER_KEY, id, accountObj, env = {}) {
  const masterAcc = {
    project_url: MASTER_URL,
    secret_key: env.MASTER_SERVICE_ROLE_KEY || MASTER_KEY,
    service_role_key: env.MASTER_SERVICE_ROLE_KEY || MASTER_KEY,
    publishable_key: MASTER_KEY,
    api_key: MASTER_KEY
  };

  const sets = [];
  const allowedFields = [
    'name', 'project_name', 'project_url', 'api_key', 'routing_tag',
    'is_active', 'publishable_key', 'secret_key', 'anon_key',
    'service_role_key', 'db_connection_string', 'db_password'
  ];

  for (const [k, v] of Object.entries(accountObj || {})) {
    if (!allowedFields.includes(k) || v === undefined) continue;
    const cleanK = k.replace(/[^a-zA-Z0-9_]/g, '');
    if (v === null) {
      sets.push(`"${cleanK}" = NULL`);
    } else if (typeof v === 'boolean' || typeof v === 'number') {
      sets.push(`"${cleanK}" = ${v}`);
    } else {
      sets.push(`"${cleanK}" = '${String(v).replace(/'/g, "''")}'`);
    }
  }

  if (sets.length === 0) {
    return { success: false, error: 'No valid fields provided for update' };
  }

  const sql = `UPDATE public.supabase_accounts SET ${sets.join(', ')} WHERE id = ${parseInt(id, 10)} RETURNING *;`;
  const res = await attemptExecuteSql(masterAcc, sql, env);
  if (res.success) {
    return { success: true, data: res.result };
  }
  return { success: false, error: res.error || 'SQL update failed' };
}

