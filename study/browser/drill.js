// The practice drill in the real browser: test-out, a problem, the peek, feedback, level 1.
const L = require('./lib.js');
const results = [];
const check = (name, ok, detail) => { results.push({ name, ok }); console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail ? ' — ' + String(detail).replace(/\s+/g, ' ').slice(0, 200) : '')); };
const T = async (p, sel) => (await L.text(p, sel)) || '';
const openPal = async p => { await p.getByRole('button', { name: /add assertion/i }).click(); await p.waitForTimeout(300); };
const add = async (p, code, unit) => { await openPal(p); await p.getByRole('button', { name: new RegExp('^' + code, 'i') }).first().click(); await p.waitForTimeout(300); if (unit) { await p.getByRole('button', { name: new RegExp('^' + unit + '$', 'i') }).click(); await p.waitForTimeout(700); } };
const qty = async (p, frag, v) => { await p.locator(`.assertion-fragment.${frag} input[placeholder=qty]`).first().fill(String(v)); await p.waitForTimeout(500); };
const item = async (p, frag, v) => { await p.locator(`.assertion-fragment.${frag} select`).first().selectOption(v); await p.waitForTimeout(500); };
const party = async (p, n) => { await p.locator('input[placeholder*=party]').fill(n); await p.waitForTimeout(500); };
(async () => {
  const { b, p } = await L.launch();
  await L.login(p, 'browser-drill2@study.test'); await p.waitForTimeout(1500);
  await p.getByRole('button', { name: /skip to the practice round/i }).click(); await p.waitForTimeout(2500);
  await L.shot(p, '20-drill');
  const narr = await T(p, '.narrative-panel');
  check('drill header says other businesses', /Other people's businesses/.test(await T(p, '.drill-header')), await T(p, '.drill-header'));
  check('narrative names a company and shows its blurb', /Riverside|Bold Ink|Northside|Maple|Blue Heron|Summit|Campus Threads|Harbor Line/.test(narr), narr.slice(0, 300));
  check('the company chain is shown under the narrative', /The chain —/.test(narr) && /Funding-001|Printer-001/.test(narr), narr.slice(-300));
  console.log('NARRATIVE:', narr.replace(/\s+/g, ' ').slice(0, 500));
  // decide what to build from the narrative
  const m = narr.match(/purchases (\d+) (blank t-shirts|ink cartridges)|for \$(\d+) cash|pays [^$]*\$(\d+)|(design)|T-shirt Printer/i);
  const amount = (narr.match(/\$(\d[\d,]*)/) || [])[1];
  check('amount found in narrative', !!amount, narr.slice(0, 200));
  await add(p, 'provides', 'cash'); await qty(p, 'provides', (amount || '100').replace(/,/g, ''));
  // the peek before receives: partial entry
  const peek = p.getByRole('button', { name: /what would this produce/i });
  check('peek button offered before submit', (await peek.count()) === 1);
  await peek.click(); await p.waitForTimeout(1500);
  const d0 = await T(p, '.derived-je'); check('peek shows a derived panel with a placeholder', /Something must balance|\?\?\?/.test(d0) || /Cash/.test(d0), d0.slice(0, 200));
  await L.shot(p, '21-peek');
  // finish according to the narrative
  if (/blank t-shirts/i.test(narr)) { await add(p, 'receives', 'physical units'); await item(p, 'receives', 'blank-tshirts'); await qty(p, 'receives', (narr.match(/(\d+) blank/i) || [, '20'])[1]); }
  else if (/ink cartridges/i.test(narr)) { await add(p, 'receives', 'physical units'); await item(p, 'receives', 'ink-cartridges'); await qty(p, 'receives', (narr.match(/(\d+) ink/i) || [, '10'])[1]); }
  else if (/printer/i.test(narr)) { await add(p, 'receives', 'physical units'); await item(p, 'receives', 't-shirt-printer'); await qty(p, 'receives', 1); await add(p, 'allows'); await p.locator('.allows-content select').nth(0).selectOption('blank-tshirts'); await p.getByRole('button', { name: /another input/i }).click(); await p.locator('.allows-content select').nth(1).selectOption('ink-cartridges'); await p.locator('.allows-content select').nth(2).selectOption('printed-tshirts'); }
  else if (/design/i.test(narr)) { await add(p, 'receives', 'physical units'); await item(p, 'receives', 'logo-design'); await qty(p, 'receives', 1); await add(p, 'allows'); await p.locator('.allows-content select').nth(0).selectOption('blank-tshirts'); await p.getByRole('button', { name: /another input/i }).click(); await p.locator('.allows-content select').nth(1).selectOption('ink-cartridges'); await p.locator('.allows-content select').nth(2).selectOption('printed-tshirts'); }
  else { await add(p, 'receives', 'a service'); await qty(p, 'receives', 1); }
  await add(p, 'has counterparty'); const vendor = (narr.match(/from ([A-Z][A-Za-z' ]+?) for|pays ([A-Z][A-Za-z' ,&]+?) \$/) || [])[1] || 'Vendor'; await party(p, vendor);
  await p.waitForTimeout(800);
  await p.getByRole('button', { name: /submit answer/i }).click(); await p.waitForTimeout(2500);
  const fb = await T(p, '.feedback-panel');
  check('feedback shown after submit', /✓ Correct|✗ Incorrect|Incomplete/.test(fb), fb.slice(0, 300));
  console.log('FEEDBACK:', fb.replace(/\s+/g, ' ').slice(0, 700));
  check('derived panel shown post-commit', /What your assertions produce/.test(fb));
  await L.shot(p, '22-feedback');
  const nextBtn = p.getByRole('button', { name: /next practice problem|start recording|fresh round/i });
  check('round controls present', (await nextBtn.count()) >= 1, await p.locator('.feedback-panel button').allInnerTexts());
  console.log('SUMMARY', results.filter(r => r.ok).length, 'passed,', results.filter(r => !r.ok).length, 'failed');
  await b.close();
})().catch(e => { console.log('ERR', e.message.slice(0, 300)); console.log('SUMMARY', results.filter(r => r.ok).length, 'passed,', results.filter(r => !r.ok).length, 'failed'); process.exit(1); });
