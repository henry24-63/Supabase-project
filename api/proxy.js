const { createClient } = require('@supabase/supabase-js');
const axios = require('axios');

const MASTER_URL = process.env.MASTER_URL || 'https://sjdlejiwqmkyfkvmodgd.supabase.co';
const MASTER_KEY = process.env.MASTER_KEY || 'sb_publishable_YAv53hX7HhPdxBcsiZpKEA_JHKMmGAt';

function setCors(res) {
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
  res.setHeader('Access-Control-Allow-Headers', 'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version, Authorization, x-project-name, x-account-id, apikey');
}

module.exports = async (req, res) => {
  setCors(res);
  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  let supabase;
  try {
    supabase = createClient(MASTER_URL, MASTER_KEY);
  } catch (initErr) {
    return res.status(500).json({ error: 'Supabase initialization failed: ' + initErr.message });
  }

  const queryType = req.query.action || req.body?.action;

  // ACTION: PING / KEEP-ALIVE ALL ACCOUNTS
  if (queryType === 'ping') {
    try {
      const { data: accounts, error } = await supabase.from('supabase_accounts').select('*');
      if (error) return res.status(500).json({ error: error.message });

      const startTime = Date.now();
      const pingPromises = (accounts || []).map(async (acc) => {
        const itemStart = Date.now();
        try {
          const resp = await axios.get(`${acc.project_url.replace(/\/+$/, '')}/rest/v1/`, {
            headers: {
              'apikey': acc.api_key,
              'Authorization': `Bearer ${acc.api_key}`
            },
            timeout: 7000
          });
          return {
            id: acc.id,
            name: acc.name,
            url: acc.project_url,
            status: 'ALIVE',
            statusCode: resp.status,
            latencyMs: Date.now() - itemStart
          };
        } catch (pingErr) {
          return {
            id: acc.id,
            name: acc.name,
            url: acc.project_url,
            status: pingErr.response ? 'ALIVE' : 'FAILED',
            statusCode: pingErr.response ? pingErr.response.status : 0,
            latencyMs: Date.now() - itemStart,
            error: pingErr.message
          };
        }
      });

      const results = await Promise.all(pingPromises);
      return res.status(200).json({
        total: results.length,
        totalTimeMs: Date.now() - startTime,
        accounts: results
      });
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  // ACTION: QUERY / PROXY
  const projectName = req.headers['x-project-name'] || req.query.project || req.body?.projectName;
  const accountId = req.headers['x-account-id'] || req.query.accountId;
  const subPath = (req.query.path || '').replace(/^\/+/, '') || '';

  // If no specific project is requested or 'all' is requested -> MULTI-ACCOUNT AGGREGATION
  if (!projectName || projectName.toLowerCase() === 'all') {
    try {
      const { data: accounts, error } = await supabase.from('supabase_accounts').select('*');
      if (error) return res.status(500).json({ error: error.message });
      if (!accounts || accounts.length === 0) {
        return res.status(200).json({ message: 'No accounts registered yet in Master Supabase.', results: [] });
      }

      const multiStart = Date.now();
      const forwardPromises = accounts.map(async (acc) => {
        const targetUrl = `${acc.project_url.replace(/\/+$/, '')}/rest/v1/${subPath}`;
        const accStart = Date.now();
        try {
          const targetRes = await axios({
            method: req.method === 'OPTIONS' ? 'GET' : req.method,
            url: targetUrl,
            data: req.body?.data || req.body,
            params: req.query.params ? JSON.parse(req.query.params) : undefined,
            headers: {
              'apikey': acc.api_key,
              'Authorization': `Bearer ${acc.api_key}`,
              'Content-Type': 'application/json'
            },
            timeout: 8000
          });
          return {
            account: acc.name,
            accountId: acc.id,
            status: 'SUCCESS',
            latencyMs: Date.now() - accStart,
            data: targetRes.data
          };
        } catch (callErr) {
          return {
            account: acc.name,
            accountId: acc.id,
            status: 'ERROR',
            latencyMs: Date.now() - accStart,
            error: callErr.response?.data || callErr.message
          };
        }
      });

      const mergedResults = await Promise.all(forwardPromises);
      return res.status(200).json({
        meta: {
          mode: 'AGGREGATED_MULTI_ACCOUNT',
          totalAccounts: accounts.length,
          executionTimeMs: Date.now() - multiStart
        },
        results: mergedResults
      });
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  // SINGLE TARGET PROJECT ROUTING
  try {
    let queryBuilder = supabase.from('supabase_accounts').select('*');
    if (accountId) {
      queryBuilder = queryBuilder.eq('id', accountId);
    } else {
      queryBuilder = queryBuilder.eq('name', projectName);
    }

    const { data: accounts, error } = await queryBuilder;
    if (error || !accounts || accounts.length === 0) {
      return res.status(404).json({ error: `Project '${projectName || accountId}' not found in Master Supabase.` });
    }

    const targetAccount = accounts[0];
    const targetUrl = `${targetAccount.project_url.replace(/\/+$/, '')}/rest/v1/${subPath}`;

    const singleStart = Date.now();
    const targetResponse = await axios({
      method: req.method,
      url: targetUrl,
      data: req.body?.data || req.body,
      headers: {
        'apikey': targetAccount.api_key,
        'Authorization': `Bearer ${targetAccount.api_key}`,
        'Content-Type': 'application/json'
      },
      timeout: 10000
    });

    return res.status(targetResponse.status).json({
      meta: {
        account: targetAccount.name,
        latencyMs: Date.now() - singleStart
      },
      data: targetResponse.data
    });
  } catch (err) {
    const status = err.response ? err.response.status : 500;
    const msg = err.response ? err.response.data : err.message;
    return res.status(status).json({ error: 'Proxy forwarding failed', details: msg });
  }
};
