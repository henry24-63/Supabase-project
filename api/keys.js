const crypto = require('crypto');

function setCors(res) {
  res.setHeader('Access-Control-Allow-Credentials', 'true');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,POST,DELETE');
  res.setHeader('Access-Control-Allow-Headers', 'X-CSRF-Token, X-Requested-With, Accept, Content-Type, Authorization');
}

// In-memory / ephemeral cache of generated keys with fallback storage
let activeKeys = [
  {
    key: 'omni_live_master_default_2026',
    name: 'Primary Master Key',
    created: '2026-10-22',
    status: 'ACTIVE',
    rateLimit: 'UNLIMITED'
  }
];

module.exports = async (req, res) => {
  setCors(res);
  if (req.method === 'OPTIONS') return res.status(200).end();

  if (req.method === 'GET') {
    return res.status(200).json(activeKeys);
  }

  if (req.method === 'POST') {
    const { name } = req.body || {};
    const randomHex = crypto.randomBytes(16).toString('hex');
    const newKey = `omni_live_${randomHex}`;
    const keyRecord = {
      key: newKey,
      name: name || 'Master Router Key ' + (activeKeys.length + 1),
      created: new Date().toISOString().split('T')[0],
      status: 'ACTIVE',
      rateLimit: 'UNLIMITED'
    };
    activeKeys.push(keyRecord);
    return res.status(201).json({ success: true, keyRecord });
  }

  if (req.method === 'DELETE') {
    const { key } = req.query;
    activeKeys = activeKeys.filter(k => k.key !== key);
    return res.status(200).json({ success: true, message: 'Key revoked' });
  }

  return res.status(405).json({ error: 'Method not allowed' });
};
