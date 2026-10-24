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

    // 3. PROXY / QUERY / READ / WRITE ROUTING
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
            const targetRes = await fetch(targetUrl, fetchOpts);
            const resData = await targetRes.json().catch(() => ({}));
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

      const targetResponse = await fetch(targetUrl, fetchOpts);
      const targetData = await targetResponse.json().catch(() => ({}));

      return new Response(JSON.stringify({
        meta: { account: targetAccount.name, latencyMs: Date.now() - singleStart },
        data: targetData
      }), { status: targetResponse.status, headers: corsHeaders });

    } catch (err) {
      return new Response(JSON.stringify({ error: 'Proxy forwarding failed', details: err.message }), { status: 500, headers: corsHeaders });
    }
  }
};
