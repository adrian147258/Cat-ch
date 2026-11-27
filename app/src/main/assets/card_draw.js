(() => {
  "use strict";
  const params = new URLSearchParams(location.search);
  const root = document.documentElement;
  root.classList.toggle("dark", params.get("dark") === "1");
  const tiers = {
    green: { label: "绿卡", count: 20, duration: 620, note: 523 },
    blue: { label: "蓝卡", count: 10, duration: 760, note: 587 },
    purple: { label: "紫卡", count: 8, duration: 900, note: 659 },
    gold: { label: "金卡", count: 5, duration: 1100, note: 784 }
  };
  const tier = params.get("tier");
  const index = Number(params.get("index"));
  const byId = id => document.getElementById(id);
  const hint = byId("hint"), pack = byId("pack"), reveal = byId("reveal");
  const card = byId("card"), seal = byId("seal"), foil = byId("foil");
  if (!Object.prototype.hasOwnProperty.call(tiers, tier) || !params.has("index") ||
      !Number.isInteger(index) || index < 0 || index >= tiers[tier].count) {
    hint.textContent = "卡片暂时无法显示，请关闭后回到卡册。";
    return;
  }

  const entry = tiers[tier];
  const preview = params.get("preview") === "1";
  const motionPreference = matchMedia("(prefers-reduced-motion: reduce)");
  const systemReduced = params.get("reduce") === "1";
  let reduced = systemReduced || motionPreference.matches;
  root.classList.toggle("reduced", reduced);
  let phase = preview ? "shown" : "sealed";
  let frame = 0, x = 0, y = 0, progress = 0, direction = 1;
  let drawnX = 0, drawnY = 0, lastFrame = 0, tearTimer = 0;
  let finger = null, suppressClick = false, muted = false, audio;
  const mute = byId("mute");
  function sound(frequency, duration) {
    if (muted || document.hidden) return;
    try {
      if (!audio) audio = new (window.AudioContext || window.webkitAudioContext)();
      if (audio.state === "suspended") audio.resume().catch(() => {});
      const oscillator = audio.createOscillator(), gain = audio.createGain();
      const start = audio.currentTime;
      oscillator.type = "sine";
      oscillator.frequency.setValueAtTime(frequency, start);
      oscillator.frequency.exponentialRampToValueAtTime(frequency * 1.5, start + duration);
      gain.gain.setValueAtTime(.055, start);
      gain.gain.exponentialRampToValueAtTime(.0001, start + duration);
      oscillator.connect(gain).connect(audio.destination);
      oscillator.onended = () => { oscillator.disconnect(); gain.disconnect(); };
      oscillator.start(start); oscillator.stop(start + duration);
    } catch (_) { /* Audio is optional on devices without Web Audio. */ }
  }
  mute.onclick = () => {
    muted = !muted;
    mute.setAttribute("aria-pressed", String(muted));
    if (muted && audio) audio.suspend().catch(() => {});
    else sound(660, .09);
  };

  function paint(now) {
    frame = 0;
    if (phase === "shown" && !reduced) {
      // Smooth incoming sensor and pointer samples in real time, including on 120 Hz screens.
      const elapsed = lastFrame ? Math.min(now - lastFrame, 64) : 16;
      const blend = 1 - Math.exp(-elapsed / 55);
      drawnX += (x - drawnX) * blend;
      drawnY += (y - drawnY) * blend;
      if (Math.abs(x - drawnX) + Math.abs(y - drawnY) > .003) {
        lastFrame = now; invalidate();
      } else {
        drawnX = x; drawnY = y; lastFrame = 0;
      }
      card.style.transform = "rotateX(" + (-drawnY * 9) + "deg) rotateY(" + (drawnX * 12) + "deg)";
      foil.style.transform = "translate3d(" + (drawnX * 22) + "%," + (drawnY * 11) + "%,0)";
    } else if (phase === "sealed") {
      if (reduced) return;
      seal.style.transform = "translateX(" + (direction * progress * 34) + "%) rotate(" + (-direction * progress * 12) + "deg)";
      pack.style.transform = "rotate(" + (-3 + direction * progress * 3) + "deg)";
    }
  }
  function invalidate() {
    if (!frame && !document.hidden) frame = requestAnimationFrame(paint);
  }
  const clamp = value => Math.min(1, Math.max(-1, value));
  function tilt(horizontal, vertical) {
    if (phase !== "shown" || reduced || !Number.isFinite(horizontal) || !Number.isFinite(vertical)) return;
    x = clamp(horizontal); y = clamp(vertical); invalidate();
  }
  window.setCardMotion = (horizontal, vertical) => { if (finger === null) tilt(horizontal, vertical); };
  function touchTilt(event) {
    const box = byId("object").getBoundingClientRect();
    tilt(2 * (event.clientX - box.left) / box.width - 1, 2 * (event.clientY - box.top) / box.height - 1);
  }
  reveal.onpointerdown = event => {
    if (finger !== null) return;
    finger = event.pointerId; reveal.setPointerCapture(finger); touchTilt(event);
  };
  reveal.onpointermove = event => {
    if (finger === event.pointerId || (finger === null && event.pointerType === "mouse")) touchTilt(event);
  };
  function rest(event) {
    if (finger !== null && finger !== event.pointerId) return;
    finger = null; tilt(0, 0);
  }
  reveal.onpointerup = reveal.onpointercancel = reveal.onlostpointercapture = rest;
  reveal.onpointerleave = event => { if (finger === null) rest(event); };

  let startX = 0, startY = 0, startProgress = 0, tearWidth = 0;
  pack.onpointerdown = event => {
    if (phase !== "sealed" || finger !== null || event.button !== 0) return;
    finger = event.pointerId; pack.setPointerCapture(finger);
    startX = event.clientX; startY = event.clientY;
    startProgress = progress; tearWidth = pack.offsetWidth;
    suppressClick = false;
  };
  pack.onpointermove = event => {
    if (phase !== "sealed" || event.pointerId !== finger) return;
    const dx = event.clientX - startX, dy = event.clientY - startY;
    if (Math.abs(dx) + Math.abs(dy) > 8) suppressClick = true;
    if (Math.abs(dx) < 6 || Math.abs(dx) < Math.abs(dy) * .6) return;
    if (!startProgress) direction = dx < 0 ? -1 : 1;
    progress = Math.min(1, Math.max(progress, startProgress + Math.abs(dx) / (tearWidth * .86)));
    invalidate();
  };
  pack.onpointerup = event => {
    if (event.pointerId !== finger) return;
    finger = null;
    if (progress >= .55) open();
  };
  pack.onpointercancel = pack.onlostpointercapture = event => {
    if (event.pointerId === finger) { finger = null; suppressClick = true; }
  };
  pack.onclick = event => { if (!suppressClick || event.detail === 0) open(); };

  const art = byId("art");
  art.onerror = () => { hint.textContent = "卡图未能载入，请关闭后在卡册重试。"; };
  art.src = "cards/" + tier + "_" + index + ".webp";
  root.classList.add(tier);
  root.style.setProperty("--duration", entry.duration + "ms");
  byId("tierLabel").textContent = entry.label;
  const edition = String(index + 1).padStart(2, "0") + " / " + String(entry.count).padStart(2, "0");
  byId("editionNumber").textContent = edition;
  reveal.setAttribute("aria-label", entry.label + "，第 " + (index + 1) + " 款，共 " + entry.count + " 款");
  function updateCardHint() {
    if (!art.complete || art.naturalWidth) hint.textContent = reduced ? entry.label : "轻触拖动或转动手机查看光泽";
  }
  function showCard(animate) {
    clearTimeout(tearTimer);
    phase = "shown"; pack.hidden = true; reveal.hidden = false;
    if (animate && !reduced) reveal.classList.add("arriving");
    if (animate) { sound(entry.note, .22); reveal.focus(); }
    updateCardHint();
  }
  reveal.addEventListener("animationend", event => {
    if (event.animationName === "arrive") reveal.classList.remove("arriving");
  });
  function finishTear() { if (phase === "opening") showCard(true); }
  pack.addEventListener("animationend", event => {
    if (event.animationName === "unwrap") finishTear();
  });
  function open() {
    if (phase !== "sealed") return;
    phase = "opening"; finger = null; pack.disabled = true;
    sound(260, .11); hint.textContent = "";
    if (reduced) { showCard(true); return; }
    const tearTime = 305;
    pack.style.setProperty("--tear-time", tearTime + "ms");
    pack.style.setProperty("--tear-start", (direction * progress * 34) + "%");
    pack.style.setProperty("--tear-angle", (-direction * progress * 12) + "deg");
    pack.style.setProperty("--release", (direction * 46) + "%");
    pack.style.setProperty("--release-angle", (-direction * 22) + "deg");
    pack.classList.add("opening");
    // A WebView may skip CSS animation events when it is briefly backgrounded.
    tearTimer = setTimeout(finishTear, tearTime + 80);
  }
  function updateMotionPreference() {
    reduced = systemReduced || motionPreference.matches;
    root.classList.toggle("reduced", reduced);
    if (reduced) {
      if (frame) cancelAnimationFrame(frame);
      frame = 0; lastFrame = 0; x = y = drawnX = drawnY = 0;
      card.style.transform = foil.style.transform = seal.style.transform = pack.style.transform = "";
      reveal.classList.remove("arriving");
      finishTear();
    }
    if (phase === "shown") updateCardHint();
  }
  motionPreference.addListener(updateMotionPreference);
  if (preview) showCard(false);
  else {
    byId("mascot").src = "pack_cat.webp";
    pack.hidden = false; hint.textContent = "滑动封口，或轻点拆开";
  }
  document.addEventListener("visibilitychange", () => {
    if (document.hidden) {
      if (frame) cancelAnimationFrame(frame);
      frame = 0; lastFrame = 0; finger = null;
      if (audio) audio.suspend().catch(() => {});
    } else invalidate();
  });
})();
