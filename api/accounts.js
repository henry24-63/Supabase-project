const { createClient } = require('@supabase/supabase-js');

// Support environment variables with safe defaults from user's Master project
const MASTER_URL = process.env.MASTER_URL || 'https://sjdlejiwqmkyfkvmodgd.supabase.co';
const MASTER_KEY = process.env.MASTER_KEY || 'sb_publishable_YAv53hX7HhPdxBcsiZpKEA_JHKMmGAt';

function setCors(res) {
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,PATCH,DELETE,POST,PUT');
  res.setHeader('Access-Control-Allow-Headers', 'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version, Authorization, x-project-name, apikey');
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
    return res.status(500).json({ error: 'Supabase client initialization failed: ' + initErr.message });
  }

  // Parse body safely whether parsed by Vercel or raw string
  let body = req.body;
  if (typeof body === 'string') {
    try { body = JSON.parse(body); } catch (e) { body = {}; }
  }
  body = body || {};

  // GET: Fetch all accounts
  if (req.method === 'GET') {
    try {
      const { data, error } = await supabase
        .from('supabase_accounts')
        .select('*')
        .order('name', { ascending: true });

      if (error) {
        return res.status(500).json({ error: error.message, code: error.code, details: error.details });
      }
      return res.status(200).json(data || []);
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  // POST: Add new account
  if (req.method === 'POST') {
    try {
      const { name, project_url, api_key, db_password } = body;
      if (!name || !project_url || !api_key) {
        return res.status(400).json({ 
          error: 'Missing required fields: project name, project url, and api key are required.',
          received: { name: !!name, project_url: !!project_url, api_key: !!api_key }
        });
      }

      // Clean project_url (strip trailing slashes, clean protocol)
      let cleanUrl = project_url.trim().replace(/\/+$/, '');
      if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
        cleanUrl = 'https://' + cleanUrl;
      }

      const newRecord = { 
        name: name.trim(), 
        project_url: cleanUrl, 
        api_key: api_key.trim(), 
        db_password: (db_password || '').trim(),
        routing_tag: (body.routing_tag || '').trim(),
        is_active: body.is_active !== undefined ? body.is_active : true
      };

      const { data, error } = await supabase
        .from('supabase_accounts')
        .insert([newRecord])
        .select();

      if (error) {
        return res.status(500).json({ 
          error: error.message, 
          code: error.code, 
          details: error.details, 
          hint: error.hint || 'Check if table supabase_accounts has permissions granted to anon role.'
        });
      }
      return res.status(201).json({ success: true, data: data?.[0] || newRecord });
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  // PATCH / PUT: Update account status or details
  if (req.method === 'PATCH' || req.method === 'PUT') {
    try {
      const id = req.query.id || body.id;
      if (!id) {
        return res.status(400).json({ error: 'Account id is required for update' });
      }
      const updateData = {};
      if (body.name !== undefined) updateData.name = body.name;
      if (body.project_url !== undefined) updateData.project_url = body.project_url;
      if (body.api_key !== undefined) updateData.api_key = body.api_key;
      if (body.db_password !== undefined) updateData.db_password = body.db_password;
      if (body.routing_tag !== undefined) updateData.routing_tag = body.routing_tag;
      if (body.is_active !== undefined) updateData.is_active = body.is_active;

      const { data, error } = await supabase
        .from('supabase_accounts')
        .update(updateData)
        .eq('id', id)
        .select();

      if (error) {
        return res.status(500).json({ error: error.message, details: error });
      }
      return res.status(200).json({ success: true, data: data?.[0] });
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  // DELETE: Delete account by id, name, or project_url
  if (req.method === 'DELETE') {
    try {
      const id = req.query.id || body.id;
      const name = req.query.name || body.name;
      const projectUrl = req.query.project_url || req.query.url || body.project_url || body.url;

      if (!id && !name && !projectUrl) {
        return res.status(400).json({ error: 'Account id, name, or project_url is required for deletion' });
      }

      let deleteBuilder = supabase.from('supabase_accounts').delete();
      if (id) {
        deleteBuilder = deleteBuilder.eq('id', id);
      } else if (name) {
        deleteBuilder = deleteBuilder.eq('name', name);
      } else if (projectUrl) {
        deleteBuilder = deleteBuilder.eq('project_url', projectUrl);
      }

      const { data, error } = await deleteBuilder;

      // Redundant purge to ensure no zombie records
      if (name && id) {
        try { await supabase.from('supabase_accounts').delete().eq('name', name); } catch(e) {}
      }
      if (projectUrl && id) {
        try { await supabase.from('supabase_accounts').delete().eq('project_url', projectUrl); } catch(e) {}
      }

      if (error) {
        return res.status(500).json({ error: error.message, details: error });
      }
      return res.status(200).json({ success: true, message: 'Account deleted successfully' });
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
