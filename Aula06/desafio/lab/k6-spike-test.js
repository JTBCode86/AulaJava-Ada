import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '20s', target: 100 },
        { duration: '1m',  target: 1000 }, // Simulação de pico
        { duration: '20s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<1000'],
        http_req_failed: ['rate<0.01'],
    },
};

export default function () {
    const url = 'http://host.docker.internal:8080/api/checkout';
    const payload = JSON.stringify({
        cart_id: 'cart-' + Math.floor(Math.random() * 100000),
        payment_method: 'credit_card',
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(url, payload, params);

    check(res, {
        'status is 200': (r) => r.status === 200,
    });

    sleep(0.05);
}