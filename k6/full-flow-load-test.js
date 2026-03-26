import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

var purchaseSuccess = new Counter('purchase_success');
var purchaseFail = new Counter('purchase_fail');
var stockInsufficient = new Counter('stock_insufficient');
var tokenTimeout = new Counter('token_timeout');

var BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
var TOTAL_STOCK = parseInt(__ENV.TOTAL_STOCK || '1000');

export var options = {
    scenarios: {
        load_test: {
            executor: 'constant-vus',
            vus: parseInt(__ENV.VUS || '3000'),
            duration: '60s',
        },
    },
};

export function setup() {
    var res = http.post(BASE_URL + '/api/v1/admin/time-deals', JSON.stringify({
        productName: 'k6 Load Test',
        totalStock: TOTAL_STOCK,
        price: 10000,
        startAt: '2026-01-01T00:00:00',
        endAt: '2026-12-31T23:59:59',
    }), { headers: { 'Content-Type': 'application/json' } });

    check(res, { 'TimeDeal 생성 성공': function(r) { return r.status === 201; } });

    var timeDealId = JSON.parse(res.body).timeDealId;
    console.log('타임딜 생성: id=' + timeDealId + ', stock=' + TOTAL_STOCK);
    return { timeDealId: timeDealId };
}

export default function (data) {
    var userId = __VU * 100000 + __ITER;
    var headers = {
        'Content-Type': 'application/json',
        'X-User-Id': String(userId),
    };

    // 1. 대기열 진입
    var enterRes = http.post(BASE_URL + '/api/v1/queue/enter',
        JSON.stringify({ timeDealId: data.timeDealId }),
        { headers: headers });

    if (enterRes.status !== 200) {
        purchaseFail.add(1);
        return;
    }

    // 2. 토큰 대기 (polling)
    var token = null;
    for (var i = 0; i < 120; i++) {
        sleep(0.5);
        var posRes = http.get(
            BASE_URL + '/api/v1/queue/position?userId=' + userId + '&timeDealId=' + data.timeDealId);

        if (posRes.status === 200) {
            var body = JSON.parse(posRes.body);
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
    var purchaseRes = http.post(BASE_URL + '/api/v1/purchase', JSON.stringify({
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
        var errBody = JSON.parse(purchaseRes.body);
        if (errBody.code === 'STOCK_INSUFFICIENT') {
            stockInsufficient.add(1);
        } else {
            purchaseFail.add(1);
        }
    } else {
        purchaseFail.add(1);
    }
}

export function teardown(data) {
    console.log('\n====== Load Test 검증 ======');
    console.log('총 재고: ' + TOTAL_STOCK);
    console.log('purchase_success <= ' + TOTAL_STOCK + ' 이면 PASS');
}
