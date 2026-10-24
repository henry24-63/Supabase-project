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

    // Serve HTML dashboard frontend at root '/'
    if (url.pathname === '/' || url.pathname === '') {
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

    // 5. PROXY / QUERY / READ / WRITE ROUTING
    let body = {};
    try {
      if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
        body = await request.json().catch(() => ({}));
      }
    } catch (e) {}

    const projectName = request.headers.get('x-project-name') || url.searchParams.get('project') || body.projectName;
    const accountId = request.headers.get('x-account-id') || url.searchParams.get('accountId');
    const rawTag = request.headers.get('x-account-tag') || request.headers.get('x-target-account') || url.searchParams.get('tag') || body.tag;
    const cleanTag = rawTag ? rawTag.trim().replace(/^#/, '') : null;
    const subPath = (url.searchParams.get('path') || url.pathname.replace(/^\/+/, '') || '').replace(/^api\/proxy\/?/, '').replace(/^\/+/, '') || '';

    let effectiveSubPath = subPath;
    let effectiveMethod = method;
    let effectiveData = body.data || body;

    if (body.action) {
      if (body.action === 'write' && body.table) {
        effectiveSubPath = `rest/v1/${body.table.replace(/^\/+/, '')}`;
        effectiveMethod = 'POST';
        effectiveData = body.data;
      } else if (body.action === 'read' && body.table) {
        const selectQuery = body.select ? `?select=${encodeURIComponent(body.select)}` : '';
        effectiveSubPath = `rest/v1/${body.table.replace(/^\/+/, '')}${selectQuery}`;
        effectiveMethod = 'GET';
        effectiveData = undefined;
      } else if (body.action === 'query' && body.path) {
        effectiveSubPath = body.path.replace(/^\/+/, '');
      } else if (body.action === 'setup_table' || action === 'setup_table') {
        const tableName = body.table || url.searchParams.get('table') || 'new_table';
        const sampleData = body.data || body.sampleData || { name: 'sample', value: 100 };
        const autoSql = generateCreateTableSql(tableName, sampleData);
        return new Response(JSON.stringify({
          status: "AUTO_SCHEMA_GENERATED",
          tableName: tableName,
          autoGeneratedSql: autoSql,
          message: `Smart system auto-inferred table schema for '${tableName}'.`
        }, null, 2), { status: 200, headers: corsHeaders });
      }
    }

    if (!effectiveSubPath) {
      return new Response(JSON.stringify({
        status: "ONLINE",
        service: "OTT Multi-Supabase Proxy Gateway",
        message: "Proxy endpoint active. Send API requests here."
      }, null, 2), { status: 200, headers: corsHeaders });
    }

    if ((!projectName || projectName.toLowerCase() === 'all') && !accountId && !cleanTag) {
      try {
        const { data: allAccounts, error } = await supabase.from('supabase_accounts').select('*');
        if (error) return new Response(JSON.stringify({ error: error.message }), { status: 500, headers: corsHeaders });
        if (!allAccounts || allAccounts.length === 0) {
          return new Response(JSON.stringify({ message: 'No accounts registered yet.', results: [] }), { status: 200, headers: corsHeaders });
        }

        const accounts = allAccounts.filter(a => a.is_active !== false);
        const multiStart = Date.now();

        const forwardPromises = accounts.map(async (acc) => {
          const targetUrl = `${acc.project_url.replace(/\/+$/, '')}/${effectiveSubPath}`;
          const accStart = Date.now();
          try {
            const fetchOpts = {
              method: effectiveMethod,
              headers: {
                'apikey': acc.api_key,
                'Authorization': `Bearer ${acc.api_key}`,
                'Content-Type': 'application/json',
                'Prefer': effectiveMethod === 'POST' ? 'return=representation' : undefined
              }
            };
            if (effectiveData && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(effectiveMethod)) {
              fetchOpts.body = JSON.stringify(effectiveData);
            }
            let targetRes = await fetch(targetUrl, fetchOpts);
            let resData = await targetRes.json().catch(() => ({}));

            // Check if table missing
            const errStr = (targetRes.status + ' ' + JSON.stringify(resData)).toLowerCase();
            const isTableMissing = targetRes.status === 404 || targetRes.status === 400 || errStr.includes('does not exist') || errStr.includes('relation') || errStr.includes('could not find the table');

            if (isTableMissing && effectiveMethod === 'POST') {
              const pathParts = effectiveSubPath.split('/');
              const tableName = pathParts[pathParts.length - 1] || 'new_table';
              const provResult = await attemptAutoProvisionTable(acc, tableName, effectiveData);

              // Retry write
              const retryRes = await fetch(targetUrl, fetchOpts);
              const retryData = await retryRes.json().catch(() => ({}));
              if (retryRes.ok) {
                return {
                  account: acc.name,
                  accountId: acc.id,
                  routingTag: acc.routing_tag || acc.name,
                  status: 'SUCCESS',
                  autoProvisioned: true,
                  tableName: tableName,
                  latencyMs: Date.now() - accStart,
                  data: retryData
                };
              } else {
                return {
                  account: acc.name,
                  accountId: acc.id,
                  routingTag: acc.routing_tag || acc.name,
                  status: 'TABLE_AUTO_SQL_READY',
                  tableName: tableName,
                  autoGeneratedSql: provResult.sql,
                  latencyMs: Date.now() - accStart,
                  error: `Table '${tableName}' missing. Smart SQL schema ready.`
                };
              }
            }

            return {
              account: acc.name,
              accountId: acc.id,
              routingTag: acc.routing_tag || acc.name,
              status: targetRes.ok ? 'SUCCESS' : 'ERROR',
              latencyMs: Date.now() - accStart,
              data: resData
            };
          } catch (callErr) {
            return {
              account: acc.name,
              accountId: acc.id,
              routingTag: acc.routing_tag || acc.name,
              status: 'ERROR',
              latencyMs: Date.now() - accStart,
              error: callErr.message
            };
          }
        });

        const mergedResults = await Promise.all(forwardPromises);
        return new Response(JSON.stringify({
          meta: { mode: 'AGGREGATED_MULTI_ACCOUNT', totalAccounts: accounts.length, executionTimeMs: Date.now() - multiStart },
          results: mergedResults
        }), { status: 200, headers: corsHeaders });
      } catch (err) {
        return new Response(JSON.stringify({ error: err.message }), { status: 500, headers: corsHeaders });
      }
    }

    try {
      const { data: accounts, error } = await supabase.from('supabase_accounts').select('*');
      if (error || !accounts || accounts.length === 0) {
        return new Response(JSON.stringify({ error: 'No accounts registered yet.' }), { status: 404, headers: corsHeaders });
      }

      let targetAccount = null;
      if (cleanTag) {
        targetAccount = accounts.find(a => 
          (a.routing_tag && a.routing_tag.toLowerCase() === cleanTag.toLowerCase()) ||
          (a.name && a.name.toLowerCase().replace(/\s+/g, '-').replace(/[^a-z0-9_-]/g, '') === cleanTag.toLowerCase()) ||
          (a.name && a.name.toLowerCase() === cleanTag.toLowerCase()) ||
          String(a.id) === cleanTag
        );
      }
      if (!targetAccount && accountId) {
        targetAccount = accounts.find(a => String(a.id) === String(accountId));
      }
      if (!targetAccount && projectName && projectName.toLowerCase() !== 'all') {
        targetAccount = accounts.find(a => name.toLowerCase() === projectName.toLowerCase());
      }

      if (!targetAccount) {
        return new Response(JSON.stringify({ error: `Target account '${cleanTag || projectName || accountId}' not found.` }), { status: 404, headers: corsHeaders });
      }

      const targetUrl = `${targetAccount.project_url.replace(/\/+$/, '')}/${effectiveSubPath}`;
      const singleStart = Date.now();

      const fetchOpts = {
        method: effectiveMethod,
        headers: {
          'apikey': targetAccount.api_key,
          'Authorization': `Bearer ${targetAccount.api_key}`,
          'Content-Type': 'application/json',
          'Prefer': effectiveMethod === 'POST' ? 'return=representation' : undefined
        }
      };
      if (effectiveData && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(effectiveMethod)) {
        fetchOpts.body = JSON.stringify(effectiveData);
      }

      let targetResponse = await fetch(targetUrl, fetchOpts);
      let targetData = await targetResponse.json().catch(() => ({}));

      // SMART AUTO-TABLE PROVISIONING INTERCEPTION
      const errStr = (targetResponse.status + ' ' + JSON.stringify(targetData)).toLowerCase();
      const isTableMissing = targetResponse.status === 404 || targetResponse.status === 400 ||
                             errStr.includes('does not exist') || errStr.includes('relation') || errStr.includes('could not find the table');

      if (isTableMissing && effectiveMethod === 'POST') {
        const pathParts = effectiveSubPath.split('/');
        const tableName = pathParts[pathParts.length - 1] || 'new_table';
        const provResult = await attemptAutoProvisionTable(targetAccount, tableName, effectiveData);

        // Retry write
        const retryRes = await fetch(targetUrl, fetchOpts);
        const retryData = await retryRes.json().catch(() => ({}));

        if (retryRes.ok) {
          return new Response(JSON.stringify({
            meta: { 
              account: targetAccount.name, 
              latencyMs: Date.now() - singleStart, 
              autoProvisioned: true, 
              tableCreated: tableName,
              sqlExecuted: provResult.sql 
            },
            data: retryData
          }), { status: retryRes.status, headers: corsHeaders });
        } else {
          return new Response(JSON.stringify({
            status: "TABLE_NOT_FOUND_AUTO_SQL_READY",
            message: `Table '${tableName}' was missing on '${targetAccount.name}'. Smart system auto-generated the exact SQL schema below.`,
            tableName: tableName,
            account: targetAccount.name,
            autoGeneratedSql: provResult.sql,
            originalError: targetData,
            instructions: "Run autoGeneratedSql in Supabase SQL Editor once to auto-create table & enable unrestricted writes!"
          }, null, 2), { status: 200, headers: corsHeaders });
        }
      }

      return new Response(JSON.stringify({
        meta: { account: targetAccount.name, latencyMs: Date.now() - singleStart },
        data: targetData
      }), { status: targetResponse.status, headers: corsHeaders });

    } catch (err) {
      return new Response(JSON.stringify({ error: 'Proxy forwarding failed', details: err.message }), { status: 500, headers: corsHeaders });
    }
  }
};

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

  const sql = `CREATE TABLE IF NOT EXISTS public."${cleanTable}" (\n  ${columns.join(',\n  ')}\n);\nALTER TABLE public."${cleanTable}" DISABLE ROW LEVEL SECURITY;\nGRANT ALL ON TABLE public."${cleanTable}" TO anon, authenticated, service_role;`;
  return sql;
}

async function attemptAutoProvisionTable(targetAccount, tableName, sampleData) {
  const sql = generateCreateTableSql(tableName, sampleData);
  const rpcEndpoints = ['rest/v1/rpc/exec_sql', 'rest/v1/rpc/sql', 'rest/v1/rpc/create_table'];
  
  for (const endpoint of rpcEndpoints) {
    try {
      const res = await fetch(`${targetAccount.project_url.replace(/\/+$/, '')}/${endpoint}`, {
        method: 'POST',
        headers: {
          'apikey': targetAccount.api_key,
          'Authorization': `Bearer ${targetAccount.api_key}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ query: sql, sql: sql, table_name: tableName })
      });
      if (res.ok) {
        return { success: true, sql };
      }
    } catch (e) {}
  }
  return { success: false, sql };
}
