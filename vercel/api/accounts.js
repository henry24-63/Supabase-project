// api/accounts.js
const { createClient } = require('@supabase/supabase-js');
const supabase = createClient(process.env.MASTER_URL, process.env.MASTER_KEY);

module.exports = async (req, res) => {
  if (req.method === 'GET') {
    const { data, error } = await supabase.from('supabase_accounts').select('*');
    return res.status(error ? 500 : 200).json(data);
  }
  if (req.method === 'POST') {
    const { name, project_url, api_key, db_password } = req.body;
    const { data, error } = await supabase.from('supabase_accounts').insert([{ name, project_url, api_key, db_password }]);
    return res.status(error ? 500 : 200).json({ success: !error, data });
  }
  if (req.method === 'DELETE') {
    const { id } = req.query;
    const { data, error } = await supabase.from('supabase_accounts').delete().eq('id', id);
    return res.status(error ? 500 : 200).json({ success: !error });
  }
};
