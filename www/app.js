const $ = (s) => document.querySelector(s);
window.addEventListener('error', (e) => { const m = $('#msg'); if (m) m.textContent = 'Script error: ' + e.message; });
window.addEventListener('unhandledrejection', (e) => { const m = $('#msg'); if (m) m.textContent = 'Error: ' + ((e.reason && e.reason.message) || e.reason); });
// No bundler here, so call the native bridge directly instead of Capacitor.registerPlugin.
const LR = new Proxy({}, {
  get: (_, method) => (opts) => {
    const C = window.Capacitor;
    if (!C) return Promise.reject(new Error('Capacitor bridge not found'));
    if (typeof C.nativePromise === 'function') return C.nativePromise('LinkRouter', method, opts || {});
    if (typeof C.registerPlugin === 'function') return C.registerPlugin('LinkRouter')[method](opts || {});
    return Promise.reject(new Error('Capacitor bridge has no nativePromise'));
  },
});
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
let cfg = { apps: [] }, installedApps = [], status = null, saveTimer;

function toast(msg) {
  const t = $('#toast'); t.textContent = msg; t.hidden = false;
  clearTimeout(toast.h); toast.h = setTimeout(() => (t.hidden = true), 2800);
}
const run = async (fn) => { try { await fn(); } catch (e) { toast(e.message || String(e)); } setTimeout(refresh, 500); };

function save() {
  clearTimeout(saveTimer);
  saveTimer = setTimeout(() => {
    cfg.apps.forEach((a) => (a.pkg = (a.pkg || '').trim()));
    LR.saveConfig({ config: cfg }).catch((e) => toast(e.message));
  }, 300);
}
const saveNow = async () => { clearTimeout(saveTimer); cfg.apps.forEach((a) => (a.pkg = (a.pkg || '').trim())); await LR.saveConfig({ config: cfg }); };

function renderApps() {
  const box = $('#apps'); box.innerHTML = '';
  cfg.apps.forEach((a, i) => {
    const d = document.createElement('div'); d.className = 'app';
    d.innerHTML = `
      <select aria-label="Choose an installed app">
        <option value="">Choose an installed app...</option>
        ${installedApps.map((x) => `<option value="${esc(x.pkg)}">${esc(x.label)}</option>`).join('')}
      </select>
      <input type="text" data-k="label" value="${esc(a.label)}" placeholder="Name shown in popup" aria-label="Name">
      <input type="text" data-k="pkg" value="${esc(a.pkg)}" placeholder="Package name, e.g. com.example.app" spellcheck="false" autocapitalize="off" aria-label="Package name">
      <div class="foot"><b class="inst" data-i="${i}"></b><button class="small ghost" data-del>Remove</button></div>`;
    d.querySelectorAll('input[data-k]').forEach((el) => el.addEventListener('input', () => { a[el.dataset.k] = el.value; save(); updateInstalled(); }));
    d.querySelector('select').onchange = (e) => {
      const x = installedApps.find((z) => z.pkg === e.target.value);
      if (!x) return;
      a.pkg = x.pkg; a.label = x.label; save(); renderApps(); updateInstalled();
    };
    d.querySelector('[data-del]').onclick = () => { cfg.apps.splice(i, 1); save(); renderApps(); };
    box.appendChild(d);
  });
  updateInstalled();
}

function updateInstalled() {
  document.querySelectorAll('.inst').forEach((el) => {
    const a = cfg.apps[+el.dataset.i];
    const inst = status && status.installed && a ? status.installed[(a.pkg || '').trim()] : undefined;
    el.textContent = !a || !a.pkg ? '' : inst === undefined ? 'Save to check' : inst ? 'Installed' : 'Not installed';
    el.className = 'inst ' + (inst ? 'ok' : '');
  });
}

function renderStatus() {
  if (!status) return;
  $('#svc').checked = status.serviceRunning;
  $('#msg').textContent = status.message || '';
  const todo = [];
  if (!status.overlay) todo.push(['Allow "Display over other apps"', 'Allow', () => LR.openOverlaySettings()]);
  if (!status.notifications) todo.push(['Allow notifications', 'Allow', () => LR.requestNotifications()]);
  if (!status.shizukuRunning) todo.push(['Start Shizuku', 'Open Shizuku', () => LR.openShizuku()]);
  else if (!status.shizukuGranted) todo.push(['Let Link Router use Shizuku', 'Authorize', () => LR.requestShizuku()]);
  const ul = $('#todo'); ul.innerHTML = '';
  todo.forEach(([t, b, fn]) => {
    const li = document.createElement('li');
    li.innerHTML = `<span>${esc(t)}</span>`;
    const btn = document.createElement('button'); btn.className = 'small'; btn.textContent = b;
    btn.onclick = () => run(fn); li.appendChild(btn); ul.appendChild(li);
  });
  $('#setup').hidden = todo.length === 0;
  updateInstalled();
}

async function refresh() {
  try { status = await LR.getStatus(); renderStatus(); }
  catch (e) { $('#msg').textContent = 'Error: ' + (e.message || e); }
}

$('#addApp').onclick = () => { cfg.apps.push({ label: '', pkg: '' }); save(); renderApps(); };
$('#test').onclick = () => run(async () => { await saveNow(); await LR.testPopup(); });
$('#svc').onchange = (e) => run(async () => {
  await saveNow();
  if (e.target.checked) await LR.startService(); else await LR.stopService();
});

document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
setInterval(() => { if (!document.hidden) refresh(); }, 2000);

(async () => {
  $('#svc').checked = false;
  try { cfg = await LR.getConfig(); } catch (e) { $('#msg').textContent = 'Error: ' + (e.message || e); }
  renderApps();
  refresh();
  try { installedApps = (await LR.listApps()).apps || []; renderApps(); } catch (e) { /* picker is optional */ }
})();
