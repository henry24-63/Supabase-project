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
        db_password: (db_password || '').trim() 
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

  // DELETE: Delete account by id
  if (req.method === 'DELETE') {
    try {
      const id = req.query.id || body.id;
      if (!id) {
        return res.status(400).json({ error: 'Account id is required for deletion' });
      }

      const { data, error } = await supabase
        .from('supabase_accounts')
        .delete()
        .eq('id', id);

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
