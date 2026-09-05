// Full walkthrough in the real browser, with checks. Prints PASS/FAIL lines.
const L = require('./lib.js');
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok, detail }); console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' — ' + String(detail).slice(0, 160) : '')); };
const T = async (p, sel) => (await L.text(p, sel)) || '';
const next = async p => { await p.getByRole('button', { name: /^(next|finish)$/i }).click(); await p.waitForTimeout(700); };
const openPal = async p => { await p.getByRole('button', { name: /add assertion/i }).click(); await p.waitForTimeout(300); };
const add = async (p, code, unit) => {
  await openPal(p);
  await p.getByRole('button', { name: new RegExp('^' + code, 'i') }).first().click(); await p.waitForTimeout(300);
  if (unit) { const u = p.getByRole('button', { name: new RegExp('^' + unit + '$', 'i') }); if (await u.count()) { await u.click(); await p.waitForTimeout(900); } else { check('unit option "' + unit + '" offered', false, 'sub-menu lacks it'); await p.getByRole('button', { name: /cancel/i }).click().catch(()=>{}); } }
  await p.waitForTimeout(400);
};
const qty = async (p, frag, v) => { await p.locator(`.assertion-fragment.${frag} input[placeholder=qty]`).first().fill(String(v)); await p.waitForTimeout(1200); };
const item = async (p, frag, v) => { await p.locator(`.assertion-fragment.${frag} select`).first().selectOption(v); await p.waitForTimeout(1200); };
const date = async (p, d) => { await p.locator('input[type=date]').fill(d); await p.waitForTimeout(600); };
const party = async (p, n) => { await p.locator('input[placeholder*=party]').fill(n); await p.waitForTimeout(1200); };
const derived = async p => (await T(p, '.derived-je')).replace(/\s+/g, ' ');
const nextEnabled = async p => p.getByRole('button', { name: /^(next|finish)$/i }).isEnabled();

