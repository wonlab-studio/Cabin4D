/*
 * Cabin 4D 트랙 생성기
 *
 * 입력
 *   S    : analyzer.js가 만든 0.5초 간격 샘플 [t, 밝기L, r, g, b, 채도가중치, 프레임차이, 하이라이트비율]
 *   spec : 장면 정의(scenes/*.json). 바람·송풍 방향은 사람이 고른 장면에만 넣고,
 *          무드등 색·밝기와 섬광은 영상 분석으로 자동 생성한다.
 * 출력
 *   앱이 읽는 트랙 JSON 문자열(app/src/main/assets/tracks/*.json 형식)
 *
 * 브라우저(분석 페이지)와 Node 양쪽에서 쓸 수 있도록 의존성 없이 작성했다.
 */
(function (root) {
  'use strict';

  const clamp = (x, a, b) => Math.max(a, Math.min(b, x));
  const round = (x, d) => Math.round(x * 10 ** d) / 10 ** d;

  function rgbToHsv(r, g, b) {
    r /= 255; g /= 255; b /= 255;
    const mx = Math.max(r, g, b), mn = Math.min(r, g, b), d = mx - mn;
    let h = 0;
    if (d > 0) {
      if (mx === r) h = ((g - b) / d) % 6;
      else if (mx === g) h = (b - r) / d + 2;
      else h = (r - g) / d + 4;
      h *= 60; if (h < 0) h += 360;
    }
    return [h, mx === 0 ? 0 : d / mx, mx];
  }

  function hsvToHex(h, s, v) {
    const c = v * s, x = c * (1 - Math.abs(((h / 60) % 2) - 1)), m = v - c;
    let r = 0, g = 0, b = 0;
    if (h < 60) [r, g, b] = [c, x, 0];
    else if (h < 120) [r, g, b] = [x, c, 0];
    else if (h < 180) [r, g, b] = [0, c, x];
    else if (h < 240) [r, g, b] = [0, x, c];
    else if (h < 300) [r, g, b] = [x, 0, c];
    else [r, g, b] = [c, 0, x];
    return [r, g, b].map((u) => Math.round((u + m) * 255).toString(16).padStart(2, '0')).join('').toUpperCase();
  }

  const hueDist = (a, b) => { const d = Math.abs(a - b) % 360; return d > 180 ? 360 - d : d; };

  function fmtTime(t) {
    const m = Math.floor(t / 60), s = t - m * 60;
    return String(m).padStart(2, '0') + ':' + s.toFixed(1).padStart(4, '0');
  }

  function quantile(arr, q) {
    if (!arr.length) return 0;
    const a = arr.slice().sort((x, y) => x - y);
    return a[Math.min(a.length - 1, Math.floor(q * a.length))];
  }

  /** 채도 가중치가 이보다 낮으면 무채색 장면(눈밭·흑백 등)으로 본다 */
  const NEUTRAL_SAT = 0.02;

  /** 화면 색 → 무드등 목표. 채도는 실내조명답게 끌어올리고, 무채색 장면은 따뜻한/차가운 흰색으로. */
  function lightTarget(w) {
    if (w.L < 10) return { hex: '2A1E14', br: 0.05, hue: 30, neutral: true, black: true };
    const br = round(clamp(0.1 + (w.L / 255) * 0.85, 0.1, 0.9), 2);
    if (w.sat < NEUTRAL_SAT) {
      const cool = w.b > w.r + 6;
      return { hex: cool ? 'CFE0FF' : 'FFE2C0', br, hue: cool ? 215 : 35, neutral: true };
    }
    // 색조는 화면에서, 채도는 화면 채도에 비례(흐린 장면은 파스텔, 강렬한 장면은 원색)
    const [h] = rgbToHsv(w.r, w.g, w.b);
    const sat = round(clamp(w.sat * 8, 0.2, 0.95), 2);
    return { hex: hsvToHex(h, sat, 1), br, hue: h, s: sat, neutral: false };
  }

  function sameLight(a, b) {
    if (a.neutral !== b.neutral) return false;
    if (!a.neutral) {
      // 파스텔(흐린 색)끼리는 색조 차이가 눈에 덜 띄므로 넓게 같은 조명으로 본다
      const tol = a.s < 0.45 && b.s < 0.45 ? 60 : 22;
      if (hueDist(a.hue, b.hue) >= tol || Math.abs(a.s - b.s) >= 0.3) return false;
    }
    if (a.neutral && a.hex !== b.hex) return false;
    return Math.abs(a.br - b.br) < 0.18;
  }

  /** 장면 안에서 움직임(프레임 차이)이 튀는 순간 = 돌풍 후보 */
  function motionPeaks(S, from, to, minGap) {
    const idx = S.map((s, i) => i).filter((i) => S[i][0] >= from && S[i][0] < to);
    const diffs = idx.map((i) => S[i][6]);
    const thr = Math.max(quantile(diffs, 0.85), 20);
    const cands = idx.filter((i) => {
      const d = S[i][6];
      if (d < thr) return false;
      for (let k = i - 2; k <= i + 2; k++) if (k !== i && S[k] && S[k][6] > d) return false;
      return true;
    }).sort((a, b) => S[b][6] - S[a][6]);
    const picked = [];
    const cap = Math.ceil((to - from) / 4);
    for (const i of cands) {
      if (picked.length >= cap) break;
      if (picked.every((t) => Math.abs(t - S[i][0]) >= minGap)) picked.push(S[i][0]);
    }
    return picked.sort((a, b) => a - b);
  }

  function generateTrack(S, spec) {
    const step = S[1][0] - S[0][0];
    const credits = spec.credits || [];
    const inCredits = (t) => credits.some(([a, b]) => t >= a && t < b);
    const scenes = (spec.scenes || []).slice().sort((a, b) => a.from - b.from);
    const sceneAt = (t) => scenes.find((sc) => t >= sc.from && t < sc.to);
    const cues = [];

    // ---------------------------------------------------------- 1) 무드등: 2초 창 요약 → 장면 단위로 묶기
    const per = Math.max(1, Math.round((spec.window || 2) / step));
    const wins = [];
    for (let a = 0; a < S.length; a += per) {
      const w = S.slice(a, a + per);
      let L = 0, r = 0, g = 0, b = 0, sw = 0;
      for (const s of w) { L += s[1]; r += s[2] * s[5]; g += s[3] * s[5]; b += s[4] * s[5]; sw += s[5]; }
      wins.push({ t: w[0][0], L: L / w.length, r: sw ? r / sw : 0, g: sw ? g / sw : 0, b: sw ? b / sw : 0, sat: sw / w.length });
    }

    const ambient = []; // {t, hex, br, note}
    let cur = null, pending = null, pendCount = 0;
    for (const w of wins) {
      if (inCredits(w.t)) { cur = null; pending = null; continue; }
      const sc = sceneAt(w.t);
      if (sc && sc.ambient) { cur = null; pending = null; continue; } // 장면이 조명을 직접 지정. 끝나면 자동 조명이 바로 이어진다
      const tg = lightTarget(w);
      if (!cur) { cur = tg; ambient.push({ t: w.t, ...tg }); continue; }
      if (sameLight(cur, tg)) { pending = null; pendCount = 0; continue; }
      // 어두운 장면은 색 추정이 흔들리므로 즉시 바꾸지 않고 6초 이상 이어질 때만 바꾼다
      const dark = tg.br < 0.25 && cur.br < 0.25;
      const big = !dark && !tg.black && !cur.black && (
        Math.abs(tg.br - cur.br) > 0.3 ||
        tg.neutral !== cur.neutral ||
        (!tg.neutral && !cur.neutral && tg.s >= 0.45 && cur.s >= 0.45 && hueDist(tg.hue, cur.hue) > 60));
      if (pending && sameLight(pending.tg, tg)) pendCount++; else { pending = { t: w.t, tg }; pendCount = 1; }
      if (big || pendCount >= (dark ? 3 : 2)) {
        cur = pending.tg;
        ambient.push({ t: pending.t, ...cur });
        pending = null; pendCount = 0;
      }
    }
    // 장면이 지정한 조명
    for (const sc of scenes) {
      if (!sc.ambient) continue;
      ambient.push({ t: sc.from, hex: sc.ambient.replace('#', ''), br: sc.brightness ?? 0.6, note: sc.label });
    }
    // 엔딩 크레딧: 은은한 주황빛으로 낮추고 바람은 끈다
    for (const [a] of credits) ambient.push({ t: a, hex: 'FFB070', br: 0.12, note: '엔딩 크레딧' });
    ambient.sort((a, b) => a.t - b.t);

    const ambientAt = (t) => { let v = null; for (const a of ambient) { if (a.t <= t) v = a; else break; } return v; };

    // ---------------------------------------------------------- 2) 섬광: 밝기가 급격히 튀는 순간(폭발·불길·번개)
    const flashTimes = [];
    if (spec.autoFlash !== false) {
      for (let i = 1; i < S.length; i++) {
        const t = S[i][0];
        if (S[i][1] - S[i - 1][1] > 30 && S[i][7] > 0.12 && !inCredits(t)) flashTimes.push(t);
      }
    }
    (spec.flashes || []).forEach((t) => flashTimes.push(t));
    (spec.noFlash || []).forEach(([a, b]) => { for (let k = flashTimes.length - 1; k >= 0; k--) if (flashTimes[k] >= a && flashTimes[k] < b) flashTimes.splice(k, 1); });
    flashTimes.sort((a, b) => a - b);
    const flashes = [];
    for (const t of flashTimes) if (!flashes.length || t - flashes[flashes.length - 1] >= 1.5) flashes.push(t);

    const flashCues = [];
    for (const t of flashes) {
      const sc = sceneAt(t);
      const before = ambientAt(t);
      flashCues.push({ t, hex: (sc && sc.flashColor ? sc.flashColor : 'FFE7B0').replace('#', ''), br: 1, note: (sc && sc.flashNote) || '섬광' });
      // 섬광 뒤 원래 장면 조명으로 복귀(그 사이 조명이 바뀌면 그 값을 따른다)
      const changesSoon = ambient.some((a) => a.t > t && a.t <= t + 0.7);
      if (before && !changesSoon) flashCues.push({ t: t + 0.7, hex: before.hex, br: before.br });
    }
    for (const a of ambient.concat(flashCues).sort((x, y) => x.t - y.t)) {
      cues.push({ at: a.t, ambient: '#' + a.hex, brightness: a.br, note: a.note });
    }

    // ---------------------------------------------------------- 3) 바람·송풍 방향: 사람이 고른 장면만
    scenes.forEach((sc, k) => {
      const next = scenes[k + 1];
      const keys = (sc.keys || []).slice().sort((a, b) => a[0] - b[0]);
      const baseAt = (t) => { let v = sc.wind ?? 0; for (const [kt, lv] of keys) if (kt <= t) v = lv; return v; };
      if (sc.dir) cues.push({ at: sc.from, direction: sc.dir, hold: Math.round((sc.to - sc.from) * 1000), note: sc.label });
      if (sc.wind != null) cues.push({ at: sc.from, wind: sc.wind, note: sc.label });
      keys.forEach(([t, lv, note]) => cues.push({ at: t, wind: lv, note: note || sc.label }));
      const gustTimes = [];
      if (sc.gusts) gustTimes.push(...motionPeaks(S, sc.from, sc.to, 3));
      flashes.filter((t) => t >= sc.from && t < sc.to).forEach((t) => { if (gustTimes.every((g) => Math.abs(g - t) >= 1.5)) gustTimes.push(t); });
      gustTimes.sort((a, b) => a - b).forEach((t) => {
        const base = baseAt(t);
        cues.push({ at: t, wind: round(clamp(base + (sc.gusts || 0.4), 0, 1), 2), note: sc.gustNote || '돌풍' });
        if (t + 1.2 < sc.to) cues.push({ at: t + 1.2, wind: base });
      });
      if (!next || next.from > sc.to + 0.01) cues.push({ at: sc.to, wind: 0 });
    });
    for (const [a] of credits) cues.push({ at: a, wind: 0, note: '엔딩 크레딧' });

    // ---------------------------------------------------------- 4) 천장 조명: 실내등 암전, 독서등 번쩍임
    // 실내등은 처음부터 엔딩 크레딧 전까지 끈다(spec.blackout: false로 끌 수 있음).
    if (spec.blackout !== false) {
      const until = credits.length ? credits[0][0] : (spec.duration || S[S.length - 1][0]);
      cues.push({ at: 0, cabin: 'off', hold: Math.round(until * 1000), note: '상영 시작 · 실내등 암전' });
    }
    // 독서등은 섬광 순간에 0.4초만 켠다. 2초 안에 이어지는 섬광은 건너뛴다(spec.readingFlash: false로 끌 수 있음).
    if (spec.readingFlash !== false) {
      let lastReading = -10;
      for (const t of flashes) {
        if (t - lastReading < 2) continue;
        cues.push({ at: t, reading: 'on', hold: 400, note: '번쩍임' });
        lastReading = t;
      }
    }

    // ---------------------------------------------------------- 5) 정리·직렬화
    const order = { cabin: 0, ambient: 1, reading: 2, direction: 3, wind: 4 };
    const kind = (c) => (c.cabin ? 'cabin' : c.reading ? 'reading' : c.ambient ? 'ambient' : c.direction ? 'direction' : 'wind');
    cues.sort((a, b) => a.at - b.at || order[kind(a)] - order[kind(b)]);

    const line = (c) => {
      const o = { at: fmtTime(c.at) };
      if (c.ambient) { o.ambient = c.ambient; o.brightness = c.brightness; }
      if (c.direction) o.direction = c.direction;
      if (c.wind != null) o.wind = c.wind;
      if (c.cabin) o.cabin = c.cabin;
      if (c.reading) o.reading = c.reading;
      if (c.hold) o.hold = c.hold;
      if (c.note) o.note = c.note;
      return '    ' + JSON.stringify(o);
    };
    const head = {
      id: spec.id,
      order: spec.order,
      title: spec.title,
      year: spec.year,
      description: spec.description,
      media: spec.media,
      duration: fmtTime(spec.duration || S[S.length - 1][0]),
      attribution: spec.attribution,
      license: spec.license,
      licenseUrl: spec.licenseUrl,
      generator: 'tools/track-maker (영상 분석 + scenes/' + spec.id + '.json)',
      highlights: (spec.highlights || []).map(([t, label]) => ({ at: fmtTime(t), label })),
      subtitles: spec.subtitles
    };
    const headJson = JSON.stringify(head, null, 2);
    return headJson.slice(0, -2) + ',\n  "cues": [\n' + cues.map(line).join(',\n') + '\n  ]\n}\n';
  }

  const api = { generateTrack, lightTarget, motionPeaks, fmtTime };
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  else root.Cabin4DTrack = api;
})(typeof window !== 'undefined' ? window : globalThis);
