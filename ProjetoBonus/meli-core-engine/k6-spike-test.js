import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '15s', target: 20 },   // Aquecimento gradual e suave
    { duration: '30s', target: 50 },   // Carga moderada e estável
    { duration: '30s', target: 50 },   // Sustentação
    { duration: '10s', target: 0 },    // Encerramento
  ],
  // Aumenta o timeout HTTP global para 10s para evitar quebras por lentidão temporária
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<3000'], // Permite até 3 segundos no percentil 95
  },
};

export default function () {
  const url = 'http://host.docker.internal:8080/api/v1/checkout';

  const payload = JSON.stringify({
    customerId: `USER-${__VU}`,
    productId: 'PROD-MELI-01',
    quantity: 1,
    amount: 150.00
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'X-Idempotency-Key': `IDEM-${__VU}-${__ITER}-${Date.now()}`
    },
    timeout: '10s', // Força o k6 a esperar até 10 segundos pela resposta do servidor
  };

  const res = http.post(url, payload, params);

  check(res, {
    'status é 200 ou 202': (r) => r.status === 200 || r.status === 202,
    'resposta contém fallback ou sucesso': (r) => {
      if (!r.body) return false;
      return r.body.includes('SUCCESS') || r.body.includes('FALLBACK') || r.body.includes('processing');
    },
  });

  sleep(0.15); // Pausa ligeiramente maior para garantir estabilidade total
}