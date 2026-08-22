import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const app = express();
const PORT = 3000;

app.use(express.static(__dirname));
app.use(express.static(path.join(__dirname, 'app/src/main/assets')));

app.get('/api/health', (req, res) => {
  res.json({ status: 'ok' });
});

app.get('/privacy-policy', (req, res) => {
  res.sendFile(path.join(__dirname, 'privacy-policy.html'));
});

app.get('*', (req, res) => {
  res.sendFile(path.join(__dirname, 'index.html'));
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Server running on http://0.0.0.0:${PORT}`);
});
