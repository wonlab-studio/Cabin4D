/*
 * Cabin 4D 영상 분석기(브라우저 전용)
 *
 * 영상을 step초 간격으로 탐색하며 프레임을 64px 폭으로 줄여 읽고, 샘플마다
 *   [t, 밝기L(0~255), r, g, b(채도 가중 평균색), 채도가중치, 직전 프레임과의 차이, 밝은 화소 비율]
 * 을 기록한다. generator.js가 이 배열로 무드등·섬광·돌풍 큐를 만든다.
 *
 * 주의: 다른 출처(origin)의 영상은 캔버스가 오염돼 픽셀을 읽을 수 없다.
 * 로컬 파일(<input type=file>)이나 같은 출처의 영상을 쓴다.
 */
(function (root) {
  'use strict';

  async function analyzeVideo(src, opts = {}) {
    const step = opts.step || 0.5;
    const onProgress = opts.onProgress || (() => {});
    const thumbEvery = opts.thumbEvery || 0; // 0이면 썸네일 없음
    const v = document.createElement('video');
    v.muted = true; v.preload = 'auto'; v.src = src;
    await new Promise((res, rej) => { v.onloadedmetadata = res; v.onerror = () => rej(new Error('영상을 열 수 없음: ' + (v.error && v.error.message))); });

    const W = 64, H = Math.max(8, Math.round(64 * v.videoHeight / v.videoWidth));
    const c = document.createElement('canvas'); c.width = W; c.height = H;
    const g = c.getContext('2d', { willReadFrequently: true });
    const TW = 240, TH = Math.round(240 * v.videoHeight / v.videoWidth);
    const tc = document.createElement('canvas'); tc.width = TW; tc.height = TH;
    const tg = tc.getContext('2d');

    const t0 = opts.from || 0, t1 = Math.min(opts.to || Infinity, v.duration - 0.05);
    const samples = [], thumbs = [];
    let prev = null, nextThumb = t0;
    for (let t = t0; t <= t1 + 1e-6; t += step) {
      await new Promise((res) => { const to = setTimeout(res, 8000); v.onseeked = () => { clearTimeout(to); res(); }; v.currentTime = t; });
      g.drawImage(v, 0, 0, W, H);
      const d = g.getImageData(0, 0, W, H).data;
      let L = 0, sr = 0, sg = 0, sb = 0, sw = 0, diff = 0, hi = 0;
      const cur = new Uint8Array(W * H);
      for (let i = 0, p = 0; i < d.length; i += 4, p++) {
        const r = d[i], gg = d[i + 1], b = d[i + 2];
        const y = 0.2126 * r + 0.7152 * gg + 0.0722 * b;
        L += y; cur[p] = y; if (y > 200) hi++;
        const mx = Math.max(r, gg, b), mn = Math.min(r, gg, b);
        const s = mx === 0 ? 0 : (mx - mn) / mx;
        const w = s * s * (mx / 255); // 채도 높고 밝은 화소일수록 색 결정에 크게 반영
        sr += r * w; sg += gg * w; sb += b * w; sw += w;
        if (prev) diff += Math.abs(y - prev[p]);
      }
      const n = W * H;
      samples.push([
        +t.toFixed(2), +(L / n).toFixed(1),
        sw > 0 ? Math.round(sr / sw) : 0, sw > 0 ? Math.round(sg / sw) : 0, sw > 0 ? Math.round(sb / sw) : 0,
        +(sw / n).toFixed(3), prev ? +(diff / n).toFixed(1) : 0, +(hi / n).toFixed(3)
      ]);
      prev = cur;
      if (thumbEvery > 0 && t >= nextThumb - 1e-6) {
        tg.drawImage(v, 0, 0, TW, TH);
        thumbs.push([+t.toFixed(2), tc.toDataURL('image/jpeg', 0.6)]);
        nextThumb += thumbEvery;
      }
      onProgress(t, t1);
    }
    return { duration: v.duration, width: v.videoWidth, height: v.videoHeight, samples, thumbs };
  }

  root.Cabin4DAnalyzer = { analyzeVideo };
})(window);
