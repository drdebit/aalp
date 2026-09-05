# Browser pass

Playwright scripts that walk the real page on choochoo the way a
student would -- the whole walkthrough with checks at every step, and a
practice-round problem with the peek, the feedback and the round
controls. They found what the text-mode client could not: a second
sentence renderer, a missing menu option, a component that threw on a
multi-flow drill-down.

    cd study/browser
    npm init -y && npm install playwright@1.47.0 && npx playwright install chromium
    node walk.js      # ~3 minutes; PASS/FAIL per check, screenshots in shots/
    node drill.js

Each run logs in as a fresh `browser-*@study.test` user; change the
email in the script if a run leaves a user mid-walkthrough.
