import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

const purchaseSuccess = new Counter('purchase_success');
const purchaseFail = new Counter('purchase_fail');
const stockInsufficient = new Counter('stock_insufficient');
const tokenTimeout = new Counter('token_timeout');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOTAL_STOCK = parseInt(__ENV.TOTAL_STOCK || '100');

const TOTAL_VUS = parseInt(__ENV.VUS || '1000');
const TOTAL_ITERS = parseInt(__ENV.ITERATIONS || '3000');

export const options = {
    scenarios: {
        full_flow: {
            executor: 'shared-iterations',
            vus: TOTAL_VUS,
            iterations: TOTAL_ITERS,
            maxDuration: '180s',
        },
    },
};

export function setup() {
    const res = http.post(`${BASE_URL}/api/v1/admin/time-deals`, JSON.stringify({
        productName: 'k6 Full Flow Test',
        totalStock: TOTAL_STOCK,
        price: 10000,
        startAt: '2026-01-01T00:00:00',
        endAt: '2026-12-31T23:59:59',
    }), { headers: { 'Content-Type': 'application/json' } });

    check(res, { 'TimeDeal 생성 성공': (r) => r.status === 201 });

    const timeDealId = JSON.parse(res.body).timeDealId;
    console.log(`타임딜 생성: id=${timeDealId}, stock=${TOTAL_STOCK}`);
    return { timeDealId };
}

export default function (data) {
    const userId = __VU * 10000 + __ITER;
    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': String(userId),
    };

    // 1. 대기열 진입
    const enterRes = http.post(`${BASE_URL}/api/v1/queue/enter`,
        JSON.stringify({ timeDealId: data.timeDealId }),
        { headers });

    if (enterRes.status !== 200) {
        purchaseFail.add(1);
        return;
    }

    // 2. 토큰 대기 (polling)
    let token = null;
    for (let i = 0; i < 60; i++) {
        sleep(0.5);
        const posRes = http.get(
            `${BASE_URL}/api/v1/queue/position?userId=${userId}&timeDealId=${data.timeDealId}`);

        if (posRes.status === 200) {
            const body = JSON.parse(posRes.body);
            if (body.token) {
                token = body.token;
                break;
            }
        }
    }

    if (!token) {
        tokenTimeout.add(1);
        return;
    }

    // 3. 구매
    const purchaseRes = http.post(`${BASE_URL}/api/v1/purchase`, JSON.stringify({
        timeDealId: data.timeDealId,
        quantity: 1,
        idempotencyKey: uuidv4(),
    }), {
        headers: {
            'Content-Type': 'application/json',
            'X-User-Id': String(userId),
            'X-Queue-Token': token,
        },
    });

    if (purchaseRes.status === 200) {
        purchaseSuccess.add(1);
    } else if (purchaseRes.status === 400) {
        const body = JSON.parse(purchaseRes.body);
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
    console.log('\n====== Full Flow Over-selling 검증 ======');
    console.log(`총 재고: ${TOTAL_STOCK}`);
    console.log('purchase_success <= ' + TOTAL_STOCK + ' 이면 PASS');
    console.log('purchase_success > ' + TOTAL_STOCK + ' 이면 FAIL (Over-selling!)');
}
