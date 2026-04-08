const express = require('express');
const fs = require('fs');
const path = require('path');

const app = express();
app.use(express.json());

const API_KEY = 'nokta-super-secret-2026';
const DB_PATH = path.join(__dirname, 'data.json');

function auth(req, res, next) {
    if (req.headers['x-nokta-key'] !== API_KEY) return res.status(401).json({error: 'Unauthorized'});
    next();
}

function loadDB() {
    if (!fs.existsSync(DB_PATH)) return {playtime: []};
    try { return JSON.parse(fs.readFileSync(DB_PATH, 'utf8')); } catch { return {playtime: []}; }
}

function saveDB(db) {
    fs.writeFileSync(DB_PATH, JSON.stringify(db, null, 2));
}

// POST /api/playtime
app.post('/api/playtime', auth, (req, res) => {
    const {username, ms} = req.body;
    if (!username || !ms) return res.status(400).json({error: 'username ve ms gerekli'});
    const db = loadDB();
    const today = new Date().toISOString().split('T')[0];
    db.playtime.push({username, ms, date: today, ts: Date.now()});
    saveDB(db);
    console.log(`📊 ${username} → ${ms}ms kaydedildi`);
    res.json({ok: true});
});

// Leaderboard yardımcı
function leaderboard(entries) {
    const map = {};
    for (const e of entries) {
        map[e.username] = (map[e.username] || 0) + e.ms;
    }
    return Object.entries(map)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 5)
        .map(([username, ms]) => ({username, ms}));
}

app.get('/api/leaderboard/daily', (req, res) => {
    const db = loadDB();
    const today = new Date().toISOString().split('T')[0];
    res.json(leaderboard(db.playtime.filter(e => e.date === today)));
});

app.get('/api/leaderboard/weekly', (req, res) => {
    const db = loadDB();
    const week = Date.now() - 7 * 24 * 60 * 60 * 1000;
    res.json(leaderboard(db.playtime.filter(e => e.ts >= week)));
});

app.get('/api/leaderboard/monthly', (req, res) => {
    const db = loadDB();
    const month = Date.now() - 30 * 24 * 60 * 60 * 1000;
    res.json(leaderboard(db.playtime.filter(e => e.ts >= month)));
});

app.get('/api/leaderboard/alltime', (req, res) => {
    const db = loadDB();
    res.json(leaderboard(db.playtime));
});

app.get('/api/health', (req, res) => res.json({ok: true}));

app.listen(3000, () => console.log('🚀 Nokta API → http://localhost:3000'));
