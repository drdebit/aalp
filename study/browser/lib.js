const { chromium } = require('playwright');
const BASE = 'http://choochoo.dyn.gsu.edu:8081/';
async function launch() {
  const b = await chromium.launch();
  const ctx = await b.newContext({ viewport: { width: 1400, height: 1000 } });
  const p = await ctx.newPage();
  p.on('pageerror', e => console.log('PAGEERROR', e.message, String(e.stack||'').split('\n').slice(0,6).join(' ~ ')));
  p.on('console', m => { if (m.type() === 'error') console.log('CONSOLE', m.text().slice(0, 200)); });
  return { b, p };
}
async function login(p, email) {
  await p.goto(BASE, { waitUntil: 'networkidle', timeout: 60000 });
  const inp = p.locator('input[type=email], input[type=text]').first();
  await inp.fill(email);
  await p.getByRole('button',{name:/sign in/i}).click();
  await p.waitForTimeout(2500);
}
async function dump(p, label) {
  const info = await p.evaluate(() => {
    const vis = el => { const r = el.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const btns = [...document.querySelectorAll('button')].filter(vis).map(b => (b.innerText || '').trim().replace(/\s+/g, ' ').slice(0, 60));
    const sels = [...document.querySelectorAll('select')].filter(vis).map(s => ({ cls: s.className, opts: [...s.options].map(o => o.value).slice(0, 8) }));
    const inputs = [...document.querySelectorAll('input')].filter(vis).map(i => ({ type: i.type, ph: i.placeholder, cls: i.className, val: i.value }));
    const heads = [...document.querySelectorAll('h1,h2,h3,h4,h5')].filter(vis).map(h => h.tagName + ': ' + (h.innerText || '').trim().slice(0, 70));
    return { btns, sels, inputs, heads };
  });
  console.log(`--- ${label} ---`);
  console.log('HEADS', JSON.stringify(info.heads));
  console.log('BUTTONS', JSON.stringify(info.btns));
  console.log('SELECTS', JSON.stringify(info.sels));
  console.log('INPUTS', JSON.stringify(info.inputs));
}
async function shot(p, name) { await p.screenshot({ path: `shots/${name}.png`, fullPage: true }); }
async function text(p, sel) { try { return (await p.locator(sel).first().innerText({ timeout: 2000 })).trim(); } catch (e) { return null; } }
module.exports = { launch, login, dump, shot, text, BASE };
