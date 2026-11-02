const http = require('http');
const fs = require('fs');
const path = require('path');

const root = __dirname;
const port = Number(process.env.PORT || 8899);

const types = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
};

const orders = [
  'SO-1001 · 待发货 · ￥128.00',
  'SO-1002 · 已完成 · ￥256.00',
  'SO-1003 · 待付款 · ￥512.00',
];
let orderSequence = 1003;
let pollCounter = 0;

function sendJson(res, status, payload) {
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
  });
  res.end(JSON.stringify(payload));
}

function readJsonBody(req, callback) {
  let raw = '';
  req.on('data', (chunk) => {
    raw += chunk;
    if (raw.length > 1e6) req.destroy();
  });
  req.on('end', () => {
    if (!raw) {
      callback({});
      return;
    }
    try {
      callback(JSON.parse(raw));
    } catch (error) {
      callback({});
    }
  });
}

function handleApi(req, res, pathname, query) {
  if (pathname === '/api/refresh') {
    if (req.method !== 'POST') {
      sendJson(res, 405, { error: 'method not allowed' });
      return;
    }
    sendJson(res, 200, { ok: true, refreshedAt: new Date().toISOString() });
    return;
  }

  if (pathname === '/api/slow') {
    const ms = Math.min(Math.max(Number(query.get('ms') || 1200), 0), 5000);
    setTimeout(() => {
      sendJson(res, 200, { items: orders, total: orders.length, delayedMs: ms });
    }, ms);
    return;
  }

  if (pathname === '/api/error') {
    sendJson(res, 500, { error: '订单服务异常（模拟 500）' });
    return;
  }

  if (pathname === '/api/unauthorized') {
    sendJson(res, 401, { error: '登录已过期（模拟 401）' });
    return;
  }

  if (pathname === '/api/empty') {
    sendJson(res, 200, { items: [], total: 0 });
    return;
  }

  if (pathname === '/api/state') {
    pollCounter += 1;
    sendJson(res, 200, { counter: pollCounter, at: new Date().toISOString() });
    return;
  }

  if (pathname === '/api/orders') {
    if (req.method === 'GET') {
      sendJson(res, 200, { items: orders, total: orders.length });
      return;
    }
    if (req.method === 'POST') {
      readJsonBody(req, (body) => {
        orderSequence += 1;
        const orderNo = 'SO-' + orderSequence;
        orders.push(orderNo + ' · 待发货 · ￥' + (Number(body.amount) || 199) + '.00');
        sendJson(res, 201, { orderNo, created: true, count: orders.length });
      });
      return;
    }
    sendJson(res, 405, { error: 'method not allowed' });
    return;
  }

  if (pathname === '/api/submit') {
    if (req.method !== 'POST') {
      sendJson(res, 405, { error: 'method not allowed' });
      return;
    }
    readJsonBody(req, (body) => {
      const name = String(body.name || '').trim();
      if (!name || body.fail === true) {
        sendJson(res, 422, { error: '提交失败：缺少收货人或服务端拒绝' });
        return;
      }
      orderSequence += 1;
      sendJson(res, 201, { orderNo: 'SO-' + orderSequence, name, created: true });
    });
    return;
  }

  sendJson(res, 404, { error: 'unknown api' });
}

http.createServer((req, res) => {
  const requestUrl = new URL(req.url || '/', 'http://localhost');
  const pathname = decodeURIComponent(requestUrl.pathname);

  if (pathname.startsWith('/api/')) {
    handleApi(req, res, pathname, requestUrl.searchParams);
    return;
  }

  if (req.method !== 'GET' && req.method !== 'HEAD') {
    res.writeHead(405, { 'content-type': 'text/plain; charset=utf-8' });
    res.end('method not allowed');
    return;
  }

  const relative = pathname === '/' ? 'index.html' : pathname.replace(/^\/+/, '');
  const file = path.join(root, relative);
  if (!file.startsWith(root)) {
    res.writeHead(403);
    res.end('forbidden');
    return;
  }
  fs.readFile(file, (error, data) => {
    if (error) {
      // 故意返回 404，用于演示破图与控制台资源加载失败。
      res.writeHead(404, { 'content-type': 'text/plain; charset=utf-8' });
      res.end('not found');
      return;
    }
    res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream' });
    res.end(req.method === 'HEAD' ? undefined : data);
  });
}).listen(port, '0.0.0.0', () => {
  console.log(`patrol e2e site listening on ${port}`);
});
