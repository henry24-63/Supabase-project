// api/proxy.js
const { createClient } = require('@supabase/supabase-js');
const axios = require('axios');
const dotenv = require('dotenv');

// Load environment variables from .env
dotenv.config();

const supabase = createClient(process.env.MASTER_URL, process.env.MASTER_KEY);

module.exports = async (req, res) => {
  // Simple check: get project name from headers or body
  const projectName = req.headers['x-project-name'] || req.body.projectName;

  if (!projectName) return res.status(400).send('Project name required');

  // 1. Fetch project details from Master Supabase
  const { data, error } = await supabase
    .from('supabase_accounts')
    .select('*')
    .eq('name', projectName)
    .single();

  if (error || !data) return res.status(404).send('Project not found');

  // 2. Forward request to Target Supabase
  try {
    const targetResponse = await axios({
      method: req.method,
      url: `${data.project_url}/rest/v1/${req.url.replace('/api/proxy', '')}`,
      data: req.body.data,
      headers: { 
        'apikey': data.api_key, 
        'Authorization': `Bearer ${data.api_key}`,
        'Content-Type': 'application/json'
      }
    });
    res.json(targetResponse.data);
  } catch (err) {
    res.status(500).send('Proxy Error: ' + err.message);
  }
};
