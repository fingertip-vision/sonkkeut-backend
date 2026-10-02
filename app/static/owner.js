// 점주 메뉴 등록 화면
const $ = (s) => document.querySelector(s)
let cur = null // {code, key}

function storeGet(k) { try { return localStorage.getItem(k) } catch { return null } }
function storeSet(k, v) { try { v == null ? localStorage.removeItem(k) : localStorage.setItem(k, v) } catch {} }

async function api(path, opt = {}) {
  const headers = { 'Content-Type': 'application/json', ...(opt.headers || {}) }
  if (cur?.key) headers['X-Owner-Key'] = cur.key
  const r = await fetch(path, { ...opt, headers })
  if (r.status === 204) return null
  const data = await r.json().catch(() => ({}))
  if (!r.ok) {
    const d = data.detail
    throw new Error(Array.isArray(d) ? d.map((e) => `${e.loc?.slice(-2).join('.')}: ${e.msg}`).join(' / ') : d || r.statusText)
  }
  return data
}

const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]))

function optToText(opts) { return (opts || []).map((o) => `${o.group}:${o.values.join('/')}`).join('; ') }
function textToOpt(t) {
  return t.split(';').map((s) => s.trim()).filter(Boolean).map((s) => {
    const [g, v = ''] = s.split(':')
    return { group: g.trim(), values: v.split('/').map((x) => x.trim()).filter(Boolean) }
  })
}

function addRow(it = {}) {
  const tr = document.createElement('tr')
  tr.innerHTML = `
    <td><input data-k="category" maxlength="40" aria-label="분류"></td>
    <td><input data-k="name" maxlength="80" aria-label="메뉴 이름" required></td>
    <td><input data-k="price" type="number" min="0" step="100" aria-label="가격"></td>
    <td><input data-k="aliases" aria-label="다른 이름"></td>
    <td><input data-k="options" aria-label="옵션"></td>
    <td class="c"><input data-k="sold_out" type="checkbox" aria-label="품절"></td>
    <td><button class="ghost del" aria-label="삭제">✕</button></td>`
  const q = (k) => tr.querySelector(`[data-k="${k}"]`)
  q('category').value = it.category || ''
  q('name').value = it.name || ''
  q('price').value = it.price ?? ''
  q('aliases').value = (it.aliases || []).join(', ')
  q('options').value = optToText(it.options)
  q('sold_out').checked = !!it.sold_out
  tr.querySelector('.del').onclick = () => { tr.remove(); count() }
  $('#rows').appendChild(tr)
  count()
  return tr
}

function count() { $('#count').textContent = `${$('#rows').children.length}개` }

function readRows() {
  return [...$('#rows').children].map((tr) => {
    const q = (k) => tr.querySelector(`[data-k="${k}"]`)
    const price = q('price').value.trim()
    return {
      category: q('category').value.trim(),
      name: q('name').value.trim(),
      price: price === '' ? null : Number(price),
      aliases: q('aliases').value.split(',').map((s) => s.trim()).filter(Boolean),
      options: textToOpt(q('options').value),
      sold_out: q('sold_out').checked,
    }
  }).filter((r) => r.name)
}

async function open(code, key) {
  cur = { code: code.trim().toUpperCase(), key: key.trim() }
  const [store, menu] = await Promise.all([api(`/api/stores/${cur.code}`), api(`/api/stores/${cur.code}/menu`)])
  // 키가 맞는지 확인 (메뉴 조회는 공개라서 수정 권한을 따로 확인한다)
  await api(`/api/stores/${cur.code}`, { method: 'PATCH', body: '{}' })
  $('#s-name').textContent = store.name
  $('#s-code').textContent = store.code
  $('#s-ver').textContent = store.menu_version
  $('#rows').innerHTML = ''
  menu.items.forEach(addRow)
  if (!menu.items.length) addRow()
  $('#login').hidden = true
  $('#editor').hidden = false
}

$('#f-login').onsubmit = async (e) => {
  e.preventDefault()
  const f = new FormData(e.target)
  $('#msg').textContent = ''
  try {
    await open(f.get('code'), f.get('key'))
    if (f.get('remember')) { storeSet('sk.code', cur.code); storeSet('sk.key', cur.key) }
  } catch (err) { cur = null; $('#msg').textContent = err.message }
}

$('#f-create').onsubmit = async (e) => {
  e.preventDefault()
  const f = Object.fromEntries(new FormData(e.target))
  for (const k of ['lat', 'lng']) f[k] = f[k] === '' ? null : Number(f[k])
  for (const k of ['address', 'kiosk_vendor']) if (!f[k]) f[k] = null
  $('#msg').textContent = ''
  try {
    const s = await api('/api/stores', { method: 'POST', body: JSON.stringify(f) })
    const box = $('#created')
    box.hidden = false
    box.innerHTML = `<b>${esc(s.name)}</b> 매장을 만들었습니다.<br>
      매장 코드 <b class="mono big">${esc(s.code)}</b> · 점주 키 <b class="mono">${esc(s.owner_key)}</b><br>
      <span class="small">점주 키는 지금만 보입니다. 꼭 따로 적어 두세요. 매장 코드는 키오스크 옆에 붙여 두면 앱이 바로 찾습니다.</span>`
    document.querySelector('#f-login [name=code]').value = s.code
    document.querySelector('#f-login [name=key]').value = s.owner_key
  } catch (err) { $('#msg').textContent = err.message }
}

$('#b-geo').onclick = () => {
  navigator.geolocation?.getCurrentPosition((p) => {
    document.querySelector('#f-create [name=lat]').value = p.coords.latitude.toFixed(6)
    document.querySelector('#f-create [name=lng]').value = p.coords.longitude.toFixed(6)
  }, () => { $('#msg').textContent = '위치를 가져오지 못했습니다' })
}

$('#b-add').onclick = () => addRow().querySelector('[data-k=name]').focus()
$('#b-logout').onclick = () => { cur = null; storeSet('sk.code', null); storeSet('sk.key', null); $('#editor').hidden = true; $('#login').hidden = false }

$('#b-save').onclick = async () => {
  const items = readRows()
  $('#emsg').className = ''
  $('#emsg').textContent = '저장 중…'
  try {
    const m = await api(`/api/stores/${cur.code}/menu`, { method: 'PUT', body: JSON.stringify({ items }) })
    $('#s-ver').textContent = m.menu_version
    $('#emsg').className = 'ok'
    $('#emsg').textContent = `저장했습니다. 메뉴 ${m.items.length}개, 버전 ${m.menu_version}. 앱이 다음 실행 때 새 메뉴를 받습니다.`
  } catch (err) { $('#emsg').className = 'err'; $('#emsg').textContent = err.message }
}

$('#b-csv').onclick = () => { $('#csv').value = ''; $('#d-csv').showModal() }
$('#d-csv').addEventListener('close', () => {
  if ($('#d-csv').returnValue !== 'ok') return
  for (const line of $('#csv').value.split(/\r?\n/)) {
    const c = line.split(/\t|,(?=(?:[^"]*"[^"]*")*[^"]*$)/).map((s) => s.replace(/^"|"$/g, '').trim())
    if (!c[1]) continue
    const price = Number(String(c[2] || '').replace(/[^\d]/g, ''))
    addRow({ category: c[0], name: c[1], price: Number.isFinite(price) && c[2] ? price : null,
             aliases: (c[3] || '').split(/[,/]/).map((s) => s.trim()).filter(Boolean) })
  }
})

const savedCode = storeGet('sk.code'), savedKey = storeGet('sk.key')
if (savedCode && savedKey) open(savedCode, savedKey).catch(() => { cur = null })
