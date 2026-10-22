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
    return res.status(500).json({ error: 'Supabase initialization failed: ' + initErr.message });
  }

  // GET: Fetch all accounts
  if (req.method === 'GET') {
    try {
      const { data, error } = await supabase
        .from('supabase_accounts')
        .select('*')
        .order('name', { ascending: true });

      if (error) {
        return res.status(500).json({ error: error.message, details: error });
      }
      return res.status(200).json(data || []);
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  // POST: Add new account
  if (req.method === 'POST') {
    try {
      const { name, project_url, api_key, db_password } = req.body || {};
      if (!name || !project_url || !api_key) {
        return res.status(400).json({ error: 'Missing required fields: name, project_url, and api_key are required.' });
      }

      // Clean project_url (strip trailing slashes)
      const cleanUrl = project_url.trim().replace(/\/+$/, '');

      const { data, error } = await supabase
        .from('supabase_accounts')
        .insert([{ 
          name: name.trim(), 
          project_url: cleanUrl, 
          api_key: api_key.trim(), 
          db_password: (db_password || '').trim() 
        }])
        .select();

      if (error) {
        return res.status(500).json({ error: error.message });
      }
      return res.status(201).json({ success: true, data: data?.[0] || data });
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  // DELETE: Delete account by id
  if (req.method === 'DELETE') {
    try {
      const id = req.query.id || (req.body && req.body.id);
      if (!id) {
        return res.status(400).json({ error: 'Account id is required for deletion' });
      }

      const { data, error } = await supabase
        .from('supabase_accounts')
        .delete()
        .eq('id', id);

      if (error) {
        return res.status(500).json({ error: error.message });
      }
      return res.status(200).json({ success: true, message: 'Account deleted successfully' });
    } catch (err) {
      return res.status(500).json({ error: err.message });
    }
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
