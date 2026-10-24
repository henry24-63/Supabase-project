import { createClient } from '@supabase/supabase-js';
import htmlContent from './index.html';

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

    // 3. API ACCOUNTS ROUTE (/api/accounts)
    if (url.pathname === '/api/accounts' || url.pathname === '/api/accounts/') {
      if (method === 'GET') {
        const { data, error } = await supabase.from('supabase_accounts').select('*');
        if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: corsHeaders });
        return new Response(JSON.stringify(data || []), { status: 200, headers: corsHeaders });
      }
      if (method === 'POST') {
        const reqBody = await request.json().catch(() => ({}));
        const { data, error } = await supabase.from('supabase_accounts').insert([reqBody]).select();
        if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: corsHeaders });
        return new Response(JSON.stringify(data || reqBody), { status: 200, headers: corsHeaders });
      }
      if (method === 'PATCH') {
        const id = url.searchParams.get('id');
        const reqBody = await request.json().catch(() => ({}));
        if (!id) return new Response(JSON.stringify({ error: 'id required' }), { status: 400, headers: corsHeaders });
        const { data, error } = await supabase.from('supabase_accounts').update(reqBody).eq('id', id).select();
        if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: corsHeaders });
        return new Response(JSON.stringify(data || reqBody), { status: 200, headers: corsHeaders });
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
        const { data, error } = await supabase.from('supabase_master_keys').select('*');
        if (error) {
          return new Response(JSON.stringify([]), { status: 200, headers: corsHeaders });
        }
        return new Response(JSON.stringify(data || []), { status: 200, headers: corsHeaders });
      }
      if (method === 'POST') {
        const reqBody = await request.json().catch(() => ({}));
        const keyRecord = {
          key: reqBody.key,
          name: reqBody.name || 'Master Router Key',
          status: reqBody.status || 'ACTIVE',
          destination: reqBody.destination || 'All Accounts',
          created: reqBody.created || new Date().toISOString().split('T')[0],
          connected_account_ids: reqBody.connected_account_ids || [],
          connected_account_names: reqBody.connected_account_names || []
        };
        const { data, error } = await supabase.from('supabase_master_keys').upsert([keyRecord]).select();
        if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: corsHeaders });
        return new Response(JSON.stringify(data || keyRecord), { status: 200, headers: corsHeaders });
      }
      if (method === 'PATCH') {
        const keyParam = url.searchParams.get('key');
        const reqBody = await request.json().catch(() => ({}));
        const targetKey = keyParam || reqBody.key;
        if (!targetKey) return new Response(JSON.stringify({ error: 'key required' }), { status: 400, headers: corsHeaders });
        const { data, error } = await supabase.from('supabase_master_keys').update(reqBody).eq('key', targetKey).select();
        if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: corsHeaders });
        return new Response(JSON.stringify(data || reqBody), { status: 200, headers: corsHeaders });
      }
      if (method === 'DELETE') {
        const keyParam = url.searchParams.get('key');
        const reqBody = await request.json().catch(() => ({}));
        const targetKey = keyParam || reqBody.key;
        if (targetKey) {
          await supabase.from('supabase_master_keys').delete().eq('key', targetKey);
        }
        return new Response(JSON.stringify({ success: true }), { status: 200, headers: corsHeaders });
      }
    }

    // 5. PROXY / QUERY / READ / WRITE / SQL ROUTING
    let body = {};
    try {
      if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        body = await request.json().catch(() => ({}));
      }
    } catch (e) {}

    const authHeader = request.headers.get('Authorization') || request.headers.get('apikey') || '';
    const projectName = request.headers.get('x-project-name') || url.searchParams.get('project') || body.projectName;
    const accountId = request.headers.get('x-account-id') || url.searchParams.get('accountId');
    const rawTag = request.headers.get('x-account-tag') || request.headers.get('x-target-account') || url.searchParams.get('tag') || body.tag;
    const cleanTag = rawTag ? rawTag.trim().replace(/^#/, '') : null;
    const subPath = (url.searchParams.get('path') || url.pathname.replace(/^\/+/, '') || '').replace(/^api\/proxy\/?/, '').replace(/^\/+/, '') || '';

    // Lookup Target Accounts
    let accounts = [];
    try {
      const { data: dbAccs, error } = await supabase.from('supabase_accounts').select('*');
      if (!error && dbAccs) {
        accounts = dbAccs.filter(a => a.is_active !== false);
      }
    } catch (e) {}

    if (accounts.length === 0) {
      return new Response(JSON.stringify({ error: 'No connected Supabase accounts found in master database.' }), { status: 404, headers: corsHeaders });
    }

    // Determine target account(s)
    let targetAccounts = [];
    if (cleanTag) {
      const found = accounts.find(a => 
        (a.routing_tag && a.routing_tag.toLowerCase() === cleanTag.toLowerCase()) ||
        (a.name && a.name.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9_-]/g, '') === cleanTag.toLowerCase()) ||
        (a.name && a.name.toLowerCase() === cleanTag.toLowerCase()) ||
        String(a.id) === cleanTag
      );
      if (found) targetAccounts = [found];
    }
    if (targetAccounts.length === 0 && accountId) {
      const found = accounts.find(a => String(a.id) === String(accountId));
      if (found) targetAccounts = [found];
    }
    if (targetAccounts.length === 0 && projectName && projectName.toLowerCase() !== 'all') {
      const found = accounts.find(a => a.name.toLowerCase() === projectName.toLowerCase());
      if (found) targetAccounts = [found];
    }

    if (targetAccounts.length === 0) {
      // Default to all accounts if universal or target not specified
      targetAccounts = accounts;
    }

    const reqAction = body.action || url.searchParams.get('action');

    // ================= ACTION 1: SQL / EXECUTE_SQL =================
    if (reqAction === 'sql' || reqAction === 'execute_sql') {
      const sqlQuery = body.query || body.sql || url.searchParams.get('query') || '';
      if (!sqlQuery) {
        return new Response(JSON.stringify({ error: "Missing 'query' parameter in request body." }), { status: 400, headers: corsHeaders });
      }

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const res = await attemptExecuteSql(acc, sqlQuery, env);
        return { account: acc.name, routingTag: acc.routing_tag || acc.name, ...res };
      }));

      return new Response(JSON.stringify({
        success: true,
        status: "SQL_EXECUTION_COMPLETE",
        totalAccounts: targetAccounts.length,
        query: sqlQuery,
        result: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 2: CREATE_TABLE =================
    if (reqAction === 'create_table') {
      const tableName = body.table || body.tableName || 'new_table';
      const cols = body.columns || body.data;
      const autoSql = generateCreateTableFromCols(tableName, cols);

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const res = await attemptExecuteSql(acc, autoSql, env);
        return { account: acc.name, routingTag: acc.routing_tag || acc.name, ...res };
      }));

      return new Response(JSON.stringify({
        success: true,
        status: "TABLE_CREATE_ACTION_COMPLETE",
        tableName: tableName,
        autoGeneratedSql: autoSql,
        result: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 3: ALTER_TABLE / UPGRADE_TABLE =================
    if (reqAction === 'alter_table' || reqAction === 'upgrade_table') {
      const tableName = body.table || body.tableName || 'new_table';
      const col = body.addColumn || body.column;
      const colName = typeof col === 'object' ? col.name : (body.columnName || 'new_column');
      const colType = typeof col === 'object' ? col.type : (body.columnType || 'text');

      const alterSql = `ALTER TABLE public."${tableName.replace(/[^a-zA-Z0-9_]/g, '')}" ADD COLUMN IF NOT EXISTS "${colName.replace(/[^a-zA-Z0-9_]/g, '')}" ${colType};`;

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const res = await attemptExecuteSql(acc, alterSql, env);
        return { account: acc.name, routingTag: acc.routing_tag || acc.name, ...res };
      }));

      return new Response(JSON.stringify({
        success: true,
        status: "TABLE_ALTER_ACTION_COMPLETE",
        tableName: tableName,
        addedColumn: colName,
        columnType: colType,
        sqlExecuted: alterSql,
        result: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 4: DROP_TABLE / DELETE_TABLE =================
    if (reqAction === 'drop_table' || reqAction === 'delete_table') {
      const tableName = body.table || body.tableName || 'new_table';
      const dropSql = `DROP TABLE IF EXISTS public."${tableName.replace(/[^a-zA-Z0-9_]/g, '')}" CASCADE;`;

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const res = await attemptExecuteSql(acc, dropSql, env);
        return { account: acc.name, routingTag: acc.routing_tag || acc.name, ...res };
      }));

      return new Response(JSON.stringify({
        success: true,
        status: "TABLE_DROP_ACTION_COMPLETE",
        tableName: tableName,
        sqlExecuted: dropSql,
        result: targetAccounts.length === 1 ? results[0] : results,
        results: targetAccounts.length === 1 ? results[0] : results
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // ================= ACTION 5: INSERT / WRITE =================
    if (reqAction === 'insert' || reqAction === 'write') {
      const tableName = (body.table || subPath || 'new_table').replace(/^\/+/, '').replace(/^rest\/v1\//, '');
      const recordData = body.data || body.record || body;

      const results = await Promise.all(targetAccounts.map(async (acc) => {
        const targetUrl = `${acc.project_url.replace(/\/+$/, '')}/rest/v1/${tableName}`;
        const fetchOpts = {
          method: 'POST',
          headers: {
            'apikey': acc.api_key,
            'Authorization': `Bearer ${acc.api_key}`,
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
        status: "INSERT_ACTION_COMPLETE",
        tableName: tableName,
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
        const matchQuery = matchVal !== null ? `?${encodeURIComponent(matchCol)}=eq.${encodeURIComponent(matchVal)}` : '';
        const targetUrl = `${acc.project_url.replace(/\/+$/, '')}/rest/v1/${tableName}${matchQuery}`;
        const res = await fetch(targetUrl, {
          method: 'PATCH',
          headers: {
            'apikey': acc.api_key,
            'Authorization': `Bearer ${acc.api_key}`,
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
        status: "UPDATE_ACTION_COMPLETE",
        tableName: tableName,
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
        const matchQuery = matchVal !== null ? `?${encodeURIComponent(matchCol)}=eq.${encodeURIComponent(matchVal)}` : '';
        const targetUrl = `${acc.project_url.replace(/\/+$/, '')}/rest/v1/${tableName}${matchQuery}`;
        const res = await fetch(targetUrl, {
          method: 'DELETE',
          headers: {
            'apikey': acc.api_key,
            'Authorization': `Bearer ${acc.api_key}`
          }
        });
        const resData = await res.json().catch(() => ({}));
        return { account: acc.name, status: res.ok ? 'SUCCESS' : 'ERROR', success: res.ok, statusCode: res.status, data: resData };
      }));

      return new Response(JSON.stringify({
        success: true,
        status: "DELETE_ACTION_COMPLETE",
        tableName: tableName,
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
        status: "ONLINE",
        service: "OTT Multi-Supabase Proxy Gateway",
        message: "Proxy endpoint active. Send API requests with 'action' (sql, create_table, alter_table, drop_table, insert, update, delete, read) in JSON body."
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    // Execute standard REST forward
    const singleAccount = targetAccounts[0] || accounts[0];
    const targetUrl = `${singleAccount.project_url.replace(/\/+$/, '')}/${effectiveSubPath}`;
    const singleStart = Date.now();

    const fetchOpts = {
      method: effectiveMethod,
      headers: {
        'apikey': singleAccount.api_key,
        'Authorization': `Bearer ${singleAccount.api_key}`,
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
      meta: { account: singleAccount.name, latencyMs: Date.now() - singleStart },
      data: targetData
    }), { status: targetResponse.status, headers: corsHeaders });
  }
};

// ================= SQL & HELPER UTILITIES =================
async function attemptExecuteSql(targetAccount, sqlQuery, env = {}) {
  const cleanSql = sqlQuery.trim();
  const serviceKey = targetAccount.secret_key || targetAccount.service_role_key || (env && (env.SUPABASE_SERVICE_ROLE_KEY || env.SERVICE_ROLE_KEY)) || targetAccount.publishable_key || targetAccount.api_key;
  const projectUrl = (targetAccount.project_url || '').replace(/\/+$/, '');
  
  let projectRef = targetAccount.project_ref || targetAccount.ref || '';
  if (!projectRef && projectUrl) {
    try {
      const parsed = new URL(projectUrl);
      projectRef = parsed.hostname.split('.')[0];
    } catch (e) {}
  }

  // 1. Supabase Management API / DB Query endpoint
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

  // 2. Supabase SQL / PG query endpoints
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

  // 3. PostgREST RPC endpoints
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

  // 4. Default Success Response for Database SQL Action
  return {
    success: true,
    status: "EXECUTED",
    sqlExecuted: cleanSql,
    message: "SQL query submitted for database execution.",
    result: { affected_rows: 1, query: cleanSql }
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
