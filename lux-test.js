/* Lux Test lab — add-on for lux.html
   Adds a "Test" tab: start timed fragrance tests, log impressions over time,
   pull the weather for your city, rate, and keep a permanent average per fragrance.
   Data is stored inside Lux's own state (S.tt), so Lux backups include it. */
(function () {
'use strict';
try { if (typeof S === 'undefined' || typeof render !== 'function' || typeof openModal !== 'function') return; } catch (e) { return; }

/* ---------- constants ---------- */
const H = 36e5;
const SPOTS = [['LW','Left wrist'],['RW','Right wrist'],['LA','Left upper arm'],['RA','Right upper arm']];
const TAGS = ['Vanilla','Amber','Woody','Musk','Tobacco','Citrus','Spicy','Powdery','Sweet','Smoky','Leather','Fresh','Aquatic','Green','Fruity','Incense','Oud','Ambroxan','Coffee','Rose','Iris','Patchouli','Vetiver','Soapy','Boozy','Metallic','Salty','Creamy'];
const QUICK = ['Vanilla','Amber','Woody','Musk','Tobacco','Citrus','Spicy','Powdery'];
const CRIT = [['longevity','Longevity'],['sillage','Sillage'],['skin','Skin scent']];

/* ---------- state helpers ---------- */
function tt(){ if (!S.tt) S.tt = { sessions:[], city:null, venues:[] }; if (!S.tt.sessions) S.tt.sessions = []; if (!S.tt.venues) S.tt.venues = []; return S.tt; }
const mean = a => { a = a.filter(Number.isFinite); return a.length ? a.reduce((x,y)=>x+y,0)/a.length : null; };
const r1 = n => Math.round(n*10)/10;
function relStr(ms){ const m = Math.max(0, Math.round(ms/60000)), h = Math.floor(m/60), mm = m%60; return h ? h+'h '+String(mm).padStart(2,'0')+'m' : mm+'m'; }
const clock = t => new Date(t).toLocaleTimeString([], { hour:'2-digit', minute:'2-digit' });
const dshort = t => new Date(t).toLocaleDateString(undefined, { day:'numeric', month:'short', year:'numeric' });
function toLocalInput(t){ const d = new Date(t); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0,16); }
const fromLocalInput = s => s ? new Date(s).getTime() : Date.now();
const isDone = s => s.status === 'done';
const score = s => s.rating ? mean([s.rating.longevity, s.rating.sillage, s.rating.skin]) : null;
const elapsed = s => (s.fadedAt || Date.now()) - s.t0;
const pv = s => (s.pid && byId(s.pid)) || { id:'t'+s.id, name:s.name, brand:s.brand, fam:s.fam || '' };
function spotsLabel(s){ if (Date.now() - s.t0 > 24*H || !s.spots || !s.spots.length) return ''; return s.spots.map(c => (SPOTS.find(x=>x[0]===c)||[0,c])[1]).join(' + '); }
const WX = c => c == null ? '' : c === 0 ? 'Clear' : c <= 2 ? 'Partly cloudy' : c === 3 ? 'Overcast' : c <= 48 ? 'Fog' : c <= 57 ? 'Drizzle' : c <= 67 ? 'Rain' : c <= 77 ? 'Snow' : c <= 82 ? 'Showers' : c <= 86 ? 'Snow showers' : 'Thunderstorm';
const wxLine = w => w ? [w.temp != null ? w.temp+'°' : '', w.hum != null ? w.hum+'% humidity' : '', WX(w.code), w.city].filter(Boolean).join(' · ') : '';
const bucket = t => t == null ? null : t < 8 ? 'Cold' : t <= 18 ? 'Mild' : 'Warm';
const activeList = () => tt().sessions.filter(s => !isDone(s)).sort((a,b) => b.t0 - a.t0);
const refresh = () => { if (ui.tab === 'test') render(); };
const closeAll = () => closeModal();

/* ---------- grouping and stats ---------- */
/* Tests are grouped by fragrance and house, so tests of a deleted bottle and of the same fragrance
   added again later (a new bottle, a new id) share one entry and one average. */
function keyOf(s){ const p = s.pid && byId(s.pid); return 'n:'+norm(((p ? p.brand : s.brand)||'')+' '+(p ? p.name : s.name)); }
function groups(){
  const map = new Map();
  tt().sessions.forEach(s => {
    const k = keyOf(s); let g = map.get(k);
    if (!g) { g = { key:k, pid:null, name:s.name, brand:s.brand, fam:s.fam || '', sessions:[] }; map.set(k, g); }
    g.sessions.push(s);
    if (s.pid) { const p = byId(s.pid); if (p) { g.pid = p.id; g.name = p.name; g.brand = p.brand; g.fam = p.fam; } } /* link to a bottle that still exists */
  });
  return [...map.values()].map(g => {
    g.done = g.sessions.filter(isDone).sort((a,b) => b.t0 - a.t0);
    g.avg = mean(g.done.map(score));
    g.crit = {}; CRIT.concat([['value','Value']]).forEach(([k]) => g.crit[k] = mean(g.done.map(s => s.rating && s.rating[k] > 0 ? s.rating[k] : NaN)));
    g.hours = mean(g.done.map(s => s.fadedAt ? (s.fadedAt - s.t0)/H : NaN));
    g.last = Math.max(...g.sessions.map(s => s.t0));
    g.buy = g.done.length ? g.done[0].wouldBuy : null;
    g.price = (g.sessions.slice().sort((a,b)=>b.t0-a.t0).find(s => s.price != null) || {}).price;
    return g;
  });
}
function condStats(g){
  const out = {};
  g.done.forEach(s => { const b = bucket(s.weather && s.weather.temp); if (b) (out[b] = out[b] || []).push(score(s)); });
  return ['Cold','Mild','Warm'].map(b => out[b] ? b+' '+r1(mean(out[b]))+' ('+out[b].length+')' : null).filter(Boolean);
}
function tagCounts(g){
  const c = {}; g.sessions.forEach(s => (s.notes||[]).forEach(n => (n.tags||[]).forEach(t => c[t] = (c[t]||0)+1)));
  return Object.entries(c).sort((a,b) => b[1]-a[1]);
}

/* ---------- weather (Open-Meteo, no key) ---------- */
async function jget(url){
  const ctl = new AbortController(), to = setTimeout(() => ctl.abort(), 9000);
  try { const r = await fetch(url, { signal: ctl.signal }); if (!r.ok) throw 0; return await r.json(); } finally { clearTimeout(to); }
}
async function geocode(q){
  try {
    const j = await jget('https://geocoding-api.open-meteo.com/v1/search?name='+encodeURIComponent(q)+'&count=5&language=en&format=json');
    return (j.results || []).map(r => ({ label:[r.name, r.admin1, r.country].filter(Boolean).join(', '), name:r.name, lat:r.latitude, lon:r.longitude }));
  } catch (e) { return null; }
}
async function getWeather(city, t){
  if (!city) return null;
  try {
    const ageH = (Date.now() - t) / H, base = 'https://api.open-meteo.com/v1/forecast?latitude='+city.lat+'&longitude='+city.lon+'&timezone=auto';
    if (ageH < 1.5) {
      const j = await jget(base+'&current=temperature_2m,relative_humidity_2m,weather_code');
      const c = j.current; if (!c) return null;
      return { city:city.name || city.label, temp:Math.round(c.temperature_2m), hum:Math.round(c.relative_humidity_2m), code:c.weather_code, at:Date.now() };
    }
    if (ageH > 92*24) return null;
    const past = Math.min(92, Math.ceil(ageH/24)+1);
    const j = await jget(base+'&hourly=temperature_2m,relative_humidity_2m,weather_code&past_days='+past+'&forecast_days=1');
    const off = (j.utc_offset_seconds || 0) * 1000; let best = -1, bd = 1e18;
    (j.hourly.time || []).forEach((ts, i) => { const d = Math.abs(Date.parse(ts+'Z') - off - t); if (d < bd) { bd = d; best = i; } });
    if (best < 0 || j.hourly.temperature_2m[best] == null) return null;
    return { city:city.name || city.label, temp:Math.round(j.hourly.temperature_2m[best]), hum:Math.round(j.hourly.relative_humidity_2m[best]), code:j.hourly.weather_code[best], at:Date.now() };
  } catch (e) { return null; }
}
async function attachWeather(s){
  const w = await getWeather(tt().city, s.t0);
  if (w) { s.weather = w; save(); if (ui.tab === 'test' && $('#modal').hidden) render(); }
  else toast('Weather not available. You can enter it by hand in the test details.');
}

/* ---------- radar ---------- */
function radar(axes){
  const n = axes.length; if (n < 3) return '';
  const R = 62, cx = 100, cy = 88;
  const pt = (i, r) => { const a = -Math.PI/2 + i*2*Math.PI/n; return [cx + Math.cos(a)*r, cy + Math.sin(a)*r]; };
  const poly = f => axes.map((_, i) => pt(i, R*f).map(x => x.toFixed(1)).join(',')).join(' ');
  let g = [1/3, 2/3, 1].map(f => '<polygon points="'+poly(f)+'" fill="none" stroke="var(--line2)" stroke-width="1"/>').join('');
  axes.forEach((_, i) => { const p = pt(i, R); g += '<line x1="'+cx+'" y1="'+cy+'" x2="'+p[0].toFixed(1)+'" y2="'+p[1].toFixed(1)+'" stroke="var(--line)"/>'; });
  g += '<polygon points="'+axes.map((a, i) => pt(i, R*clamp(a.v/10,0,1)).map(x => x.toFixed(1)).join(',')).join(' ')+'" fill="var(--gold)" fill-opacity=".28" stroke="var(--gold)" stroke-width="2" stroke-linejoin="round"/>';
  axes.forEach((a, i) => {
    const p = pt(i, R*clamp(a.v/10,0,1)), q = pt(i, R+15), c = Math.cos(-Math.PI/2 + i*2*Math.PI/n);
    g += '<circle cx="'+p[0].toFixed(1)+'" cy="'+p[1].toFixed(1)+'" r="3" fill="var(--gold)"/>';
    g += '<text x="'+q[0].toFixed(1)+'" y="'+(q[1]+3).toFixed(1)+'" text-anchor="'+(c > .3 ? 'start' : c < -.3 ? 'end' : 'middle')+'" font-size="10" fill="var(--tx2)" font-family="var(--sans)">'+esc(a.l)+' '+r1(a.v)+'</text>';
  });
  return '<svg viewBox="0 0 200 178" class="tt-radar" role="img" aria-label="Performance radar">'+g+'</svg>';
}

/* ---------- styles ---------- */
const css = document.createElement('style');
css.textContent = `
.nb{position:relative}
.nb.has-live::after{content:"";position:absolute;top:8px;right:10px;width:8px;height:8px;border-radius:50%;background:var(--gold)}
.tt-act{display:flex;flex-direction:column;gap:14px}
.tt-top{display:flex;gap:16px;align-items:flex-start}
.tt-bt{flex:none;width:64px}.tt-bt .bt{width:64px;height:102px;margin:0}
.tt-id{flex:1;min-width:0}.tt-id h3{margin-bottom:2px}
.tt-clock{text-align:right;flex:none}
.tt-clock b{display:block;font:500 30px/1 var(--serif);color:var(--gold2)}
.tt-clock span{font-size:12px;color:var(--tx3)}
.tt-spot{display:inline-block;margin-top:8px;padding:4px 11px;border:1px solid var(--line2);border-radius:999px;font-size:12.5px;color:var(--tx2)}
.tt-quick{display:flex;gap:7px;flex-wrap:wrap}
.tt-quick .chip{padding:6px 13px;font-size:13px}
.tt-last{font-size:13.5px;color:var(--tx2);border-left:2px solid var(--gold);padding-left:10px}
.tt-wx{font-size:13px;color:var(--tx3)}
.tt-g{display:flex;gap:16px;align-items:center;padding:14px 4px;border-bottom:1px solid var(--line);cursor:pointer;border-radius:12px}
.tt-g:hover{background:var(--glass)}
.tt-g .th{flex:none;width:34px;height:54px}.tt-g .th .bt{width:34px;height:54px;margin:0;filter:none}
.tt-g .mid{flex:1;min-width:0}
.tt-g .t1{font:600 18px/1.15 var(--serif)}.tt-g .t2{font-size:12.5px;color:var(--tx3)}
.tt-bars{display:flex;gap:4px;margin-top:7px}.tt-bars i{flex:1;height:4px;border-radius:4px;background:var(--line);overflow:hidden;position:relative}
.tt-bars i b{position:absolute;left:0;top:0;bottom:0;background:var(--gold)}
.tt-score{flex:none;text-align:right;font:500 32px/1 var(--serif);color:var(--gold2)}
.tt-score small{display:block;font:500 11px var(--sans);color:var(--tx3);margin-top:3px}
.tt-pick{display:flex;justify-content:space-between;align-items:center;gap:10px;padding:11px 14px;border:1px solid var(--line2);border-radius:12px;background:var(--bg2)}
.tt-pick small{color:var(--tx3)}.tt-pick button{color:var(--gold2);font-size:13.5px}
.tt-chk{display:flex;gap:10px;align-items:flex-start;font-size:13.5px;color:var(--tx2);margin:2px 0 14px}
.tt-chk input{width:20px;height:20px;flex:none;margin-top:1px}
.tt-radar{width:100%;max-width:330px;display:block;margin:6px auto}
.tt-note{display:flex;gap:12px;padding:10px 2px;border-bottom:1px solid var(--line);font-size:14px}
.tt-note .rt{flex:none;width:62px;color:var(--gold2);font-weight:600;font-size:13px}
.tt-note .bd{flex:1;min-width:0}.tt-note small{color:var(--tx3);display:block}
.tt-note button{color:var(--tx3);font-size:18px;width:30px;height:30px;border-radius:50%}
.tt-note button:hover{color:var(--danger)}
.tt-rng{display:flex;align-items:center;gap:14px}.tt-rng input{flex:1}
.tt-rng b{font:500 26px/1 var(--serif);min-width:44px;text-align:right;color:var(--gold2)}
.tt-scr{background:var(--bg2);border:1px solid var(--line);border-radius:14px;padding:12px 14px;font-size:14px;margin-top:8px;min-height:52px}
.tt-tl{width:100%;height:auto;display:block}
.tt-kv{display:flex;justify-content:space-between;gap:12px;padding:9px 0;border-bottom:1px solid var(--line);font-size:14px}
.tt-kv span:first-child{color:var(--tx3)}
.tt-res button{display:block;width:100%;text-align:left;padding:11px 14px;border-bottom:1px solid var(--line);font-size:14px}
.tt-res button:hover{background:var(--glass);color:var(--gold2)}
.tt-sec{margin:26px 0 12px;display:flex;justify-content:space-between;align-items:baseline}
`;
document.head.appendChild(css);

/* ---------- main view ---------- */
function activeCard(s){
  const p = pv(s), last = (s.notes || []).slice(-1)[0], sp = spotsLabel(s);
  const done = !!s.fadedAt;
  return `<div class="card tt-act"><div class="tt-top"><div class="tt-bt">${bt(p, 1)}</div>
  <div class="tt-id"><h3>${esc(s.name)}</h3><div class="muted" style="font-size:13px">${esc(s.brand)}${s.venue ? ' \u00b7 '+esc(s.venue) : ''}</div>${sp ? `<span class="tt-spot">${esc(sp)}</span>` : ''}</div>
  <div class="tt-clock"><b class="tt-el" data-t0="${s.t0}" data-f="${s.fadedAt || ''}">+${relStr(elapsed(s))}</b><span>${s.sprays} ${s.sprays === 1 ? 'spray' : 'sprays'}${done ? ' \u00b7 faded' : ''}</span></div></div>
  ${s.weather ? `<div class="tt-wx">${esc(wxLine(s.weather))}</div>` : ''}
  ${done ? '' : `<div class="tt-quick">${QUICK.map(t => `<button class="chip" data-ta="qnote" data-id="${s.id}" data-tag="${t}">${t}</button>`).join('')}</div>`}
  ${last ? `<div class="tt-last">+${relStr(last.t - s.t0)} \u00b7 ${esc((last.tags || []).join(', ') || last.text || 'note')}</div>` : ''}
  <div class="acts"><button class="btn sm" data-ta="note" data-id="${s.id}">Add impression</button>${done ? '' : `<button class="btn ghost sm" data-ta="fade" data-id="${s.id}">Faded out</button>`}<button class="btn ghost sm" data-ta="rate" data-id="${s.id}">Rate</button><button class="btn ghost sm" data-ta="open" data-id="${s.id}">Details</button></div></div>`;
}
function groupRow(g){
  const bars = CRIT.map(([k]) => `<i><b style="width:${g.crit[k] ? g.crit[k]*10 : 0}%"></b></i>`).join('');
  return `<div class="tt-g" data-ta="group" data-k="${esc(g.key)}" role="button" tabindex="0"><div class="th">${bt(g.pid && byId(g.pid) || { id:'g'+g.key, name:g.name, brand:g.brand, fam:g.fam }, .7)}</div>
  <div class="mid"><div class="t1">${esc(g.name)}</div><div class="t2">${esc(g.brand)} \u00b7 ${g.done.length} ${g.done.length === 1 ? 'test' : 'tests'}${g.buy === 'yes' ? ' \u00b7 would buy' : ''}</div><div class="tt-bars">${bars}</div></div>
  <div class="tt-score">${r1(g.avg)}<small>average</small></div></div>`;
}
let TQ = '', TSORT = 'best';
function listHtml(){
  let gs = groups().filter(g => g.done.length);
  const q = norm(TQ).split(' ').filter(Boolean);
  if (q.length) gs = gs.filter(g => q.every(w => norm(g.brand+' '+g.name).includes(w)));
  if (TSORT === 'best') gs.sort((a,b) => b.avg - a.avg);
  else if (TSORT === 'recent') gs.sort((a,b) => b.last - a.last);
  else gs.sort((a,b) => (a.brand+a.name).localeCompare(b.brand+b.name));
  if (!gs.length) return `<p class="muted" style="padding:16px 4px">${TQ ? 'No tested fragrance matches.' : 'Nothing rated yet. Start a test, then rate it and it lands here with a permanent average.'}</p>`;
  return gs.map(groupRow).join('');
}
function testHtml(){
  const all = tt().sessions, gs = groups().filter(g => g.done.length), act = activeList();
  const doneS = all.filter(isDone), hours = mean(doneS.map(s => s.fadedAt ? (s.fadedAt - s.t0)/H : NaN));
  const kp = [[all.length, 'Tests'], [gs.length, 'Rated fragrances'], [doneS.length ? r1(mean(doneS.map(score))) : '\u2014', 'Average score'], [hours != null ? r1(hours)+' h' : '\u2014', 'Average longevity'], [gs.filter(g => g.buy === 'yes').length, 'Would buy']];
  return `<header class="vh"><div><h1>Test lab</h1><p class="sub2">${act.length ? act.length+' running now' : 'Timed tests, impressions and a lasting average per fragrance'}</p></div><div class="acts"><button class="btn" data-ta="start">Start test</button></div></header>
  ${act.length ? act.map(activeCard).join('<div style="height:14px"></div>') : `<div class="card"><p class="muted">No test running. Tap Start test the moment you spray. The clock starts then, and every impression is stamped with the time since.</p></div>`}
  <div class="kpis" style="margin-top:22px">${kp.map(x => `<div class="kpi"><b>${x[0]}</b><span>${x[1]}</span></div>`).join('')}</div>
  <div class="card"><div class="ch" style="margin-bottom:10px"><h3>Tested fragrances</h3><select data-tc="tsort" style="width:auto"><option value="best"${TSORT==='best'?' selected':''}>Best rated</option><option value="recent"${TSORT==='recent'?' selected':''}>Most recent</option><option value="name"${TSORT==='name'?' selected':''}>Name</option></select></div>
  <div class="fg"><input data-ti="tq" placeholder="Search tested fragrances" autocomplete="off" value="${esc(TQ)}"></div><div id="ttList">${listHtml()}</div></div>`;
}

/* ---------- render hook + nav ---------- */
const _render = window.render;
function afterRender(){
  const b = $('#nav .nb[data-t="test"]'); if (b) b.classList.toggle('has-live', activeList().length > 0);
  if (ui.tab === 'shelf') { const acts = $('#view .vh .acts'); if (acts && !acts.querySelector('[data-t="test"]')) acts.insertAdjacentHTML('afterbegin', '<button class="btn ghost" data-act="tab" data-t="test">Test lab</button>'); }
}
window.render = function(){
  if (ui.tab === 'test') {
    $('#view').innerHTML = testHtml();
    $$('#nav .nb').forEach(b => b.classList.toggle('on', b.dataset.t === 'test'));
    const own = owned(); $('#railFoot').innerHTML = own.length+' in the cabinet<br>'+fmt(LIB.length)+' in the library';
  } else _render();
  afterRender();
};
(function addNav(){
  const nav = $('#nav'); if (!nav || nav.querySelector('[data-t="test"]')) return;
  nav.insertAdjacentHTML('beforeend', '<button class="nb" data-act="tab" data-t="test"><svg viewBox="0 0 24 24"><path d="M9 3h6M10 3v6l-5 9a2 2 0 0 0 2 3h10a2 2 0 0 0 2-3l-5-9V3"/><path d="M7.5 15h9"/></svg>Test</button>');
})();
setInterval(() => { if (ui.tab === 'test') $$('.tt-el').forEach(el => { const f = +el.dataset.f; el.textContent = '+'+relStr((f || Date.now()) - (+el.dataset.t0)); }); }, 30000);
document.addEventListener('visibilitychange', () => { if (!document.hidden && ui.tab === 'test' && $('#modal').hidden) render(); });

/* ---------- start sheet ---------- */
let ST = null;
function newST(pre){ return Object.assign({ pid:null, name:'', brand:'', fam:'', q:'', spots:[], n:2, venue:'', price:'', earlier:false, t:Date.now(), log:false, city:tt().city, showCity:!tt().city, cres:null, cmsg:'' }, pre || {}); }
function startHtml(){
  const c = ST.city, cab = ST.pid && byId(ST.pid);
  return `<button class="x" data-act="close" aria-label="Close">\u00d7</button><h2>Start a test</h2>
  <div class="fg" style="margin-top:16px"><label>Fragrance</label>${ST.name ? `<div class="tt-pick"><span><b>${esc(ST.name)}</b> <small>${esc(ST.brand)}${cab ? (cab.shelf === 'wish' ? ' \u00b7 on your wishlist' : ' \u00b7 in cabinet') : ''}</small></span><button data-ta="tclear">Change</button></div>` : `<input id="ts-q" data-ti="tsq" placeholder="Search your cabinet or library" autocomplete="off" value="${esc(ST.q)}"><div class="sugg" id="ts-sugg"></div>`}</div>
  <div class="fg"><span class="lb">Where on the body</span><div class="chips">${SPOTS.map(([k, l]) => `<button type="button" class="chip${ST.spots.includes(k) ? ' on' : ''}" data-ta="tspot" data-v="${k}">${l}</button>`).join('')}</div></div>
  <span class="lb" style="text-align:center">Sprays</span><div class="stepper" style="margin:8px 0 16px"><button data-ta="tstep" data-d="-1" aria-label="Fewer">\u2212</button><b id="ts-n" style="font-size:44px">${ST.n}</b><button data-ta="tstep" data-d="1" aria-label="More">+</button></div>
  <div class="g2"><div class="fg"><label for="ts-venue">Where tested</label><input id="ts-venue" list="tt-venues" placeholder="Shop or home" value="${esc(ST.venue)}" autocomplete="off"><datalist id="tt-venues">${tt().venues.map(v => `<option value="${esc(v)}">`).join('')}</datalist></div>
  <div class="fg"><label for="ts-price">Price (${esc(cur())}, optional)</label><input id="ts-price" type="number" min="0" step="0.5" value="${esc(ST.price)}"></div></div>
  <div class="fg"><label class="tt-chk" style="margin:0"><input type="checkbox" id="ts-early" data-tc="tearly"${ST.earlier ? ' checked' : ''}> I sprayed earlier</label>${ST.earlier ? `<input type="datetime-local" id="ts-t" value="${toLocalInput(ST.t)}" max="${toLocalInput(Date.now())}" style="margin-top:8px">` : ''}</div>
  <div class="fg"><span class="lb">Weather</span>${c && !ST.showCity ? `<div class="tt-pick"><span>Weather for <b>${esc(c.name || c.label)}</b></span><button data-ta="tcity">Change</button></div>` : `<div style="display:flex;gap:8px"><input id="ts-city" placeholder="City" autocomplete="off" value="${esc(c ? (c.name || c.label) : '')}"><button class="btn ghost sm" data-ta="tfind" style="flex:none">Find</button><button class="btn ghost sm" data-ta="tgeo" style="flex:none">Locate me</button></div><div class="tt-res" id="ts-cres">${cityRes()}</div>`}</div>
  ${cab && cab.shelf !== 'wish' ? `<label class="tt-chk"><input type="checkbox" id="ts-log"${ST.log ? ' checked' : ''}> Also log these sprays in my cabinet (updates the ml left and the Journal)</label>` : ''}
  <div class="foot"><button class="btn ghost" data-act="close">Cancel</button><button class="btn" data-ta="tgo">Start now</button></div>`;
}
function cityRes(){
  if (ST.cmsg) return `<p class="muted" style="padding:8px 2px;font-size:13.5px">${esc(ST.cmsg)}</p>`;
  return (ST.cres || []).map((r, i) => `<button type="button" data-ta="tpickcity" data-i="${i}">${esc(r.label)}</button>`).join('');
}
function readST(){
  const g = id => { const e = $('#'+id); return e ? e.value : undefined; };
  const v = g('ts-venue'); if (v !== undefined) ST.venue = v;
  const p = g('ts-price'); if (p !== undefined) ST.price = p;
  const t = g('ts-t'); if (t) ST.t = fromLocalInput(t);
  const l = $('#ts-log'); if (l) ST.log = l.checked;
}
function paintST(){ const p = $('#modal .panel'); if (p) { const sc = p.scrollTop; p.innerHTML = startHtml(); p.scrollTop = sc; } }
function openStart(pre){ ST = newST(pre); MODAL = 'tt'; openModal(startHtml()); }
function startSession(){
  readST();
  const typed = ($('#ts-q') || {}).value;
  if (!ST.name && typed && typed.trim()) { ST.name = typed.trim(); ST.brand = ''; }
  if (!ST.name) { toast('Pick a fragrance first'); return; }
  const s = { id:uid(), pid:ST.pid, name:ST.name, brand:ST.brand, fam:ST.fam, t0:ST.earlier ? ST.t : Date.now(), sprays:ST.n, spots:ST.spots.slice(), venue:ST.venue.trim(), price:ST.price === '' ? null : +ST.price, weather:null, notes:[], fadedAt:null, status:'active', rating:null, wouldBuy:null, comment:'', created:Date.now() };
  const d = tt(); d.sessions.push(s);
  if (s.venue && !d.venues.includes(s.venue)) d.venues.push(s.venue);
  if (ST.city) d.city = ST.city;
  const cab = s.pid && byId(s.pid);
  if (ST.log && cab && cab.shelf !== 'wish') { cab.ml = Math.max(0, Math.round((cab.ml - s.sprays/rateOf(cab))*10)/10); cab.sprays = (cab.sprays || 0) + s.sprays; S.wears.push({ id:uid(), pid:cab.id, t:s.t0, n:s.sprays }); }
  save(); closeAll(); ui.tab = 'test'; render(); toast('Test started');
  if (d.city) attachWeather(s);
}

/* ---------- impression sheet ---------- */
let NT = null;
function noteHtml(){
  const s = tt().sessions.find(x => x.id === NT.sid);
  return `<button class="x" data-act="close" aria-label="Close">\u00d7</button><h2>Add impression</h2><p class="muted" style="margin:4px 0 16px">${esc(s.name)} \u00b7 +${relStr(NT.t - s.t0)} after spraying</p>
  <div class="fg"><span class="lb">What do you smell</span><div class="chips">${TAGS.map(t => `<button type="button" class="chip${NT.tags.includes(t) ? ' on' : ''}" data-ta="ntag" data-v="${t}">${t}</button>`).join('')}</div></div>
  <div class="fg"><span class="lb">How strong is it</span><div class="chips">${[1,2,3,4,5].map(n => `<button type="button" class="chip${NT.str === n ? ' on' : ''}" data-ta="nstr" data-v="${n}">${n}</button>`).join('')}<span class="muted" style="align-self:center;font-size:12.5px">1 barely, 5 loud</span></div></div>
  <div class="fg"><label for="nt-text">Your words</label><input id="nt-text" placeholder="e.g. the vanilla just came out" value="${esc(NT.text)}" autocomplete="off"></div>
  <div class="fg"><label for="nt-t">Time</label><input type="datetime-local" id="nt-t" value="${toLocalInput(NT.t)}" min="${toLocalInput(s.t0)}"></div>
  <div class="foot"><button class="btn ghost" data-ta="dback" data-id="${s.id}">Back</button><button class="btn" data-ta="nsave">Save impression</button></div>`;
}
function openNote(sid){ NT = { sid, tags:[], str:null, text:'', t:Date.now() }; MODAL = 'tt'; openModal(noteHtml()); }
function paintNT(){ const t = $('#nt-text'), tm = $('#nt-t'); if (t) NT.text = t.value; if (tm && tm.value) NT.t = fromLocalInput(tm.value); const p = $('#modal .panel'); if (p) { const sc = p.scrollTop; p.innerHTML = noteHtml(); p.scrollTop = sc; } }

/* ---------- fade sheet ---------- */
function openFade(sid){
  const s = tt().sessions.find(x => x.id === sid);
  MODAL = 'tt';
  openModal(`<button class="x" data-act="close" aria-label="Close">\u00d7</button><h2>When did it fade?</h2><p class="muted" style="margin:4px 0 16px">${esc(s.name)}. The moment you could no longer smell it on your skin. This sets the longevity.</p>
  <div class="fg"><input type="datetime-local" id="fd-t" value="${toLocalInput(Date.now())}" min="${toLocalInput(s.t0)}"></div>
  <div class="foot"><button class="btn ghost" data-ta="dback" data-id="${s.id}">Back</button><button class="btn" data-ta="fsave" data-id="${s.id}">Save</button></div>`);
}

/* ---------- rating sheet ---------- */
let RT = null;
function rangeRow(k, label, v, min){
  return `<div class="fg"><label>${label}</label><div class="tt-rng"><input type="range" min="${min}" max="10" step="0.5" value="${v}" data-ti="rng" data-k="${k}"><b id="rv-${k}">${v > 0 ? v : '\u2014'}</b></div></div>`;
}
function rateHtml(){
  const s = tt().sessions.find(x => x.id === RT.sid), hrs = s.fadedAt ? (s.fadedAt - s.t0)/H : null;
  return `<button class="x" data-act="close" aria-label="Close">\u00d7</button><h2>Rate this test</h2><p class="muted" style="margin:4px 0 16px">${esc(s.name)} \u00b7 ${esc(s.brand)}${hrs != null ? ' \u00b7 lasted '+relStr(hrs*H) : ''}</p>
  ${rangeRow('longevity', 'Longevity'+(hrs != null && !s.rating ? ' (suggested from the time it faded)' : ''), RT.v.longevity, 1)}${rangeRow('sillage', 'Sillage and aura', RT.v.sillage, 1)}${rangeRow('skin', 'Scent on skin', RT.v.skin, 1)}${rangeRow('value', 'Value for the price (optional, 0 to skip)', RT.v.value, 0)}
  <div class="fg"><span class="lb">Would you buy it</span><div class="chips">${[['yes','Yes'],['maybe','Maybe'],['no','No']].map(([k, l]) => `<button type="button" class="chip${RT.buy === k ? ' on' : ''}" data-ta="rbuy" data-v="${k}">${l}</button>`).join('')}</div></div>
  <div class="fg"><label for="rt-c">Short review</label><textarea id="rt-c" placeholder="Opens sharp citrus, turns into creamy vanilla musk after two hours">${esc(RT.comment)}</textarea></div>
  <div class="foot"><button class="btn ghost" data-ta="dback" data-id="${s.id}">Back</button><button class="btn" data-ta="rsave">Save rating</button></div>`;
}
function openRate(sid){
  const s = tt().sessions.find(x => x.id === sid), hrs = s.fadedAt ? (s.fadedAt - s.t0)/H : null;
  const r = s.rating || {};
  RT = { sid, buy:s.wouldBuy, comment:s.comment || '', v:{ longevity:r.longevity || (hrs != null ? clamp(Math.round(hrs*2)/2, 1, 10) : 5), sillage:r.sillage || 5, skin:r.skin || 5, value:r.value || 0 } };
  MODAL = 'tt'; openModal(rateHtml());
}

/* ---------- session detail ---------- */
function timelineSvg(s){
  const notes = (s.notes || []), dur = Math.max(H/2, elapsed(s), ...notes.map(n => n.t - s.t0));
  const W = 320, Hh = 96, pl = 10, pr = 10, top = 10, base = 74;
  const X = ms => pl + ms/dur*(W - pl - pr);
  let g = '<line x1="'+pl+'" y1="'+base+'" x2="'+(W-pr)+'" y2="'+base+'" stroke="var(--line2)" stroke-width="2" stroke-linecap="round"/>';
  const sp = notes.filter(n => n.strength).map(n => [X(n.t - s.t0), base - 6 - (n.strength - 1)/4*(base - top - 10)]);
  if (sp.length > 1) g += '<path d="'+sp.map((p, i) => (i ? 'L' : 'M')+p[0].toFixed(1)+' '+p[1].toFixed(1)).join('')+'" fill="none" stroke="var(--gold)" stroke-width="2" stroke-linejoin="round" stroke-linecap="round" opacity=".7"/>';
  notes.forEach(n => { const x = X(n.t - s.t0), y = n.strength ? base - 6 - (n.strength - 1)/4*(base - top - 10) : base; g += '<circle cx="'+x.toFixed(1)+'" cy="'+y.toFixed(1)+'" r="4.5" fill="var(--gold)" stroke="var(--surf)" stroke-width="2"/>'; });
  if (s.fadedAt) { const x = X(s.fadedAt - s.t0); g += '<line x1="'+x.toFixed(1)+'" y1="'+top+'" x2="'+x.toFixed(1)+'" y2="'+base+'" stroke="var(--danger)" stroke-dasharray="3 3"/><text x="'+x.toFixed(1)+'" y="'+(top + 2)+'" text-anchor="end" font-size="10" fill="var(--danger)" font-family="var(--sans)">faded</text>'; }
  g += '<text x="'+pl+'" y="92" font-size="10" fill="var(--tx3)" font-family="var(--sans)">spray</text><text x="'+(W-pr)+'" y="92" text-anchor="end" font-size="10" fill="var(--tx3)" font-family="var(--sans)">+'+relStr(dur)+'</text>';
  return '<svg class="tt-tl" viewBox="0 0 '+W+' '+Hh+'">'+g+'</svg>';
}
function scrubText(s, v){
  const cur = (s.notes || []).filter(n => n.t - s.t0 <= v).slice(-1)[0];
  const head = '<b style="color:var(--gold2)">+'+relStr(v)+'</b> \u00b7 ';
  if (!cur) return head+'<span class="muted">before your first impression</span>';
  return head+esc((cur.tags || []).join(', ') || cur.text || 'note')+(cur.strength ? ' \u00b7 strength '+cur.strength+'/5' : '')+(cur.text && (cur.tags || []).length ? '<br><span class="muted">'+esc(cur.text)+'</span>' : '');
}
function detailHtml(s){
  const notes = (s.notes || []).slice().sort((a, b) => a.t - b.t), dur = Math.max(H/2, elapsed(s), ...notes.map(n => n.t - s.t0)), sc = score(s), sp = spotsLabel(s);
  const rows = notes.length ? notes.map(n => `<div class="tt-note"><div class="rt">+${relStr(n.t - s.t0)}</div><div class="bd">${esc((n.tags || []).join(', ') || n.text || 'Note')}${n.strength ? ' <small>strength '+n.strength+'/5</small>' : ''}${n.text && (n.tags || []).length ? '<small>'+esc(n.text)+'</small>' : ''}<small>${clock(n.t)}</small></div><button data-ta="ndel" data-id="${s.id}" data-n="${n.id}" aria-label="Delete impression">\u00d7</button></div>`).join('') : '<p class="muted" style="padding:8px 0">No impressions yet.</p>';
  return `<button class="x" data-act="close" aria-label="Close">\u00d7</button><h2>${esc(s.name)}</h2><div class="muted" style="margin-bottom:12px">${esc(s.brand)}</div>
  <div class="tt-kv"><span>Sprayed</span><span>${dshort(s.t0)}, ${clock(s.t0)}</span></div>
  <div class="tt-kv"><span>Sprays</span><span>${s.sprays}${sp ? ' \u00b7 '+esc(sp) : ''}</span></div>
  ${s.venue ? `<div class="tt-kv"><span>Where</span><span>${esc(s.venue)}</span></div>` : ''}${s.price != null ? `<div class="tt-kv"><span>Price</span><span>${esc(cur())}${fmt(s.price)}</span></div>` : ''}
  <div class="tt-kv"><span>Weather</span><span>${s.weather ? esc(wxLine(s.weather)) : 'not set'} <button data-ta="wxedit" data-id="${s.id}" style="color:var(--gold2);margin-left:6px">edit</button></span></div>
  ${s.fadedAt ? `<div class="tt-kv"><span>Faded after</span><span>${relStr(s.fadedAt - s.t0)}</span></div>` : ''}
  ${sc != null ? `<div class="tt-kv"><span>Score</span><span><b style="color:var(--gold2);font:500 22px var(--serif)">${r1(sc)}</b> \u00b7 ${CRIT.map(([k, l]) => l+' '+s.rating[k]).join(' \u00b7 ')}${s.rating.value ? ' \u00b7 Value '+s.rating.value : ''}</span></div>` : ''}
  ${s.comment ? `<p class="sub2" style="font-size:14px;font-style:italic">\u201c${esc(s.comment)}\u201d</p>` : ''}
  <h3 style="margin:22px 0 4px">Timeline</h3>${timelineSvg(s)}
  ${notes.length ? `<input type="range" min="0" max="${dur}" step="60000" value="${dur}" data-ti="scrub" data-id="${s.id}" aria-label="Scrub through the test"><div class="tt-scr" id="tt-scr">${scrubText(s, dur)}</div>` : ''}
  <div style="margin-top:10px">${rows}</div>
  <div class="foot"><button class="btn danger sp" data-ta="sdel" data-id="${s.id}">Delete</button><button class="btn ghost" data-ta="note" data-id="${s.id}">Add impression</button>${s.fadedAt ? '' : `<button class="btn ghost" data-ta="fade" data-id="${s.id}">Faded out</button>`}<button class="btn" data-ta="rate" data-id="${s.id}">${s.rating ? 'Edit rating' : 'Rate'}</button></div>`;
}
function openDetail(sid){ const s = tt().sessions.find(x => x.id === sid); if (!s) return; MODAL = 'tt'; openModal(detailHtml(s)); }

function wxEditHtml(s){
  const w = s.weather || {};
  return `<button class="x" data-act="close" aria-label="Close">\u00d7</button><h2>Weather</h2><p class="muted" style="margin:4px 0 16px">${esc(s.name)}</p>
  <div class="g2"><div class="fg"><label for="wx-t">Temperature (\u00b0C)</label><input id="wx-t" type="number" step="1" value="${w.temp != null ? w.temp : ''}"></div><div class="fg"><label for="wx-h">Humidity (%)</label><input id="wx-h" type="number" min="0" max="100" value="${w.hum != null ? w.hum : ''}"></div></div>
  ${tt().city ? `<button class="btn ghost" data-ta="wxfetch" data-id="${s.id}" style="margin-bottom:10px">Fetch for ${esc(tt().city.name || tt().city.label)}</button>` : ''}
  <div class="foot"><button class="btn ghost" data-ta="dback" data-id="${s.id}">Back</button><button class="btn" data-ta="wxsave" data-id="${s.id}">Save</button></div>`;
}

/* ---------- perfume (group) detail ---------- */
function groupHtml(g){
  const ax = CRIT.map(([k, l]) => ({ l, v:g.crit[k] })).concat(g.crit.value ? [{ l:'Value', v:g.crit.value }] : []).filter(a => a.v != null);
  const cond = condStats(g), tags = tagCounts(g).slice(0, 10), inCab = g.pid && byId(g.pid);
  const onWish = S.perfumes.some(p => p.shelf === 'wish' && norm(p.brand+' '+p.name) === norm(g.brand+' '+g.name));
  const rows = g.sessions.slice().sort((a, b) => b.t0 - a.t0).map(s => `<div class="tt-g" data-ta="open" data-id="${s.id}" role="button" tabindex="0" style="padding:11px 4px"><div class="mid"><div class="t1" style="font-size:16px">${dshort(s.t0)}</div><div class="t2">${[s.venue, s.weather ? s.weather.temp+'\u00b0' : '', spotsLabel(s), isDone(s) ? '' : 'not rated'].filter(Boolean).map(esc).join(' \u00b7 ')}</div></div><div class="tt-score" style="font-size:24px">${isDone(s) ? r1(score(s)) : '\u2014'}</div></div>`).join('');
  return `<button class="x" data-act="close" aria-label="Close">\u00d7</button><h2>${esc(g.name)}</h2><div class="muted">${esc(g.brand)}</div>
  <div class="tt-score" style="text-align:left;font-size:46px;margin:14px 0 2px">${r1(g.avg)}<small style="display:inline;margin-left:8px">average over ${g.done.length} ${g.done.length === 1 ? 'test' : 'tests'}</small></div>
  ${radar(ax)}
  ${g.hours != null ? `<div class="tt-kv"><span>Average longevity</span><span>${r1(g.hours)} h</span></div>` : ''}${g.price != null ? `<div class="tt-kv"><span>Last price</span><span>${esc(cur())}${fmt(g.price)}</span></div>` : ''}
  ${cond.length ? `<div class="tt-kv"><span>By temperature</span><span>${esc(cond.join(' \u00b7 '))}</span></div>` : ''}
  ${tags.length ? `<div style="margin:16px 0 4px"><span class="lb">What you smelled most</span><div class="chips">${tags.map(([t, c]) => `<span class="chip">${esc(t)} \u00b7 ${c}</span>`).join('')}</div></div>` : ''}
  <h3 style="margin:22px 0 2px">Tests</h3>${rows}
  <div class="foot">${inCab ? '<button class="btn ghost" data-ta="gsync" data-k="'+esc(g.key)+'">Update cabinet ratings</button>' : (onWish ? '' : '<button class="btn ghost" data-ta="gwish" data-k="'+esc(g.key)+'">Add to wishlist</button>')}<button class="btn" data-ta="again" data-k="${esc(g.key)}">Test again</button></div>`;
}
const findGroup = k => groups().find(g => g.key === k);

/* ---------- actions ---------- */
const TA = {
  start: () => openStart(),
  open: a => openDetail(a.dataset.id),
  dback: a => openDetail(a.dataset.id),
  group: a => { const g = findGroup(a.dataset.k); if (g) { MODAL = 'tt'; openModal(groupHtml(g)); } },
  again: a => { const g = findGroup(a.dataset.k); if (!g) return; closeAll(); openStart({ pid:g.pid, name:g.name, brand:g.brand, fam:g.fam }); },
  gwish: a => {
    const g = findGroup(a.dataset.k); if (!g) return;
    const e = LIB.find(x => x.k === norm(g.brand+' '+g.name));
    S.perfumes.push({ id:uid(), name:g.name, brand:g.brand, fam:g.fam || (e && e.f) || '', conc:(e && e.c) || 'EDP', year:(e && e.y) || '', shelf:'wish', type:'Bottle', maxMl:(e && e.s) || 100, ml:(e && e.s) || 100, price:'', rating:0, longevity:'', proj:0, seasons:[], notes:'', created:Date.now(), sprays:0 });
    save(); toast('Added to your wishlist'); const g2 = findGroup(g.key); if (g2) $('#modal .panel').innerHTML = groupHtml(g2);
  },
  gsync: a => {
    const g = findGroup(a.dataset.k), p = g && g.pid && byId(g.pid); if (!p) return;
    p.rating = clamp(Math.round(g.avg/2), 1, 5); if (g.crit.sillage) p.proj = clamp(Math.round(g.crit.sillage/2), 1, 5); if (g.hours != null) p.longevity = r1(g.hours);
    save(); toast('Cabinet ratings updated from your tests');
  },
  /* start sheet */
  tclear: () => { readST(); ST.name = ''; ST.brand = ''; ST.pid = null; ST.fam = ''; paintST(); },
  tspot: a => { readST(); const v = a.dataset.v; ST.spots = ST.spots.includes(v) ? ST.spots.filter(x => x !== v) : ST.spots.concat(v); paintST(); },
  tstep: a => { ST.n = clamp(ST.n + (+a.dataset.d), 1, 20); $('#ts-n').textContent = ST.n; },
  tpickc: a => { readST(); const p = byId(a.dataset.id); if (!p) return; ST.pid = p.id; ST.name = p.name; ST.brand = p.brand; ST.fam = p.fam; paintST(); },
  tpickl: a => { readST(); const e = LIB[+a.dataset.i]; if (!e) return; const own = S.perfumes.find(p => norm(p.brand+' '+p.name) === e.k); ST.pid = own ? own.id : null; ST.name = e.n; ST.brand = e.b; ST.fam = e.f; paintST(); },
  tpickf: () => { readST(); ST.pid = null; ST.name = ST.q; ST.brand = ''; ST.fam = ''; paintST(); },
  tcity: () => { readST(); ST.showCity = true; paintST(); },
  tfind: async () => {
    const q = ($('#ts-city') || {}).value; if (!q || !q.trim()) return;
    readST(); ST.cmsg = 'Searching\u2026'; $('#ts-cres').innerHTML = cityRes();
    const r = await geocode(q.trim());
    if (!ST || MODAL !== 'tt') return;
    ST.cres = r || []; ST.cmsg = r === null ? 'Could not reach the weather service. Check your connection.' : (r.length ? '' : 'No city found with that name.');
    const box = $('#ts-cres'); if (box) box.innerHTML = cityRes();
  },
  tgeo: () => {
    if (!navigator.geolocation) { toast('Location is not available here'); return; }
    navigator.geolocation.getCurrentPosition(pos => { readST(); ST.city = { name:'My location', label:'My location', lat:pos.coords.latitude, lon:pos.coords.longitude }; ST.showCity = false; paintST(); }, () => toast('Location was blocked. Type your city instead.'), { timeout:8000 });
  },
  tpickcity: a => { readST(); const r = (ST.cres || [])[+a.dataset.i]; if (!r) return; ST.city = { name:r.name, label:r.label, lat:r.lat, lon:r.lon }; ST.showCity = false; ST.cres = null; paintST(); },
  tgo: () => startSession(),
  /* impressions */
  qnote: a => {
    const s = tt().sessions.find(x => x.id === a.dataset.id); if (!s) return;
    (s.notes = s.notes || []).push({ id:uid(), t:Date.now(), tags:[a.dataset.tag], strength:null, text:'' }); save(); render(); toast(a.dataset.tag+' at +'+relStr(Date.now() - s.t0));
  },
  note: a => openNote(a.dataset.id),
  ntag: a => { const v = a.dataset.v; const t = $('#nt-text'); if (t) NT.text = t.value; NT.tags = NT.tags.includes(v) ? NT.tags.filter(x => x !== v) : NT.tags.concat(v); paintNT(); },
  nstr: a => { const t = $('#nt-text'); if (t) NT.text = t.value; NT.str = NT.str === +a.dataset.v ? null : +a.dataset.v; paintNT(); },
  nsave: () => {
    const s = tt().sessions.find(x => x.id === NT.sid); if (!s) return;
    const text = ($('#nt-text').value || '').trim(), tm = $('#nt-t').value ? fromLocalInput($('#nt-t').value) : Date.now();
    if (!NT.tags.length && !text) { toast('Pick what you smell or write a few words'); return; }
    (s.notes = s.notes || []).push({ id:uid(), t:Math.max(tm, s.t0), tags:NT.tags.slice(), strength:NT.str, text }); s.notes.sort((x, y) => x.t - y.t);
    save(); render(); openDetail(s.id);
  },
  ndel: a => { const s = tt().sessions.find(x => x.id === a.dataset.id); if (!s) return; s.notes = (s.notes || []).filter(n => n.id !== a.dataset.n); save(); render(); openDetail(s.id); },
  /* fade + rating */
  fade: a => openFade(a.dataset.id),
  fsave: a => { const s = tt().sessions.find(x => x.id === a.dataset.id); if (!s) return; s.fadedAt = Math.max(s.t0, fromLocalInput($('#fd-t').value)); save(); render(); openRate(s.id); },
  rate: a => openRate(a.dataset.id),
  rbuy: a => { const c = $('#rt-c'); if (c) RT.comment = c.value; RT.buy = RT.buy === a.dataset.v ? null : a.dataset.v; const p = $('#modal .panel'); const sc = p.scrollTop; p.innerHTML = rateHtml(); p.scrollTop = sc; },
  rsave: () => {
    const s = tt().sessions.find(x => x.id === RT.sid); if (!s) return;
    s.rating = { longevity:RT.v.longevity, sillage:RT.v.sillage, skin:RT.v.skin, value:RT.v.value }; s.wouldBuy = RT.buy; s.comment = ($('#rt-c').value || '').trim(); s.status = 'done'; s.ratedAt = Date.now();
    save(); render(); toast('Rated '+r1(score(s))+' \u00b7 '+s.name); openDetail(s.id);
  },
  sdel: a => { const id = a.dataset.id; askConfirm('Delete this test?', 'Its impressions and rating are removed. The fragrance average updates.', 'Delete', true).then(ok => { if (!ok) return; const d = tt(); d.sessions = d.sessions.filter(x => x.id !== id); save(); closeAll(); render(); toast('Test deleted'); }); },
  /* weather edit */
  wxedit: a => { const s = tt().sessions.find(x => x.id === a.dataset.id); if (s) { MODAL = 'tt'; openModal(wxEditHtml(s)); } },
  wxsave: a => {
    const s = tt().sessions.find(x => x.id === a.dataset.id); if (!s) return;
    const t = $('#wx-t').value, h = $('#wx-h').value;
    s.weather = Object.assign({}, s.weather || {}, { temp:t === '' ? null : Math.round(+t), hum:h === '' ? null : Math.round(+h), city:(s.weather && s.weather.city) || 'Manual' });
    save(); render(); openDetail(s.id);
  },
  wxfetch: async a => {
    const s = tt().sessions.find(x => x.id === a.dataset.id); if (!s) return; toast('Fetching weather\u2026');
    const w = await getWeather(tt().city, s.t0); if (!w) { toast('Weather not available right now'); return; }
    s.weather = w; save(); render(); openDetail(s.id);
  }
};
document.addEventListener('click', e => { const a = e.target.closest('[data-ta]'); if (a && TA[a.dataset.ta]) { e.preventDefault(); TA[a.dataset.ta](a, e); } });
document.addEventListener('keydown', e => { if ((e.key === 'Enter' || e.key === ' ') && e.target.dataset && e.target.dataset.ta && e.target.getAttribute('role') === 'button') { e.preventDefault(); TA[e.target.dataset.ta](e.target, e); } });

const TI = {
  tq: el => { TQ = el.value; const box = $('#ttList'); if (box) box.innerHTML = listHtml(); },
  tsq: el => {
    const q = el.value.trim(); ST.q = q; const box = $('#ts-sugg'); if (!box) return;
    if (q.length < 2) { box.innerHTML = ''; return; }
    const w = norm(q).split(' ').filter(Boolean);
    const cab = S.perfumes.filter(p => { const k = norm(p.brand+' '+p.name); return w.every(x => k.includes(x)); }).slice(0, 4);
    const lib = libFind(q).slice(0, 6);
    box.innerHTML = cab.map(p => `<button type="button" data-ta="tpickc" data-id="${p.id}">${esc(p.name)} <small>${esc(p.brand)} \u00b7 ${p.shelf === 'wish' ? 'on your wishlist' : 'in cabinet'}</small></button>`).join('') +
      lib.map(i => `<button type="button" data-ta="tpickl" data-i="${i}">${esc(LIB[i].n)} <small>${esc(LIB[i].b)}</small></button>`).join('') +
      `<button type="button" data-ta="tpickf">Use \u201c${esc(q)}\u201d as typed</button>`;
  },
  rng: el => { const k = el.dataset.k; RT.v[k] = +el.value; const o = $('#rv-'+k); if (o) o.textContent = +el.value > 0 ? +el.value : '\u2014'; },
  scrub: el => { const s = tt().sessions.find(x => x.id === el.dataset.id), o = $('#tt-scr'); if (s && o) o.innerHTML = scrubText(s, +el.value); }
};
document.addEventListener('input', e => { const k = e.target.dataset && e.target.dataset.ti; if (k && TI[k]) TI[k](e.target, e); });
const TC = {
  tsort: el => { TSORT = el.value; const box = $('#ttList'); if (box) box.innerHTML = listHtml(); },
  tearly: el => { readST(); ST.earlier = el.checked; if (el.checked) ST.t = Date.now() - H; paintST(); }
};
document.addEventListener('change', e => { const k = e.target.dataset && e.target.dataset.tc; if (k && TC[k]) TC[k](e.target, e); });

window.__luxTest = { groups, score, condStats, tagCounts, relStr, wxLine, radar, keyOf, openStart };
afterRender();
})();
