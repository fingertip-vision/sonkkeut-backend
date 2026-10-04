/* Local, deterministic kiosk simulation. No camera, model, network or real payment. */
(function (global) {
  "use strict";

  const WIDTH = 540, HEIGHT = 700, DWELL_SECONDS = 0.3;
  const STATE_LABELS = { S0: "준비", S1: "화면 탐색", S2: "화면 인식", S3: "주문 입력 및 확인", S4: "손끝 유도", S5: "결과 확인", S6: "안내 완료", SE: "복구" };
  const SCREEN_LABELS = { start: "주문 시작", menu: "메뉴 선택", option: "옵션 선택", cart: "장바구니", payment: "결제 안내", complete: "가상 주문 완료" };
  const MENU = [
    { id: "americano", name: "아메리카노", price: 4500, category: "coffee", icon: "☕" },
    { id: "latte", name: "카페라떼", price: 5000, category: "coffee", icon: "☕" },
    { id: "vanilla", name: "바닐라라떼", price: 5500, category: "coffee", icon: "☕" },
    { id: "cheesecake", name: "치즈케이크", price: 6500, category: "dessert", icon: "🍰" },
    { id: "cookie", name: "초콜릿 쿠키", price: 3200, category: "dessert", icon: "🍪" }
  ];
  const SCENARIOS = {
    coffee: {
      name: "아메리카노 · 포장 · ICE", description: "포장 → 아메리카노 → ICE → 담기 → 주문 확인 → 카드 안내 → 가상 승인. 총 4,500원을 검증합니다.",
      milestones: ["포장 주문", "아메리카노 ICE 1잔", "장바구니 4,500원", "결제 안내와 가상 완료"],
      actions: ["service:takeout", "menu:americano", "temp:ICE", "add", "cart", "checkout", "payment:card", "payment:simulate"],
      expected: [{ id: "americano", temperature: "ICE", size: "REGULAR", quantity: 1 }], total: 4500, service: "takeout"
    },
    latte: {
      name: "라떼 · 매장 · LARGE · 2잔", description: "매장 → 카페라떼 → HOT / LARGE → 수량 2잔 → 담기 → 결제. 크기 추가금 700원과 합계 11,400원을 검증합니다.",
      milestones: ["매장 주문", "카페라떼 HOT / LARGE 2잔", "장바구니 11,400원", "결제 안내와 가상 완료"],
      actions: ["service:dinein", "menu:latte", "size:LARGE", "quantity:plus", "add", "cart", "checkout", "payment:card", "payment:simulate"],
      expected: [{ id: "latte", temperature: "HOT", size: "LARGE", quantity: 2 }], total: 11400, service: "dinein"
    },
    recovery: {
      name: "품절 대체 + 손 유실 복구 + 디저트", description: "라떼 품절을 감지해 아메리카노 ICE로 변경합니다. 손 유실, 잘못 누름, 화면 변화 없음에서 복구한 뒤 치즈케이크를 추가합니다. 총 11,000원을 검증합니다.",
      milestones: ["품절 감지 후 아메리카노로 대체", "손 유실 / 잘못 누름 / no_change 복구", "음료 + 디저트 11,000원", "결제 안내와 가상 완료"],
      actions: ["service:takeout", "menu:latte", "menu:americano", "temp:ICE", "add", "category:dessert", "menu:cheesecake", "add", "cart", "checkout", "payment:card", "payment:simulate"],
      faults: { 1: "sold_out", 3: "hand_lost", 5: "wrong_click", 9: "no_change" },
      expected: [{ id: "americano", temperature: "ICE", size: "REGULAR", quantity: 1 }, { id: "cheesecake", temperature: null, size: null, quantity: 1 }], total: 11000, service: "takeout", recoveryCount: 4
    }
  };
  const ERROR_LABELS = {
    hand_lost: "손끝이 화면에서 사라졌습니다", screen_lost: "키오스크 화면을 찾지 못했습니다", low_confidence: "손끝 추적 신뢰도가 낮습니다", low_screen_confidence: "화면 인식 신뢰도가 낮습니다", sold_out: "선택한 메뉴가 품절입니다", wrong_click: "계획과 다른 버튼이 눌렸습니다", no_change: "누른 뒤 예상한 화면 변화가 없습니다", cart_empty: "장바구니가 비어 있습니다", cart_limit: "서로 다른 메뉴는 4종류까지 담을 수 있습니다", quantity_limit: "같은 메뉴·옵션은 9개까지 담을 수 있습니다", invalid_target: "현재 화면에서 목표 버튼을 찾지 못했습니다", timeout: "단계 진행 시간이 초과되었습니다"
  };
  const EVENT_LABELS = { session_start: "로컬 세션 시작", scenario_start: "시나리오 시작", state_change: "상태 전환", plan_created: "안내 계획 생성", selection_confirmed: "이미 선택된 상태 확인", dwell_start: "안전 영역 진입", dwell_reset: "누름 대기 초기화", press_allowed: "0.3초 유지 · 누름 허용", click_applied: "키오스크 버튼 적용", step_success: "예상 결과 확인", error_detected: "복구 필요", recovery_complete: "다시 안내", scenario_complete: "주문 검증 완료", fault_armed: "오류 주입 예약", scenario_paused: "시나리오 일시 정지", plan_cancelled: "기존 안내 취소" };
  const clone = value => JSON.parse(JSON.stringify(value));
  const money = value => value.toLocaleString("ko-KR") + "원";
  const clamp = (value, min, max) => Math.max(min, Math.min(max, value));
  function initialModel() { return { screen: "start", service: null, category: "coffee", current: null, options: { temperature: "HOT", size: "REGULAR", quantity: 1 }, cart: [], payment: { method: null, status: "not_started", approvals: 0 }, soldOut: [], revision: 0 }; }
  function itemTotal(item) { return (item.price + (item.size === "LARGE" ? 700 : 0)) * item.quantity; }
  function orderTotal(model) { return model.cart.reduce((sum, item) => sum + itemTotal(item), 0); }
  function semanticModel(model) {
    return { screen: model.screen, service: model.service, category: model.category, current: model.current, options: model.options, cart: model.cart, payment: model.payment };
  }
  function fingerprint(model) { return JSON.stringify(semanticModel(model)); }
  function rect(x, y, width, height) { return { x, y, width, height }; }
  function createButton(id, label, bounds, expected, options) { return Object.assign({ id, label, bounds, expected, selected: false, enabled: true, kind: "choice" }, options || {}); }
  function getButtons(model) {
    const buttons = [];
    const button = (id, label, x, y, w, h, expected, options) => buttons.push(createButton(id, label, rect(x, y, w, h), expected, options));
    if (model.screen === "start") {
      button("service:dinein", "매장에서 먹기", 40, 245, 220, 190, "매장 주문으로 설정되고 메뉴 화면으로 이동");
      button("service:takeout", "포장하기", 280, 245, 220, 190, "포장 주문으로 설정되고 메뉴 화면으로 이동");
    }
    if (model.screen === "menu") {
      button("category:coffee", "커피", 40, 155, 220, 55, "커피 메뉴만 표시", { selected: model.category === "coffee" });
      button("category:dessert", "디저트", 280, 155, 220, 55, "디저트 메뉴만 표시", { selected: model.category === "dessert" });
      MENU.filter(item => item.category === model.category).forEach((item, i) => {
        button("menu:" + item.id, item.name, 40 + (i % 2) * 240, 240 + Math.floor(i / 2) * 140, 220, 120, item.name + " 옵션 화면으로 이동", { item, soldOut: model.soldOut.includes(item.id), subtitle: money(item.price) });
      });
      button("cart", "장바구니 확인 · " + model.cart.reduce((sum, item) => sum + item.quantity, 0) + "개", 40, 570, 460, 70, "장바구니의 메뉴·수량·금액 확인 화면으로 이동", { kind: "action" });
    }
    if (model.screen === "option") {
      const item = MENU.find(value => value.id === model.current), drink = item.category === "coffee";
      if (drink) {
        button("temp:HOT", "HOT", 40, 190, 220, 70, "현재 메뉴의 온도가 HOT으로 설정", { selected: model.options.temperature === "HOT" });
        button("temp:ICE", "ICE", 280, 190, 220, 70, "현재 메뉴의 온도가 ICE로 설정", { selected: model.options.temperature === "ICE" });
        button("size:REGULAR", "REGULAR", 40, 315, 220, 70, "현재 메뉴의 크기가 REGULAR로 설정", { selected: model.options.size === "REGULAR", subtitle: "추가금 없음" });
        button("size:LARGE", "LARGE", 280, 315, 220, 70, "현재 메뉴의 크기가 LARGE로 설정되고 잔당 700원 추가", { selected: model.options.size === "LARGE", subtitle: "+700원" });
      }
      button("quantity:minus", "−", 40, 447, 95, 60, "주문 수량 1 감소 (최소 1개)", { enabled: model.options.quantity > 1 });
      button("quantity:plus", "+", 405, 447, 95, 60, "주문 수량 1 증가 (최대 9개)", { enabled: model.options.quantity < 9 });
      button("back", "메뉴로", 40, 575, 220, 65, "선택 중인 옵션을 취소하고 메뉴 화면으로 이동");
      button("add", "담기", 280, 575, 220, 65, "선택한 메뉴·옵션·수량을 장바구니에 담고 메뉴 화면으로 이동", { kind: "action" });
    }
    if (model.screen === "cart") {
      model.cart.forEach((item, i) => {
        const y = 180 + i * 80;
        button("cart:minus:" + i, "−", 340, y, 46, 40, "이 항목 수량 1 감소; 0개가 되면 항목 제거");
        button("cart:plus:" + i, "+", 392, y, 46, 40, "이 항목 수량 1 증가", { enabled: item.quantity < 9 });
        button("cart:remove:" + i, "×", 444, y, 46, 40, "이 항목을 장바구니에서 제거");
      });
      button("back", "계속 주문", 40, 575, 220, 65, "장바구니를 유지하고 메뉴 화면으로 이동");
      button("checkout", "결제 안내로", 280, 575, 220, 65, "최종 금액을 유지하고 카드 결제 안내 화면으로 이동", { kind: "action", enabled: model.cart.length > 0 });
    }
    if (model.screen === "payment") {
      if (model.payment.status === "awaiting_method") {
        button("payment:card", "카드로 결제", 40, 340, 460, 105, "카드 삽입 안내를 표시 (실제 카드 인식 없음)", { kind: "action" });
        button("back", "주문 확인으로", 40, 560, 460, 65, "결제를 진행하지 않고 장바구니로 돌아감");
      } else {
        button("payment:simulate", "가상 승인 확인", 40, 405, 460, 90, "가상 주문 완료 표시. 실제 결제·청구는 없음", { kind: "action" });
        button("back", "취소하고 주문 확인", 40, 560, 460, 65, "가상 카드 단계를 취소하고 장바구니로 돌아감");
      }
    }
    if (model.screen === "complete") button("restart", "새 주문 시작", 40, 520, 460, 90, "기존 주문을 비우고 주문 시작 화면으로 이동", { kind: "action" });
    return buttons;
  }
  function applyAction(model, id) {
    const available = getButtons(model).find(button => button.id === id);
    if (!available || !available.enabled) return { ok: false, error: "invalid_target" };
    const [kind, value, index] = id.split(":");
    if (kind === "service") { model.service = value; model.screen = "menu"; }
    else if (kind === "category") model.category = value;
    else if (kind === "menu") {
      if (model.soldOut.includes(value)) return { ok: false, error: "sold_out" };
      model.current = value; model.options = { temperature: "HOT", size: "REGULAR", quantity: 1 }; model.screen = "option";
    }
    else if (kind === "temp") model.options.temperature = value;
    else if (kind === "size") model.options.size = value;
    else if (kind === "quantity") model.options.quantity = clamp(model.options.quantity + (value === "plus" ? 1 : -1), 1, 9);
    else if (id === "add") {
      const item = MENU.find(value => value.id === model.current), drink = item.category === "coffee";
      if (model.soldOut.includes(item.id)) return { ok: false, error: "sold_out" };
      const entry = { id: item.id, name: item.name, price: item.price, temperature: drink ? model.options.temperature : null, size: drink ? model.options.size : null, quantity: model.options.quantity };
      const match = model.cart.find(row => row.id === entry.id && row.temperature === entry.temperature && row.size === entry.size);
      if (!match && model.cart.length >= 4) return { ok: false, error: "cart_limit" };
      if (match && match.quantity + entry.quantity > 9) return { ok: false, error: "quantity_limit" };
      if (match) match.quantity += entry.quantity; else model.cart.push(entry);
      model.screen = "menu"; model.current = null;
    }
    else if (kind === "cart" && value) {
      const position = Number(index), entry = model.cart[position];
      if (!entry) return { ok: false, error: "invalid_target" };
      if (value === "remove") model.cart.splice(position, 1);
      else { entry.quantity += value === "plus" ? 1 : -1; if (entry.quantity === 0) model.cart.splice(position, 1); }
    }
    else if (id === "cart") model.screen = "cart";
    else if (id === "checkout") {
      if (!model.cart.length) return { ok: false, error: "cart_empty" };
      model.screen = "payment"; model.payment.method = null; model.payment.status = "awaiting_method";
    }
    else if (id === "payment:card") { model.payment.method = "card"; model.payment.status = "awaiting_simulation"; }
    else if (id === "payment:simulate") { model.payment.status = "simulated"; model.payment.approvals += 1; model.screen = "complete"; }
    else if (id === "back") {
      if (model.screen === "payment") { model.screen = "cart"; model.payment.method = null; model.payment.status = "not_started"; }
      else { model.screen = "menu"; model.current = null; }
    }
    else if (id === "restart") Object.assign(model, initialModel());
    else return { ok: false, error: "invalid_target" };
    model.revision += 1;
    return { ok: true, screen: model.screen, fingerprint: fingerprint(model) };
  }
  function getDirection(pointer, target) {
    const dx = target.x - pointer.x, dy = target.y - pointer.y;
    const distance = Math.hypot(dx, dy);
    if (distance < 8) return { name: "목표 도착", arrow: "◎", distance, code: "CENTER", dx, dy };
    const directions = [{ name: "오른쪽", arrow: "→", code: "E" }, { name: "오른쪽 아래", arrow: "↘", code: "SE" }, { name: "아래쪽", arrow: "↓", code: "S" }, { name: "왼쪽 아래", arrow: "↙", code: "SW" }, { name: "왼쪽", arrow: "←", code: "W" }, { name: "왼쪽 위", arrow: "↖", code: "NW" }, { name: "위쪽", arrow: "↑", code: "N" }, { name: "오른쪽 위", arrow: "↗", code: "NE" }];
    const octant = (Math.round(Math.atan2(dy, dx) / (Math.PI / 4)) + 8) % 8;
    return Object.assign({ distance, dx, dy }, directions[octant]);
  }
  function safeBounds(bounds) { const padding = Math.min(18, bounds.width * 0.18, bounds.height * 0.18); return rect(bounds.x + padding, bounds.y + padding, bounds.width - 2 * padding, bounds.height - 2 * padding); }
  function contains(bounds, pointer) { return pointer.x >= bounds.x && pointer.x <= bounds.x + bounds.width && pointer.y >= bounds.y && pointer.y <= bounds.y + bounds.height; }
  function targetCenter(button) { return { x: button.bounds.x + button.bounds.width / 2, y: button.bounds.y + button.bounds.height / 2 }; }

  class Simulation {
    constructor() { this.reset(); }
    reset() {
      this.model = initialModel(); this.state = "S0"; this.stateTime = 0; this.elapsed = 0; this.events = []; this.pointer = { x: 70, y: 620 }; this.mode = "auto"; this.speed = 180; this.confidence = 0.97; this.screenConfidence = 0.96; this.handVisible = true; this.screenVisible = true; this.autoRecovery = true; this.plan = null; this.fault = null; this.error = null; this.dwell = 0; this.dwellStartedAt = null; this.pendingResult = null; this.successes = 0; this.errors = 0; this.recoveries = 0; this.runner = null; this.completed = null; this.latestResult = null; this.notice = null; this.lastTargetStatus = null; this.paused = false; this.pausedRunnerPlaying = false; this.sequence = 0; this.emit("session_start", { mode: "simulation", camera: false, network: false, real_payment: false });
    }
    emit(type, details) { this.events.push({ sequence: ++this.sequence, simulation_ms: Math.round(this.elapsed * 1000), state: this.state, screen: this.model.screen, type, details: clone(details || {}) }); }
    setState(state, reason) {
      if (this.state !== state) { const from = this.state; this.state = state; this.stateTime = 0; this.emit("state_change", { from, to: state, meaning: STATE_LABELS[state], reason: reason || "" }); }
    }
    clearDwell(reason) { if (this.dwellStartedAt !== null) this.emit("dwell_reset", { reason, previous_ms: Math.round(this.dwell * 1000) }); this.dwell = 0; this.dwellStartedAt = null; }
    movePointer(x, y) { this.pointer = { x: clamp(x, 12, WIDTH - 12), y: clamp(y, 15, HEIGHT - 15) }; if (this.plan && !contains(this.plan.safe_bounds, this.pointer)) this.clearDwell("손끝이 안전 영역을 벗어남"); }
    setTarget(id) {
      if (this.state === "S5") return false;
      const button = getButtons(this.model).find(value => value.id === id);
      if (!button || !button.enabled) return false;
      if (this.plan) this.emit("plan_cancelled", { target: this.plan.target.id });
      const predicted = clone(this.model), outcome = applyAction(predicted, id);
      this.clearDwell("목표 버튼 재선택"); this.notice = null;
      if (outcome.ok && fingerprint(predicted) === fingerprint(this.model)) {
        this.plan = null; this.pendingResult = null; this.error = null; this.lastTargetStatus = "already_selected"; this.notice = button.label + "은(는) 이미 선택되어 있습니다."; this.setState("S3", "이미 선택된 옵션을 확인했습니다"); this.emit("selection_confirmed", { target_id: id, target_label: button.label, expected: button.expected, applied: false, counted_as_step_success: false }); return true;
      }
      this.lastTargetStatus = "planned";
      this.plan = { id: "plan-" + (this.sequence + 1), source_screen: this.model.screen, target: clone(button), safe_bounds: safeBounds(button.bounds), expected: button.expected, expected_screen: outcome.ok ? predicted.screen : this.model.screen, expected_fingerprint: outcome.ok ? fingerprint(predicted) : null, expected_order_total: orderTotal(predicted), before: clone(this.model), created_ms: Math.round(this.elapsed * 1000), source: "deterministic_simulation", confidence: this.screenConfidence };
      this.error = null; this.pendingResult = null; this.completed = null; this.setState("S1", "버튼과 현재 화면 탐색"); this.emit("plan_created", { target_id: id, target_label: button.label, expected: button.expected, expected_screen: this.plan.expected_screen, simulated_ai: true }); return true;
    }
    armFault(type) { this.fault = type; this.emit("fault_armed", { type, effect: ERROR_LABELS[type] || type }); }
    fail(type, details) {
      if (this.state === "SE") return;
      this.error = { type, message: ERROR_LABELS[type] || type, details: clone(details || {}), at_ms: Math.round(this.elapsed * 1000) }; this.clearDwell("인식 또는 결과 오류"); this.errors += 1; this.setState("SE", this.error.message); this.emit("error_detected", { error: type, message: this.error.message, ...details });
    }
    recover() {
      if (this.state !== "SE") { this.handVisible = true; this.screenVisible = true; this.confidence = 0.97; this.screenConfidence = 0.96; this.clearDwell("인식 다시 확인"); return; }
      const error = this.error, previousPlan = this.plan, resumeId = previousPlan && previousPlan.target.id;
      if (error.type === "wrong_click" && previousPlan) { const stock = this.model.soldOut.slice(); this.model = clone(previousPlan.before); this.model.soldOut = stock; }
      this.handVisible = true; this.screenVisible = true; this.confidence = 0.97; this.screenConfidence = 0.96; this.clearDwell("오류 복구"); this.pendingResult = null; this.error = null; this.plan = null; this.recoveries += 1; this.emit("recovery_complete", { error: error.type, rollback: error.type === "wrong_click", action: error.type === "sold_out" ? "대체 메뉴 재선택" : "기존 목표 재계획" });
      if (error.type === "sold_out") {
        if (this.runner && this.runner.key === "recovery") { this.runner.index += 1; this.runner.faultApplied = false; }
        this.setState("S3", "품절 메뉴 대신 다른 메뉴를 선택하세요");
      } else if (resumeId && getButtons(this.model).some(button => button.id === resumeId && button.enabled)) this.setTarget(resumeId);
      else this.setState("S3", "현재 화면에서 목표 버튼을 다시 선택하세요");
    }
    startScenario(key) {
      if (!SCENARIOS[key]) return false;
      const preferences = { mode: this.mode, speed: this.speed, autoRecovery: this.autoRecovery };
      this.reset(); Object.assign(this, preferences); this.runner = { key, index: 0, playing: true, faultApplied: false, stepStarted: 0 }; this.emit("scenario_start", { key, name: SCENARIOS[key].name, expected_total: SCENARIOS[key].total }); this.setState("S1", "시나리오 입력을 확인합니다"); return true;
    }
    pause() { this.paused = !this.paused; this.clearDwell(this.paused ? "일시 정지로 연속 유지 중단" : "계속 실행 시 안전 유지 다시 측정"); if (this.runner) { if (this.paused) { this.pausedRunnerPlaying = this.runner.playing; this.runner.playing = false; } else this.runner.playing = this.pausedRunnerPlaying; } this.emit("scenario_paused", { paused: this.paused }); return this.paused; }
    stepScenario(key) {
      if (!this.runner || this.runner.key !== key || this.completed) {
        this.startScenario(key); this.runner.playing = false; this.paused = false;
      }
      if (this.plan || this.state === "SE") return false;
      this.prepareScenarioStep(); return true;
    }
    prepareScenarioStep() {
      if (!this.runner || this.runner.index >= SCENARIOS[this.runner.key].actions.length) return;
      const scenario = SCENARIOS[this.runner.key], id = scenario.actions[this.runner.index];
      if (!this.runner.faultApplied) {
        const fault = scenario.faults && scenario.faults[this.runner.index];
        if (fault === "hand_lost") this.handVisible = false;
        else if (fault) this.armFault(fault);
        this.runner.faultApplied = true;
      }
      if (!this.setTarget(id)) this.fail("invalid_target", { target: id });
      else if (this.lastTargetStatus === "already_selected") { this.runner.index += 1; this.runner.faultApplied = false; }
      this.runner.stepStarted = this.elapsed;
    }
    healthy() {
      if (!this.handVisible) return "hand_lost";
      if (!this.screenVisible) return "screen_lost";
      if (this.confidence < 0.75) return "low_confidence";
      if (this.screenConfidence < 0.8) return "low_screen_confidence";
      return null;
    }
    press() {
      if (!this.plan || this.state !== "S4" || this.dwellStartedAt === null || this.elapsed - this.dwellStartedAt + 1e-9 < DWELL_SECONDS || this.healthy() || !contains(this.plan.safe_bounds, this.pointer)) return false;
      const intended = this.plan.target.id, planned = this.plan.expected_fingerprint, before = fingerprint(this.model);
      this.emit("press_allowed", { intended_id: intended, dwell_ms: Math.round((this.elapsed - this.dwellStartedAt) * 1000), dwell_started_ms: Math.round(this.dwellStartedAt * 1000), hand_confidence: this.confidence, screen_confidence: this.screenConfidence, pointer: clone(this.pointer) });
      let actual = intended, outcome;
      if (this.fault === "sold_out") { const id = intended.startsWith("menu:") ? intended.slice(5) : this.model.current; if (id) { if (!this.model.soldOut.includes(id)) this.model.soldOut.push(id); this.fault = null; } }
      else if (this.fault === "wrong_click") {
        const alternatives = getButtons(this.model).filter(button => button.id !== intended && button.enabled && !button.soldOut);
        const different = alternatives.find(button => { const candidate = clone(this.model); return applyAction(candidate, button.id).ok && fingerprint(candidate) !== planned; });
        actual = different ? different.id : "missing_target"; this.fault = null;
      }
      if (this.fault === "no_change") { outcome = { ok: true, suppressed: true, fingerprint: before }; this.fault = null; }
      else outcome = applyAction(this.model, actual);
      this.pendingResult = { intended_id: intended, actual_id: actual, before_fingerprint: before, expected_fingerprint: planned, actual_fingerprint: fingerprint(this.model), outcome, pressed_at_ms: Math.round(this.elapsed * 1000), total: orderTotal(this.model) };
      this.emit("click_applied", { intended_id: intended, actual_id: actual, changed: before !== fingerprint(this.model), applied: outcome.ok && !outcome.suppressed, simulated_touch: true }); this.setState("S5", "누른 뒤 화면·옵션·장바구니를 비교합니다"); this.dwell = 0; this.dwellStartedAt = null; return true;
    }
    verify() {
      const result = this.pendingResult;
      if (!result) return;
      if (!result.outcome.ok) { this.fail(result.outcome.error, { target: result.intended_id }); return; }
      if (result.actual_id !== result.intended_id) { this.fail("wrong_click", { intended: result.intended_id, actual: result.actual_id, expected_screen: this.plan.expected_screen, actual_screen: this.model.screen }); return; }
      if (result.outcome.suppressed || result.actual_fingerprint !== result.expected_fingerprint) { this.fail("no_change", { intended: result.intended_id, expected_screen: this.plan.expected_screen, actual_screen: this.model.screen }); return; }
      this.successes += 1; this.latestResult = clone(result); this.emit("step_success", { target_id: result.intended_id, screen: this.model.screen, total: result.total, verdict: "expected_semantic_result_matched", latency_ms: Math.round(this.elapsed * 1000) - this.plan.created_ms }); this.plan = null; this.pendingResult = null;
      if (this.runner) { this.runner.index += 1; this.runner.faultApplied = false; }
      if (this.model.screen === "complete") {
        this.setState("S6", "가상 주문이 완료되었습니다");
        if (this.runner) { this.completed = validateScenario(this, this.runner.key); this.runner.playing = false; this.emit("scenario_complete", this.completed); }
      } else this.setState("S3", "다음 메뉴 또는 버튼을 확인합니다");
    }
    tick(seconds) {
      if (this.paused) return;
      const dt = clamp(seconds, 0, 0.1); this.elapsed += dt; this.stateTime += dt;
      if (this.state === "SE") { if (this.runner && this.runner.playing && this.autoRecovery && this.stateTime >= 1.0) this.recover(); return; }
      if (!this.plan) {
        if (this.runner && this.runner.playing && this.runner.index < SCENARIOS[this.runner.key].actions.length) this.prepareScenarioStep();
        return;
      }
      if (this.state === "S1" && this.stateTime >= 0.18) this.setState("S2", "모의 화면 구조와 버튼 좌표를 확인했습니다");
      else if (this.state === "S2" && this.stateTime >= 0.18) this.setState("S3", "입력 주문과 목표 버튼의 예상 결과를 확인합니다");
      else if (this.state === "S3" && this.stateTime >= 0.25) this.setState("S4", "손끝 좌표와 목표 좌표를 비교해 안내합니다");
      else if (this.state === "S4") {
        const problem = this.healthy();
        if (problem) { this.fail(problem, { hand_confidence: this.confidence, screen_confidence: this.screenConfidence, dwell_reset: true }); return; }
        if (this.mode === "auto") {
          const center = targetCenter(this.plan.target), vector = getDirection(this.pointer, center), travel = Math.min(vector.distance, this.speed * dt);
          if (vector.distance > 0) this.movePointer(this.pointer.x + vector.dx / vector.distance * travel, this.pointer.y + vector.dy / vector.distance * travel);
        }
        if (contains(this.plan.safe_bounds, this.pointer)) {
          if (this.dwellStartedAt === null) { this.dwellStartedAt = this.elapsed; this.emit("dwell_start", { target: this.plan.target.id, required_ms: 300 }); }
          this.dwell = this.elapsed - this.dwellStartedAt;
          if (this.dwell + 1e-9 >= DWELL_SECONDS) this.press();
        } else this.clearDwell("손끝이 안전 영역을 벗어남");
      } else if (this.state === "S5" && this.stateTime >= 0.55) this.verify();
    }
    screenJSON() {
      return { source: "simulation_only", screen_type: this.model.screen, label: SCREEN_LABELS[this.model.screen], frame_size: { width: WIDTH, height: HEIGHT }, confidence: this.screenConfidence, visible: this.screenVisible, coordinate_system: "normalized_xywh", buttons: getButtons(this.model).map(button => ({ id: button.id, text: button.label, bbox: [button.bounds.x / WIDTH, button.bounds.y / HEIGHT, button.bounds.width / WIDTH, button.bounds.height / HEIGHT].map(value => Number(value.toFixed(4))), enabled: button.enabled, selected: button.selected, sold_out: !!button.soldOut, expected_result: button.expected })), finger: { x: Number((this.pointer.x / WIDTH).toFixed(4)), y: Number((this.pointer.y / HEIGHT).toFixed(4)), confidence: this.confidence, visible: this.handVisible } };
    }
    orderJSON() {
      return { source: "simulation_only", service_type: this.model.service, items: this.model.cart.map(item => ({ ...clone(item), unit_price: item.price + (item.size === "LARGE" ? 700 : 0), line_total: itemTotal(item) })), item_count: this.model.cart.reduce((sum, item) => sum + item.quantity, 0), total: orderTotal(this.model), currency: "KRW", payment: { ...this.model.payment, real_payment: false } };
    }
    exportJSON() { return { simulation: true, state: this.state, elapsed_ms: Math.round(this.elapsed * 1000), screen_json: this.screenJSON(), order_json: this.orderJSON(), plan: this.plan ? { ...clone(this.plan), before: undefined, expected_fingerprint: undefined } : null, event_json: clone(this.events), metrics: { verified_steps: this.successes, errors: this.errors, recoveries: this.recoveries }, scenario_result: this.completed }; }
  }
  function validateScenario(simulation, key) {
    const scenario = SCENARIOS[key], actual = simulation.orderJSON(), reasons = [];
    if (simulation.model.screen !== "complete") reasons.push("가상 완료 화면에 도달하지 못함");
    if (actual.service_type !== scenario.service) reasons.push("매장 / 포장 유형 불일치");
    if (actual.total !== scenario.total) reasons.push("총액 불일치: " + actual.total + " / " + scenario.total);
    if (actual.items.length !== scenario.expected.length) reasons.push("메뉴 종류 수 불일치");
    scenario.expected.forEach(expected => { const item = actual.items.find(value => value.id === expected.id && value.temperature === expected.temperature && value.size === expected.size); if (!item || item.quantity !== expected.quantity) reasons.push(expected.id + " 옵션 또는 수량 불일치"); });
    if (actual.payment.status !== "simulated" || actual.payment.approvals !== 1) reasons.push("가상 승인 상태 또는 중복 승인 방지 실패");
    if (scenario.recoveryCount && simulation.recoveries !== scenario.recoveryCount) reasons.push("오류 복구 수 불일치: " + simulation.recoveries);
    if (scenario.recoveryCount && ["sold_out", "hand_lost", "wrong_click", "no_change"].some(type => !simulation.events.some(event => event.type === "error_detected" && event.details.error === type))) reasons.push("필수 복구 사례가 실행되지 않음");
    const safetyViolations = []; let lastDwellStart = null;
    simulation.events.forEach(event => {
      if (event.type === "dwell_start") lastDwellStart = event;
      else if (event.type === "dwell_reset") lastDwellStart = null;
      else if (event.type === "press_allowed") {
        if (!lastDwellStart || lastDwellStart.details.target !== event.details.intended_id || event.simulation_ms - lastDwellStart.simulation_ms < 300 || event.details.dwell_ms < 300 || event.simulation_ms - event.details.dwell_started_ms < 300 || event.details.hand_confidence < 0.75 || event.details.screen_confidence < 0.8) safetyViolations.push(event);
        lastDwellStart = null;
      }
    });
    if (safetyViolations.length) reasons.push("누름 안전 조건 위반");
    return { scenario: key, name: scenario.name, passed: reasons.length === 0, reasons, expected_total: scenario.total, actual_total: actual.total, verified_steps: simulation.successes, error_count: simulation.errors, recovery_count: simulation.recoveries, elapsed_ms: Math.round(simulation.elapsed * 1000), virtual_approvals: actual.payment.approvals, safety_violations: safetyViolations.length };
  }
  function runScenario(key, preferences) {
    const simulation = new Simulation(); Object.assign(simulation, preferences || {}); simulation.mode = "auto"; simulation.autoRecovery = true; simulation.startScenario(key);
    let iterations = 0;
    while (!simulation.completed && iterations < 30000) { simulation.tick(0.05); iterations += 1; }
    const result = simulation.completed || validateScenario(simulation, key);
    if (!simulation.completed) { result.passed = false; result.reasons.push("30,000 프레임 내 실행 완료 실패"); }
    return { result, simulation, iterations };
  }
  function runRepeatedTests(count) {
    const rows = [], start = typeof performance !== "undefined" ? performance.now() : Date.now();
    for (let i = 0; i < count; i += 1) Object.keys(SCENARIOS).forEach((key, index) => { const result = runScenario(key, { speed: 150 + ((i + index) % 6) * 45 }).result; rows.push({ run: i + 1, ...result }); });
    const wallMs = (typeof performance !== "undefined" ? performance.now() : Date.now()) - start;
    return { rows, total: rows.length, passed: rows.filter(row => row.passed).length, failed: rows.filter(row => !row.passed).length, verified_steps: rows.reduce((sum, row) => sum + row.verified_steps, 0), recoveries: rows.reduce((sum, row) => sum + row.recovery_count, 0), simulated_duration_ms: rows.reduce((sum, row) => sum + row.elapsed_ms, 0), compute_ms: Math.round(wallMs), safety_tests: runSafetyTests(), source: "actual_local_simulation_engine" };
  }

  function runSafetyTests() {
    const results = [];
    const check = (name, execute) => { try { const reason = execute(); results.push({ name, passed: !reason, reason: reason || "" }); } catch (error) { results.push({ name, passed: false, reason: error.message }); } };
    const manualAtTarget = () => { const simulation = new Simulation(); simulation.mode = "manual"; simulation.setTarget("service:takeout"); simulation.state = "S4"; simulation.stateTime = 0; simulation.movePointer(390, 340); simulation.tick(.001); return simulation; };
    check("0.3초 전에는 누름 금지", () => { const simulation = manualAtTarget(); simulation.tick(.1); simulation.tick(.1); simulation.tick(.099); return simulation.model.screen !== "start" || simulation.events.some(event => event.type === "press_allowed") ? "299ms에서 버튼이 눌렸습니다" : ""; });
    check("0.3초 도달 시 한 번만 누름", () => { const simulation = manualAtTarget(); simulation.tick(.1); simulation.tick(.1); simulation.tick(.1); for (let i = 0; i < 20; i += 1) simulation.tick(.05); return simulation.model.screen !== "menu" || simulation.events.filter(event => event.type === "press_allowed").length !== 1 ? "누름 횟수 또는 화면 불일치" : ""; });
    check("안전 영역 이탈 시 유지 시간 초기화", () => { const simulation = manualAtTarget(); simulation.tick(.1); simulation.tick(.1); simulation.movePointer(20, 20); simulation.tick(.05); simulation.movePointer(390, 340); simulation.tick(.1); simulation.tick(.1); return simulation.model.screen !== "start" || Math.abs(simulation.dwell - .1) > .0001 ? "안전 영역 이탈 후 이전 시간이 누적됐습니다" : ""; });
    check("일시 정지 후 0.3초 연속 유지 다시 측정", () => { const simulation = manualAtTarget(); simulation.tick(.1); simulation.tick(.1); simulation.tick(.05); simulation.pause(); if (simulation.dwell !== 0 || simulation.dwellStartedAt !== null) return "일시 정지에 유지 시간이 남았습니다"; simulation.pause(); simulation.tick(.05); simulation.tick(.1); simulation.tick(.1); simulation.tick(.099); if (simulation.model.screen !== "start") return "재개 후 299ms 이전에 눌렸습니다"; simulation.tick(.001); return simulation.model.screen !== "menu" ? "재개 후 300ms 도달 시 누름 실패" : ""; });
    [["손 유실 중 누름 차단", "handVisible", false, "hand_lost"], ["화면 유실 중 누름 차단", "screenVisible", false, "screen_lost"], ["손끝 신뢰도 74% 누름 차단", "confidence", .74, "low_confidence"], ["화면 신뢰도 79% 누름 차단", "screenConfidence", .79, "low_screen_confidence"]].forEach(([name, property, value, errorType]) => {
      check(name, () => { const simulation = manualAtTarget(); simulation.tick(.1); simulation[property] = value; simulation.tick(.1); if (simulation.state !== "SE" || simulation.error.type !== errorType || simulation.model.screen !== "start" || simulation.dwell !== 0) return "인식 실패 중 누름이 차단되지 않았습니다"; simulation.recover(); return simulation.dwell !== 0 || simulation.state !== "S1" ? "복구 후 안전 유지 시간이 초기화되지 않았습니다" : ""; });
    });
    check("잘못 누름 복구 시 직전 주문 상태 복원", () => { const simulation = new Simulation(); simulation.speed = 540; simulation.armFault("wrong_click"); simulation.setTarget("service:takeout"); for (let i = 0; i < 150 && simulation.state !== "SE"; i += 1) simulation.tick(.05); if (simulation.error?.type !== "wrong_click" || simulation.model.service !== "dinein") return "잘못된 버튼 적용을 검출하지 못했습니다"; simulation.recover(); return simulation.model.screen !== "start" || simulation.model.service !== null || simulation.plan?.target.id !== "service:takeout" ? "잘못 누른 결과가 복구 후 남았습니다" : ""; });
    check("화면 변화 없음은 성공으로 세지 않음", () => { const simulation = new Simulation(); simulation.speed = 540; simulation.armFault("no_change"); simulation.setTarget("service:takeout"); for (let i = 0; i < 150 && simulation.state !== "SE"; i += 1) simulation.tick(.05); return simulation.error?.type !== "no_change" || simulation.successes !== 0 || simulation.model.screen !== "start" ? "no_change가 성공으로 처리됐습니다" : ""; });
    check("같은 가상 주문의 중복 승인 차단", () => { const simulation = runScenario("coffee").simulation; const result = applyAction(simulation.model, "payment:simulate"); return result.ok || simulation.model.payment.approvals !== 1 ? "완료된 주문이 다시 승인됐습니다" : ""; });
    check("이미 선택된 옵션 확인은 누름 성공에 미포함", () => { const simulation = new Simulation(); applyAction(simulation.model, "service:takeout"); simulation.setTarget("category:coffee"); for (let i = 0; i < 30; i += 1) simulation.tick(.05); return simulation.plan || simulation.successes !== 0 || simulation.events.some(event => event.type === "press_allowed") || !simulation.events.some(event => event.type === "selection_confirmed") ? "이미 선택된 버튼이 누름 성공으로 집계됐습니다" : ""; });
    check("누름 이벤트의 진입 시각부터 실제 300ms 경과", () => { const simulation = runScenario("coffee").simulation; let start = null; for (const event of simulation.events) { if (event.type === "dwell_start") start = event; if (event.type === "dwell_reset") start = null; if (event.type === "press_allowed") { if (!start || event.simulation_ms - start.simulation_ms < 300) return "누름 이벤트가 영역 진입 후 300ms 이전에 발생했습니다"; start = null; } } return ""; });
    check("8방향 안내 좌표 일치", () => { const origin = { x: 100, y: 100 }; const points = [[150, 100, "E"], [150, 150, "SE"], [100, 150, "S"], [50, 150, "SW"], [50, 100, "W"], [50, 50, "NW"], [100, 50, "N"], [150, 50, "NE"]]; return points.some(([x, y, code]) => getDirection(origin, { x, y }).code !== code) ? "좌표와 안내 방향 불일치" : ""; });
    check("버튼과 안전 영역이 화면 내부에 위치", () => { const models = [initialModel(), runScenario("coffee").simulation.model]; const model = initialModel(); for (const id of SCENARIOS.latte.actions) { applyAction(model, id); models.push(clone(model)); } const invalid = models.flatMap(getButtons).some(button => { const b = button.bounds, s = safeBounds(b); return b.x < 0 || b.y < 0 || b.x + b.width > WIDTH || b.y + b.height > HEIGHT || s.width <= 0 || s.height <= 0; }); return invalid ? "화면 밖 버튼 또는 비어 있는 안전 영역" : ""; });
    return { results, total: results.length, passed: results.filter(row => row.passed).length, failed: results.filter(row => !row.passed).length };
  }

  const api = { Simulation, SCENARIOS, MENU, STATE_LABELS, SCREEN_LABELS, getButtons, getDirection, safeBounds, contains, targetCenter, applyAction, orderTotal, validateScenario, runScenario, runRepeatedTests, runSafetyTests, DWELL_SECONDS, WIDTH, HEIGHT };
  if (typeof module !== "undefined" && module.exports) module.exports = api;
  global.SonkkeutSimulation = api;
  if (typeof document === "undefined") return;

  const $ = id => document.getElementById(id);
  const sim = new Simulation();
  let lastFrame = performance.now(), renderKey = "", eventCount = -1, dataTime = 0, voiceOn = false, previousSpeech = "", dragging = false, tests = null;
  const html = value => String(value).replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[char]);
  const time = seconds => { const minutes = Math.floor(seconds / 60); return String(minutes).padStart(2, "0") + ":" + (seconds % 60).toFixed(1).padStart(4, "0"); };
  function screenContent() {
    const model = sim.model, title = model.screen === "option" ? MENU.find(item => item.id === model.current).name : SCREEN_LABELS[model.screen];
    let content = '<h3>' + html(title) + '</h3>';
    if (model.screen === "start") content += '<p class="kiosk-subtitle">어디서 드시나요?<br>버튼을 선택하면 가상 손끝이 해당 버튼으로 이동합니다.</p>';
    if (model.screen === "menu") content += '<p class="kiosk-subtitle">메뉴를 선택해 옵션을 정해주세요.</p>';
    if (model.screen === "option") {
      const item = MENU.find(value => value.id === model.current), drink = item.category === "coffee", price = item.price + (drink && model.options.size === "LARGE" ? 700 : 0);
      if (drink) content += '<div class="kiosk-info" style="left:7.4%;top:23%">온도</div><div class="kiosk-info" style="left:7.4%;top:41%">크기</div>';
      else content += '<p class="kiosk-subtitle">디저트는 온도 / 크기 옵션이 없습니다.</p><div class="kiosk-info" style="left:0;right:0;top:31%;text-align:center;font-size:72px">' + item.icon + '</div>';
      content += '<div class="kiosk-info" style="left:7.4%;top:59%">수량</div><div class="kiosk-info" style="left:0;right:0;top:65%;text-align:center"><strong>' + model.options.quantity + '개</strong></div><div class="kiosk-info" style="left:7.4%;top:76%">합계 <strong>' + money(price * model.options.quantity) + '</strong></div>';
    }
    if (model.screen === "cart") {
      content += '<p class="kiosk-subtitle">메뉴·옵션·수량을 확인해주세요.</p><div class="kiosk-cart">';
      model.cart.forEach((item, index) => { content += '<div style="height:80px;align-items:flex-start"><span>' + html(item.name) + '<small>' + html([item.temperature, item.size, item.quantity + "개"].filter(Boolean).join(" / ")) + '<br>' + money(itemTotal(item)) + '</small></span></div>'; });
      if (!model.cart.length) content += '<div>담긴 메뉴가 없습니다.</div>';
      content += '</div><div class="kiosk-info" style="left:7.4%;right:7.4%;top:76%;text-align:right">총 <strong>' + money(orderTotal(model)) + '</strong></div>';
    }
    if (model.screen === "payment") {
      content += '<p class="kiosk-subtitle">결제 금액과 결제 수단을 확인합니다.</p><div class="kiosk-info" style="left:0;right:0;top:30%;text-align:center"><strong style="font-size:2em">' + money(orderTotal(model)) + '</strong></div>';
      if (model.payment.status === "awaiting_simulation") content += '<div class="kiosk-info" style="left:7.4%;right:7.4%;top:44%;text-align:center">▣ 카드 삽입 안내<br><small>여기서는 가상 승인만 확인합니다.</small></div>';
      content += '<div class="kiosk-info" style="left:7.4%;right:7.4%;top:73%;text-align:center;font-size:12px">실제 카드 번호 입력 · 결제 · 청구 없음</div>';
    }
    if (model.screen === "complete") content += '<div class="kiosk-info" style="left:0;right:0;top:30%;text-align:center"><span style="font-size:55px;color:#286f52">✓</span><br><strong>가상 주문 완료</strong><br><br>' + money(orderTotal(model)) + '<br><small>승인 횟수 ' + model.payment.approvals + '회 · 실제 결제 없음</small></div>';
    getButtons(model).forEach(button => {
      const b = button.bounds, css = 'left:' + (b.x / WIDTH * 100) + '%;top:' + (b.y / HEIGHT * 100) + '%;width:' + (b.width / WIDTH * 100) + '%;height:' + (b.height / HEIGHT * 100) + '%';
      content += '<button type="button" data-target="' + html(button.id) + '" class="' + html(button.kind + (button.selected ? " selected" : "") + (button.soldOut ? " sold-out" : "") + (sim.plan && sim.plan.target.id === button.id ? " target" : "")) + '" style="' + css + '"' + (!button.enabled ? ' disabled' : '') + ' aria-label="' + html(button.label + (button.soldOut ? " 품절" : "") + '. ' + button.expected + (sim.mode === "manual" ? ". 누르면 목표를 설정합니다." : ". 누르면 손끝 안내를 시작합니다.")) + '">' + (button.item ? '<span aria-hidden="true">' + button.item.icon + '</span>' : '') + html(button.label) + (button.subtitle ? '<small>' + html(button.subtitle) + '</small>' : '') + (button.soldOut ? '<small>품절</small>' : '') + '</button>';
    });
    $('kiosk-content').innerHTML = content;
    $('service-label').textContent = model.service === "takeout" ? "포장 주문" : model.service === "dinein" ? "매장 주문" : "함께, 편안한 주문";
    $('screen-name').textContent = SCREEN_LABELS[model.screen];
    const list = model.cart.map(item => '<div class="order-row"><span>' + html(item.name) + '<small>' + html([item.temperature, item.size, item.quantity + "개"].filter(Boolean).join(" / ")) + '</small></span><strong>' + money(itemTotal(item)) + '</strong></div>').join("");
    $('order-list').innerHTML = list || "담긴 메뉴가 없습니다."; $('order-total').textContent = money(orderTotal(model)); $('cart-count').textContent = model.cart.reduce((sum, item) => sum + item.quantity, 0) + "개";
  }
  function renderPlan() {
    if (!sim.plan) { $('plan-view').innerHTML = sim.completed ? '<strong class="pass">주문 및 안전 조건 검증 완료</strong><p class="small">' + html(sim.completed.name) + '<br>예상 ' + money(sim.completed.expected_total) + ' / 실제 ' + money(sim.completed.actual_total) + '</p>' : sim.notice ? '<strong class="pass">' + html(sim.notice) + '</strong><p class="small">다시 누르지 않고 선택 상태를 확인했습니다. 다음 버튼을 선택해주세요.</p>' : '목표 버튼을 선택하면 계획과 검증 조건이 표시됩니다.'; return; }
    const plan = sim.plan, b = plan.target.bounds;
    $('plan-view').innerHTML = '<dl><dt>누를 버튼</dt><dd class="target-name">' + html(plan.target.label) + '</dd><dt>현재 화면 → 기대 화면</dt><dd>' + html(SCREEN_LABELS[plan.source_screen]) + ' → ' + html(SCREEN_LABELS[plan.expected_screen]) + '</dd><dt>누른 뒤 확인할 결과</dt><dd>' + html(plan.expected) + '</dd><dt>버튼 좌표 · 가상 픽셀</dt><dd><code>x ' + b.x + ' / y ' + b.y + ' / w ' + b.width + ' / h ' + b.height + '</code></dd><dt>안전 누름 조건</dt><dd>안쪽 여백을 뺀 영역에서 0.3초 유지<br>손끝 ≥ 75% / 화면 ≥ 80%</dd><dt>예상 장바구니 총액</dt><dd>' + money(plan.expected_order_total) + '</dd></dl>' + (sim.error ? '<div class="error-banner"><strong>' + html(sim.error.message) + '</strong><br>' + (sim.error.type === "sold_out" ? '대체 메뉴를 선택한 뒤 주문을 계속합니다.' : sim.error.type === "wrong_click" ? '잘못 반영된 상태를 되돌리고 같은 목표를 다시 안내합니다.' : '인식을 복구한 뒤 누름 대기 시간을 0초부터 다시 측정합니다.') + '</div>' : '');
  }
  function renderTimeline() {
    if (eventCount === sim.events.length) return; eventCount = sim.events.length;
    $('event-count').textContent = sim.events.length + "건";
    $('timeline').innerHTML = sim.events.slice(-45).reverse().map(event => {
      const d = event.details, detail = d.target_label || d.message || d.expected || d.target_id || d.actual_id || (d.from ? d.from + " → " + d.to + " " + (d.reason || "") : d.name || d.effect || d.action || "");
      return '<li class="' + (event.type === "error_detected" ? "error" : ["step_success", "recovery_complete", "scenario_complete"].includes(event.type) ? "success" : "") + '"><time>' + time(event.simulation_ms / 1000) + '</time><strong>' + html(EVENT_LABELS[event.type] || event.type) + '</strong>' + (detail ? '<p>' + html(detail) + '</p>' : '') + '</li>';
    }).join("");
    renderPlan();
  }
  function renderScenario() {
    const key = $('scenario').value, scenario = SCENARIOS[key], runner = sim.runner && sim.runner.key === key ? sim.runner : null, i = runner ? runner.index : -1;
    $('scenario-description').textContent = scenario.description;
    let progress;
    if (key === "recovery") progress = [i > 2, i > 9, i > 8, !!sim.completed];
    else progress = [i > 0, i > (key === "coffee" ? 3 : 4), i > (key === "coffee" ? 4 : 5), !!sim.completed];
    const current = progress.indexOf(false);
    $('scenario-checklist').innerHTML = scenario.milestones.map((title, index) => '<div class="' + (progress[index] ? "done" : index === current && runner ? "current" : "") + '"><i>' + (progress[index] ? "✓" : index + 1) + '</i><span>' + html(title) + '</span></div>').join("");
    $('play').textContent = sim.runner && sim.paused ? "▶ 이어서 실행" : "▶ 시나리오 실행";
    $('pause').textContent = sim.paused ? "계속" : "일시 정지";
    $('pause').disabled = !sim.plan && !sim.runner;
    if (sim.completed) $('scenario-result').innerHTML = '<span class="' + (sim.completed.passed ? "pass" : "fail") + '">' + (sim.completed.passed ? "✓ 주문·금액·안전 조건 검증 통과" : "검증 실패: " + html(sim.completed.reasons.join(", "))) + '</span>';
    else if (sim.error) $('scenario-result').textContent = sim.error.message + (sim.runner && sim.runner.playing && sim.autoRecovery && !sim.paused ? " · 1초 후 자동 복구" : " · 복구 버튼을 눌러주세요");
    else if (runner) $('scenario-result').textContent = "단계 " + Math.min(i + 1, scenario.actions.length) + " / " + scenario.actions.length + " · " + (sim.paused ? "일시 정지" : runner.playing ? "자동 실행 중" : "한 단계 실행");
    else $('scenario-result').textContent = "키오스크 버튼을 선택하면 실제 시뮬레이션 상태가 바뀝니다.";
  }
  function renderTelemetry() {
    const pointer = sim.pointer, plan = sim.plan;
    ['finger-dot', 'finger-halo'].forEach(id => { $(id).setAttribute('cx', pointer.x); $(id).setAttribute('cy', pointer.y); $(id).style.opacity = sim.handVisible ? 1 : .18; });
    $('finger-label').setAttribute('x', pointer.x > 440 ? pointer.x - 66 : pointer.x + 18); $('finger-label').setAttribute('y', pointer.y + 5); $('finger-label').textContent = sim.handVisible ? '손끝' : '손 유실';
    let text = sim.state === "S6" ? "가상 주문 안내가 완료되었습니다." : "시나리오 또는 버튼을 선택하세요.", detail = "버튼 선택 → 손끝 이동 → 안전 유지 → 결과 확인", arrow = "◎";
    if (plan) {
      const center = targetCenter(plan.target), vector = getDirection(pointer, center), safe = plan.safe_bounds;
      $('path-line').setAttribute('x1', pointer.x); $('path-line').setAttribute('y1', pointer.y); $('path-line').setAttribute('x2', center.x); $('path-line').setAttribute('y2', center.y);
      ['target-box', 'safe-box'].forEach((id, index) => { const bounds = index ? safe : plan.target.bounds; Object.keys(bounds).forEach(key => $(id).setAttribute(key, bounds[key])); });
      $('path-line').style.display = ''; $('target-box').style.display = ''; $('safe-box').style.display = '';
      $('distance').textContent = Math.round(vector.distance) + " px"; $('direction').textContent = vector.name; arrow = vector.arrow;
      if (sim.state === "S4") {
        const near = contains(safe, pointer); text = near ? "멈추세요. 안전하게 누를 위치입니다." : vector.name + "으로 손끝을 이동하세요.";
        detail = near ? "안전 영역 유지 중 · " + sim.dwell.toFixed(2) + "초 / 0.30초" : plan.target.label + "까지 " + Math.round(vector.distance) + "px · " + (vector.distance > 200 ? "멀리" : vector.distance > 80 ? "중간 거리" : "가까이");
      } else { text = STATE_LABELS[sim.state] + " 중"; detail = sim.state === "S5" ? "0.55초 뒤 화면·옵션·장바구니 결과를 비교합니다." : "모의 화면 구조와 주문 계획을 확인합니다."; }
    } else { $('path-line').style.display = 'none'; $('target-box').style.display = 'none'; $('safe-box').style.display = 'none'; $('distance').textContent = "—"; $('direction').textContent = "대기"; }
    if (sim.notice && !plan) { text = sim.notice; detail = "선택 상태 확인 · 누름 횟수에 포함하지 않습니다."; }
    if (sim.error) { text = sim.error.message; detail = "누름 중지 · 안전 유지 시간을 초기화했습니다."; arrow = "!"; }
    if (sim.paused) { text = "일시 정지되었습니다."; detail = "계속 버튼을 누르면 같은 단계에서 이어집니다."; }
    $('guidance-text').textContent = text; $('guide-detail').textContent = detail; $('direction-icon').textContent = arrow;
    const shownDwell = sim.state === "S5" ? 0.3 : Math.min(sim.dwell, 0.3);
    $('dwell').textContent = shownDwell.toFixed(2) + " / 0.30초"; $('dwell-bar').style.width = Math.min(100, shownDwell / 0.3 * 100) + "%";
    $('press-state').textContent = sim.error ? "누름 차단" : sim.state === "S5" ? "누름 후 검증" : sim.dwell > 0 ? "안전 유지 중" : plan ? "이동 / 확인 중" : "대기";
    $('elapsed').textContent = time(sim.elapsed); $('success-count').textContent = sim.successes; $('error-count').textContent = sim.errors; $('recovery-count').textContent = sim.recoveries;
    if (voiceOn && text !== previousSpeech && (sim.state === "S4" || sim.state === "SE" || sim.state === "S6")) { previousSpeech = text; speak(text); }
  }
  function render() {
    const key = fingerprint(sim.model) + ":" + sim.model.soldOut.join(",") + ":" + (sim.plan ? sim.plan.target.id : "");
    if (key !== renderKey) { renderKey = key; screenContent(); }
    $('states').innerHTML = Object.entries(STATE_LABELS).map(([state, label]) => '<span class="state-chip' + (state === sim.state ? " active" : "") + (state === "SE" ? " error" : "") + '"><b>' + state + '</b> ' + label + '</span>').join("");
    renderTelemetry(); renderTimeline(); renderScenario();
    if (sim.elapsed - dataTime > .25 || dataTime === 0) {
      dataTime = sim.elapsed; $('screen-json').textContent = JSON.stringify(sim.screenJSON(), null, 2); $('order-json').textContent = JSON.stringify(sim.orderJSON(), null, 2); $('event-json').textContent = JSON.stringify(sim.events.slice(-12), null, 2);
    }
    document.querySelectorAll('[data-fault]').forEach(button => button.classList.toggle('armed', button.dataset.fault === sim.fault));
    $('fault-message').textContent = sim.fault ? (sim.fault === "sold_out" ? "다음 메뉴 선택 / 담기에 예약됨: " : "다음 누름에 예약됨: ") + (ERROR_LABELS[sim.fault] || sim.fault) : "오류 발생 후 복구 버튼으로 다시 안내합니다. 잘못 누름은 직전 상태로 되돌린 뒤 재계획합니다.";
  }
  function syncControls() {
    $('pointer-mode').value = sim.mode; $('pointer-mode-label').textContent = sim.mode === "auto" ? "자동 이동" : "수동 이동";
    $('speed').value = sim.speed; $('speed-value').textContent = sim.speed + " px/s";
    $('confidence').value = Math.round(sim.confidence * 100); $('confidence-value').textContent = Math.round(sim.confidence * 100) + "%";
    $('screen-confidence').value = Math.round(sim.screenConfidence * 100); $('screen-confidence-value').textContent = Math.round(sim.screenConfidence * 100) + "%";
    $('hand-lost').checked = !sim.handVisible; $('screen-lost').checked = !sim.screenVisible;
    $('auto-recovery').checked = sim.autoRecovery;
  }
  function speak(text) {
    if (!('speechSynthesis' in global)) return;
    global.speechSynthesis.cancel(); const utterance = new SpeechSynthesisUtterance(text); utterance.lang = "ko-KR"; utterance.rate = .95; global.speechSynthesis.speak(utterance);
  }
  $('play').addEventListener('click', () => {
    if (sim.runner && sim.paused && sim.runner.key === $('scenario').value) sim.pause();
    else sim.startScenario($('scenario').value);
    dataTime = 0; eventCount = -1; syncControls(); render();
  });
  $('pause').addEventListener('click', () => { sim.pause(); render(); });
  $('next').addEventListener('click', () => { sim.stepScenario($('scenario').value); dataTime = 0; syncControls(); render(); });
  $('reset').addEventListener('click', () => { const speed = sim.speed, mode = sim.mode, autoRecovery = sim.autoRecovery; sim.reset(); Object.assign(sim, { speed, mode, autoRecovery }); renderKey = ""; dataTime = 0; eventCount = -1; syncControls(); render(); });
  $('scenario').addEventListener('change', () => { renderScenario(); });
  $('pointer-mode').addEventListener('change', event => { sim.mode = event.target.value; syncControls(); render(); });
  $('speed').addEventListener('input', event => { sim.speed = Number(event.target.value); syncControls(); });
  $('confidence').addEventListener('input', event => { sim.confidence = Number(event.target.value) / 100; syncControls(); });
  $('screen-confidence').addEventListener('input', event => { sim.screenConfidence = Number(event.target.value) / 100; syncControls(); });
  $('hand-lost').addEventListener('change', event => { sim.handVisible = !event.target.checked; });
  $('screen-lost').addEventListener('change', event => { sim.screenVisible = !event.target.checked; });
  $('auto-recovery').addEventListener('change', event => { sim.autoRecovery = event.target.checked; });
  $('recover').addEventListener('click', () => { sim.recover(); syncControls(); render(); });
  document.querySelectorAll('[data-fault]').forEach(button => button.addEventListener('click', () => { if (sim.fault === button.dataset.fault) sim.fault = null; else sim.armFault(button.dataset.fault); render(); }));
  $('kiosk-content').addEventListener('click', event => {
    const button = event.target.closest('[data-target]'); if (!button || sim.mode === "manual" && dragging) return;
    if (sim.state === "S5") return;
    if (sim.state === "SE") { sim.recover(); syncControls(); }
    if (sim.runner) { sim.runner = null; sim.paused = false; }
    sim.setTarget(button.dataset.target); dataTime = 0; render();
  });
  const moveFromEvent = event => { const bounds = $('kiosk').getBoundingClientRect(), inset = parseFloat(getComputedStyle($('kiosk')).borderLeftWidth) || 0; sim.movePointer((event.clientX - bounds.left - inset) / (bounds.width - inset * 2) * WIDTH, (event.clientY - bounds.top - inset) / (bounds.height - inset * 2) * HEIGHT); renderTelemetry(); };
  $('kiosk').addEventListener('pointerdown', event => { if (sim.mode !== "manual") return; if (event.target.closest('[data-target]') && !sim.plan) return; dragging = false; moveFromEvent(event); });
  $('kiosk').addEventListener('pointermove', event => { if (sim.mode === "manual" && event.buttons) { dragging = true; moveFromEvent(event); } });
  $('kiosk').addEventListener('pointerup', () => { setTimeout(() => { dragging = false; }, 0); });
  $('kiosk').addEventListener('keydown', event => { if (sim.mode !== "manual") return; const vectors = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] }; const vector = vectors[event.key]; if (vector) { event.preventDefault(); sim.movePointer(sim.pointer.x + vector[0] * (event.shiftKey ? 20 : 5), sim.pointer.y + vector[1] * (event.shiftKey ? 20 : 5)); renderTelemetry(); } });
  $('voice').addEventListener('click', () => { voiceOn = !voiceOn; $('voice').textContent = voiceOn ? "음성 켜짐" : "음성 꺼짐"; $('voice').setAttribute('aria-pressed', String(voiceOn)); previousSpeech = ""; if (!voiceOn && 'speechSynthesis' in global) global.speechSynthesis.cancel(); renderTelemetry(); });
  $('run-tests').addEventListener('click', () => {
    const button = $('run-tests'); button.disabled = true; $('test-results').textContent = "주문 엔진으로 좌표 이동, 누름 조건, 주문·결제 상태를 실행 검증하고 있습니다…";
    setTimeout(() => {
      tests = runRepeatedTests(Number($('repeat-count').value));
      const names = Object.keys(SCENARIOS), grouped = names.map(key => { const rows = tests.rows.filter(row => row.scenario === key), passed = rows.filter(row => row.passed).length; return '<tr><td>' + html(SCENARIOS[key].name) + '</td><td class="' + (passed === rows.length ? "pass" : "fail") + '">' + passed + " / " + rows.length + '</td><td>' + rows.reduce((sum, row) => sum + row.verified_steps, 0) + '</td><td>' + rows.reduce((sum, row) => sum + row.recovery_count, 0) + '</td><td>' + money(rows[0].actual_total) + '</td></tr>'; }).join("");
      $('test-results').innerHTML = '<div class="test-summary"><span><strong>' + tests.passed + ' / ' + tests.total + '</strong>실행 통과</span><span><strong>' + tests.verified_steps + '</strong>버튼 결과 검증</span><span><strong>' + tests.recoveries + '</strong>오류 복구</span><span>브라우저 연산 ' + tests.compute_ms + 'ms · 가상 경과 ' + time(tests.simulated_duration_ms / 1000) + '</span></div><table><thead><tr><th>시나리오</th><th>통과 / 실행</th><th>검증된 단계</th><th>복구</th><th>주문 총액</th></tr></thead><tbody>' + grouped + '</tbody></table><p style="margin-top:14px" class="' + (tests.safety_tests.failed ? 'fail' : 'pass') + '">누름 차단·복구·중복 승인·8방향 안전 검사 ' + tests.safety_tests.passed + ' / ' + tests.safety_tests.total + ' 통과</p><details><summary>각 실행 결과 / 실패 원인 보기</summary><pre>' + html(JSON.stringify(tests.rows, null, 2)) + '</pre></details><details><summary>안전 검사 결과 보기</summary><pre>' + html(JSON.stringify(tests.safety_tests.results, null, 2)) + '</pre></details>';
      button.disabled = false;
    }, 25);
  });
  $('download').addEventListener('click', () => { const blob = new Blob([JSON.stringify({ ...sim.exportJSON(), repeated_tests: tests }, null, 2)], { type: "application/json;charset=utf-8" }); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = "sonkkeut-simulation.json"; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000); });
  function frame(now) {
    const dt = Math.min((now - lastFrame) / 1000, .1); lastFrame = now;
    const beforeError = sim.error, hand = sim.handVisible, screen = sim.screenVisible; sim.tick(dt);
    if (sim.error !== beforeError || hand !== sim.handVisible || screen !== sim.screenVisible) syncControls();
    render(); global.requestAnimationFrame(frame);
  }
  // Exposed for local browser verification; contains only synthetic session data.
  global.sonkkeutLab = { simulation: sim, runRepeatedTests, exportJSON: () => sim.exportJSON() };
  syncControls(); render(); global.requestAnimationFrame(frame);
})(typeof window !== "undefined" ? window : globalThis);
