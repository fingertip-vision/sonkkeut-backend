"""Create the demo store through the same APIs used by the owner UI, keeping its key out of Git."""
import argparse
import json
from pathlib import Path
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--url', default='http://127.0.0.1:18080')
    parser.add_argument('--credentials', required=True)
    args = parser.parse_args()
    path = Path(args.credentials)
    if path.exists():
        store = json.loads(path.read_text(encoding='utf-8'))
    else:
        request = Request(args.url + '/api/stores', data=json.dumps({'name': '카페 손끝 (시연)', 'kiosk_vendor': 'sonkkeut-mock'}).encode(), headers={'Content-Type': 'application/json'}, method='POST')
        with urlopen(request) as response:
            store = json.load(response)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(store, ensure_ascii=False), encoding='utf-8')
    items = [{'name': name, 'price': price, 'category': '커피' if name != '치즈케이크' else '디저트',
              'aliases': aliases, 'options': [{'group': '온도', 'values': ['HOT', 'ICE']}], 'sold_out': False}
             for name, price, aliases in [('아메리카노', 4500, ['아아']), ('카페라떼', 5000, ['라떼']), ('바닐라라떼', 5500, []), ('치즈케이크', 6500, [])]]
    req = Request(args.url + f"/api/stores/{store['code']}/menu", data=json.dumps({'items': items}).encode(), headers={'Content-Type': 'application/json', 'X-Owner-Key': store['owner_key']}, method='PUT')
    with urlopen(req) as response:
        menu = json.load(response)
    print(json.dumps({'store_code': store['code'], 'menu_version': menu['menu_version'], 'items': len(menu['items'])}))


if __name__ == '__main__':
    main()