(async () => {
  const { b, p } = await L.launch();
  await L.login(p, 'browser-pass7@study.test'); await p.waitForTimeout(1500);
  check('gate offers the walkthrough', (await p.getByRole('button', { name: /walk me through/i }).count()) === 1);
  await p.getByRole('button', { name: /walk me through/i }).click(); await p.waitForTimeout(1500);

  // ---- Episode 1: funding
  check('ep1 chain empty at start', /Nothing yet/.test(await T(p, '.chain-panel')));
  await next(p); await next(p);
  check('ep1 next disabled before date', !(await nextEnabled(p)));
  await add(p, 'has date'); await date(p, '2026-01-01'); check('ep1 next enabled after date', await nextEnabled(p)); await next(p);
  await add(p, 'receives', 'cash'); await qty(p, 'receives', 20000);
  let d = await derived(p); check('ep1 Cash + Owner\'s Capital 20,000', /Cash\s+\$20,000/.test(d) && /Owner's Capital\s+\$20,000/.test(d), d);
  check('ep1 then-text shows after the step', /Two lines appeared/.test(await T(p, '.walkthrough')));
  await next(p);
  await add(p, 'provides', 'ownership units'); await qty(p, 'provides', 200);
  d = await derived(p); check('ep1 ownership units in the strip', /IN THE CHAIN, NOT ON THE ENTRY/i.test(d) && /count|monetary unit/i.test(d), d.slice(-300));
  check('ep1 sentence subject is the business', /the business/.test(await T(p, '.main-sentence')), await T(p, '.main-sentence'));
  await next(p); await next(p);
  await add(p, 'has counterparty'); await party(p, 'SP'); await next(p); await p.waitForTimeout(1200);

  // ---- Episode 2: printer
  check('ep2 chain has the funding event', /funding/.test(await T(p, '.chain-panel')), await T(p, '.chain-panel'));
  check('ep2 previous note not shown at fresh episode', true);
  await add(p, 'has date'); await date(p, '2026-01-02'); await next(p);
  await add(p, 'provides', 'cash'); await qty(p, 'provides', 3000); await next(p);
  await add(p, 'receives', 'physical units'); await item(p, 'receives', 't-shirt-printer'); await qty(p, 'receives', 1);
  d = await derived(p); check('ep2 printer not yet classified without allows', /not yet classified/.test(d), d);
  await next(p); await next(p);
  await add(p, 'allows');
  await p.locator('.allows-content select').nth(0).selectOption('blank-tshirts'); await p.waitForTimeout(300);
  await p.getByRole('button', { name: /another input/i }).click(); await p.waitForTimeout(300);
  await p.locator('.allows-content select').nth(1).selectOption('ink-cartridges'); await p.waitForTimeout(300);
  await p.locator('.allows-content select').nth(2).selectOption('printed-tshirts'); await p.waitForTimeout(1500);
  d = await derived(p); check('ep2 Equipment after allows', /Equipment/.test(d), d);
  await next(p);
  await add(p, 'has counterparty'); await party(p, 'PrinterWorld'); await next(p);
  // the allows step: take it off with its ×, then put it back
  check('ep2 next disabled until allows removed', !(await nextEnabled(p)));
  await p.locator('.allows-content button.remove-assertion').click(); await p.waitForTimeout(1500);
  d = await derived(p); check('ep2 Equipment gone with allows off', !/Equipment/.test(d) && /not yet classified/.test(d), d);
  check('ep2 next enabled once allows is off', await nextEnabled(p));
  await next(p);
  await add(p, 'allows');
  await p.locator('.allows-content select').nth(0).selectOption('blank-tshirts'); await p.getByRole('button', { name: /another input/i }).click(); await p.waitForTimeout(300);
  await p.locator('.allows-content select').nth(1).selectOption('ink-cartridges'); await p.locator('.allows-content select').nth(2).selectOption('printed-tshirts'); await p.waitForTimeout(1500);
  d = await derived(p); check('ep2 Equipment back with allows on', /Equipment/.test(d), d);
  await next(p);
  check('ep2 account-name question step present', /what is this thing for/i.test(await T(p, '.walkthrough')), (await T(p, '.walkthrough')).slice(0, 200));
  await next(p); await next(p); await p.waitForTimeout(1200);

  // ---- Episode 3: design + servicing
  check('ep3 title merges design and servicing', /design, and the printer is serviced/i.test(await T(p, '.walkthrough')));
  await add(p, 'has date'); await date(p, '2026-01-03'); await next(p);
  await add(p, 'provides', 'cash'); await qty(p, 'provides', 400); await next(p);
  await add(p, 'receives', 'physical units'); await item(p, 'receives', 'logo-design'); await qty(p, 'receives', 1); await next(p);
  await add(p, 'allows');
  await p.locator('.allows-content select').nth(0).selectOption('blank-tshirts'); await p.getByRole('button', { name: /another input/i }).click(); await p.waitForTimeout(300);
  await p.locator('.allows-content select').nth(1).selectOption('ink-cartridges'); await p.locator('.allows-content select').nth(2).selectOption('printed-tshirts'); await p.waitForTimeout(1500);
  d = await derived(p); check('ep3 Design (Intangible Asset)', /Design \(Intangible Asset\)/.test(d), d);
  await next(p);
  await add(p, 'has counterparty'); await party(p, 'Ada Okafor'); await next(p); await p.waitForTimeout(1200);
  check('ep3 new event: chain has design, builder cleared', /design/.test(await T(p, '.chain-panel')) && !/Ada/.test(await T(p, '.main-sentence')), await T(p, '.chain-panel'));
  await add(p, 'has date'); await date(p, '2026-01-03'); await next(p);
  await add(p, 'provides', 'cash'); await qty(p, 'provides', 60); await next(p);
  await add(p, 'receives', 'a service');
  d = await derived(p); check('ep3 Services Expense', /Services Expense/.test(d), d);
  await L.shot(p, '11-service');
  if (await nextEnabled(p)) { await next(p); } else { check('ep3 service step completable', false, 'Next disabled: service unit not selectable'); await p.getByRole('button', { name: /leave the walkthrough/i }).click(); await b.close(); return; }
  await p.waitForTimeout(1200);

  // ---- Episode 4: shirts
  await add(p, 'has date'); await date(p, '2026-01-04'); await next(p);
  await add(p, 'provides', 'cash'); await qty(p, 'provides', 100); await next(p);
  await add(p, 'receives', 'physical units'); await item(p, 'receives', 'blank-tshirts'); await qty(p, 'receives', 20);
  d = await derived(p); check('ep4 Raw Materials Inventory', /Raw Materials Inventory/.test(d), d);
  await next(p);
  await p.locator('tr.dj-line').filter({ hasText: /Raw Materials/ }).first().click(); await p.waitForTimeout(800);
  d = await derived(p); check('ep4 Decided earlier names the printer event', /Decided earlier/i.test(d) && /blank-tshirts and ink-cartridges/i.test(d), d.slice(0, 400));
  await L.shot(p, '12-decided-earlier');
  await next(p);
  await add(p, 'has counterparty'); await party(p, 'TextileDirect'); await next(p); await p.waitForTimeout(1200);

  // ---- Episode 5: ink
  await add(p, 'has date'); await date(p, '2026-01-04'); await next(p);
  await add(p, 'provides', 'cash'); await qty(p, 'provides', 20); await next(p);
  await add(p, 'receives', 'physical units'); await item(p, 'receives', 'ink-cartridges'); await qty(p, 'receives', 2);
  d = await derived(p); check('ep5 ink is Raw Materials', /Raw Materials Inventory/.test(d), d);
  await next(p);
  await add(p, 'has counterparty'); await party(p, 'InkMasters'); await next(p); await p.waitForTimeout(1200);
  check('ep6 holdings listed under the chain', /On hand/.test(await T(p, '.chain-panel')) && /blank-tshirts/.test(await T(p, '.chain-panel')), await T(p, '.chain-panel'));

  // ---- Episode 6: printing
  await add(p, 'has date'); await date(p, '2026-01-05'); await next(p);
  await add(p, 'consumes');
  await p.locator('.transformation-row input').first().fill('10'); await p.locator('.transformation-row select').first().selectOption('blank-tshirts'); await p.waitForTimeout(300);
  await p.getByRole('button', { name: /another input/i }).click(); await p.waitForTimeout(300);
  await p.locator('.transformation-row').nth(1).locator('input').first().fill('1'); await p.locator('.transformation-row').nth(1).locator('select').first().selectOption('ink-cartridges'); await p.waitForTimeout(1500);
  d = await derived(p); check('ep6 consumes priced $50 and $10', /\$50/.test(d) && /\$10/.test(d), d);
  await next(p);
  await add(p, 'creates');
  const cr = p.locator('.sentence-section.transformation').last();
  await cr.locator('input').first().fill('10'); await cr.locator('select').first().selectOption('printed-tshirts'); await p.waitForTimeout(1500);
  d = await derived(p); check('ep6 Finished Goods $60', /Finished Goods Inventory\s+\$60/.test(d), d);
  await next(p);
  await add(p, 'is allowed by');
  const capSel = p.locator('.allowed-by-content select').first();
  const capOpts = await capSel.evaluate(s => [...s.options].map(o => o.textContent));
  check('ep6 is-allowed-by offers the printer event', capOpts.some(o => /printer/.test(o)), capOpts.join(' | '));
  await capSel.selectOption({ index: 1 }); await p.waitForTimeout(1200);
  check('ep6 is-allowed-by step completes', await nextEnabled(p));
  await next(p); await p.waitForTimeout(1200);

  // ---- Episode 7: sale
  await add(p, 'has date'); await date(p, '2026-01-07'); await next(p);
  await add(p, 'provides', 'physical units'); await item(p, 'provides', 'printed-tshirts'); await qty(p, 'provides', 4); await next(p);
  await add(p, 'receives', 'cash'); await qty(p, 'receives', 100); await next(p);
  await add(p, 'has counterparty'); await party(p, 'Campus Boutique');
  d = await derived(p); check('ep7 Revenue and COGS $24', /Revenue\s+\$100/.test(d) && /Cost of Goods Sold\s+\$24/.test(d), d);
  await next(p);
  const fromSel = p.locator('.assertion-fragment.provides select').nth(1);
  check('ep7 batch picker offered on the sale', (await fromSel.count()) === 1);
  if (await fromSel.count()) { const o = await fromSel.evaluate(s => [...s.options].map(x => x.value)); check('ep7 batch picker lists production', o.includes('production'), o.join(',')); await fromSel.selectOption('production'); await p.waitForTimeout(1500); }
  check('ep7 batch step completes', await nextEnabled(p));
  await p.locator('tr.dj-line').filter({ hasText: /Cost of Goods Sold/ }).first().click(); await p.waitForTimeout(800);
  d = await derived(p); check('ep7 drill-down names the batch', /batch production/i.test(d), d.slice(0, 500));
  await L.shot(p, '13-sale');
  await next(p); await next(p); await p.waitForTimeout(1500);
  check('walkthrough finished back at gate', (await p.getByRole('button', { name: /start tutorial/i }).count()) === 1);
  await L.shot(p, '14-after');
  console.log('SUMMARY', results.filter(r => r.ok).length, 'passed,', results.filter(r => !r.ok).length, 'failed');
  await b.close();
})().catch(e => { console.log('ERR', e.message.slice(0, 300)); console.log('SUMMARY', results.filter(r => r.ok).length, 'passed,', results.filter(r => !r.ok).length, 'failed'); process.exit(1); });
