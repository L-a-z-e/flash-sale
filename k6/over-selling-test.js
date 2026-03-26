import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const purchaseSuccess = new Counter('purchase_success');
const stockInsufficient = new Counter('stock_insufficient');
const purchaseFail = new Counter('purchase_fail');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_STOCK = parseInt(__ENV.TOTAL_STOCK || '100');

export const options = {
    scenarios: {
        concurrent_purchase: {
            executor: 'shared-iterations',
            vus: 1000,
            iterations: 1500,
            maxDuration: '60s',
        },
    },
};

export function setup() {
    const res = http.post(`${BASE_URL}/api/v1/admin/time-deals`, JSON.stringify({
        productName: 'k6 Over-selling Test',
        totalStock: TOTAL_STOCK,
        price: 10000,
        startAt: '2026-01-01T00:00:00',
        endAt: '2026-12-31T23:59:59',
    }), { headers: { 'Content-Type': 'application/json' } });

    check(res, { 'TimeDeal 생성 성공': (r) => r.status === 201 });

    const timeDealId = JSON.parse(res.body).timeDealId;
    console.log(`타임딜 생성 완료: id=${timeDealId}, stock=${TOTAL_STOCK}`);
    return { timeDealId };
}

export default function (data) {
    const userId = __VU * 10000 + __ITER;

    const res = http.post(`${BASE_URL}/api/v1/purchase`, JSON.stringify({
        timeDealId: data.timeDealId,
        quantity: 1,
        idempotencyKey: uuidv4(),
    }), {
        headers: {
            'Content-Type': 'application/json',
            'X-User-Id': String(userId),
        },
    });

    if (res.status === 200) {
        purchaseSuccess.add(1);
    } else if (res.status === 400) {
        const body = JSON.parse(res.body);
        if (body.code === 'STOCK_INSUFFICIENT') {
            stockInsufficient.add(1);
        } else {
            purchaseFail.add(1);
        }
    } else {
        purchaseFail.add(1);
    }
}

export function teardown(data) {
    console.log('\n====== Over-selling 검증 결과 ======');
    console.log(`총 재고: ${TOTAL_STOCK}`);
    console.log('purchase_success <= ' + TOTAL_STOCK + ' 이면 PASS');
    console.log('purchase_success > ' + TOTAL_STOCK + ' 이면 FAIL (Over-selling!)');
}
