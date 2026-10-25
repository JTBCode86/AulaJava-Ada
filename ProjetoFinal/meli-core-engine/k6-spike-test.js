import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '5s', target: 20 },   // Aquecimento
    { duration: '10s', target: 300 },  // PICO SÚBITO (Spike)
    { duration: '20s', target: 300 },  // Sustentação do pico
    { duration: '5s', target: 0 },     // Resfriamento
  ],
};

export default function () {
  const res = http.post('http://localhost:8080/api/v1/checkout?amount=150.00');
  check(res, {
    'status is 200': (r) => r.status === 200,
    'resposta contém fallback ou sucesso': (r) => r.body.includes('SUCCESS') || r.body.includes('FALLBACK'),
  });
  sleep(0.05);
}