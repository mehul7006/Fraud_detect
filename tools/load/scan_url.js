// 8.7 load test: 200 requests/second on POST /scan/url for 60 s, goal p95 < 300 ms.
// Runs against a separate throwaway backend (tools/load/run.sh) - no Safe Browsing key, rate limit raised.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://host.docker.internal:18081/api/v1';
export const options = {
  scenarios: {
    scan: { executor: 'constant-arrival-rate', rate: Number(__ENV.RATE || 200), timeUnit: '1s', duration: __ENV.DURATION || '60s',
            preAllocatedVUs: 100, maxVUs: 400 },
  },
  thresholds: { 'http_req_duration{name:scan}': ['p(95)<300'], 'http_req_failed{name:scan}': ['rate<0.01'] },
};

export function setup() {
  const tokens = [];
  for (let i = 0; i < 20; i++) {
    const r = http.post(`${BASE}/auth/register`, JSON.stringify({ email: `load${i}-${Date.now()}@t.com`, password: 'password123' }),
      { headers: { 'Content-Type': 'application/json' } });
    tokens.push(r.json('token'));
  }
  return { tokens };
}

const urls = ['https://www.sbi.co.in/web/personal-banking', 'http://192.168.4.20/sbi-kyc-update/verify.apk',
  'http://paytm-kyc-verify.top/login', 'https://bit.ly/3xYzAbc', 'https://www.flipkart.com/offers',
  'http://xn--fpkart-3ya.com/sale', 'https://example.org/'];

export default function (data) {
  const t = data.tokens[__VU % data.tokens.length];
  const u = urls[Math.floor(Math.random() * urls.length)] + '?q=' + Math.floor(Math.random() * 1e6);
  const r = http.post(`${BASE}/scan/url`, JSON.stringify({ url: u, sourceApp: 'load' }),
    { headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${t}` }, tags: { name: 'scan' } });
  check(r, { '200': (x) => x.status === 200 });
}
