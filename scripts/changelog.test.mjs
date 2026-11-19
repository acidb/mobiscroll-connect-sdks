// Run: node --test scripts/changelog.test.mjs
import test from 'node:test';
import assert from 'node:assert/strict';
import { parseChangelog, parseSummary, checkSummary, inlineHtml, ChangelogError } from './changelog.mjs';

const GOOD = `# Changelog

## [Unreleased]

### Added

- <!-- Events --> \`events.list()\` accepts \`eventIds\`.
  It wraps. See [the reference](https://mobiscroll.com/docs/connect/integration/node-sdk#events-list).
- <!-- Webhooks --> \`subscribeWebhook()\` accepts \`singleEvents\`.

### Fixed

- <!-- SDK --> We fixed the user's type.

## [1.6.1] — 2026-11-11

### Fixed

- <!-- OAuth --> We added the missing \`auth.revoke()\`.

## [1.6.0] — 2026-10-08

### Added

- <!-- General --> Old.
`;

const parse = (text, version, unreleased) => parseChangelog(text, 'CHANGELOG.md', version, unreleased);
const fails = (fn, pattern) => assert.throws(fn, (e) => e instanceof ChangelogError && pattern.test(e.message));

test('without --version the newest released section is used, with its date', () => {
  const r = parse(GOOD);
  assert.equal(r.version, '1.6.1');
  assert.equal(r.date, '2026-11-11');
  assert.deepEqual([r.entries[0].component, r.entries[0].type], ['OAuth', 'Fixed']);
});

test('[Unreleased] is used for a version that has no section yet', () => {
  const r = parse(GOOD, '1.7.0', true);
  assert.equal(r.version, '1.7.0');
  assert.equal(r.date, null);
  assert.equal(r.entries.length, 3);
  assert.deepEqual(
    r.entries.map((e) => [e.component, e.type]),
    [
      ['Events', 'New'],
      ['Webhooks', 'New'],
      ['SDK', 'Fixed'],
    ],
  );
  assert.match(
    r.entries[0].text,
    /^<code>events\.list\(\)<\/code> accepts <code>eventIds<\/code>\. It wraps\. See <a href="\/docs\/connect\/integration\/node-sdk#events-list">the reference<\/a>\.$/,
  );
  assert.match(r.entries[2].text, /user&rsquo;s type/);
  fails(() => parse(GOOD, '1.7.0', false), /no section ## \[1\.7\.0\]/);
  assert.equal(parse(GOOD, '1.6.0').entries[0].component, 'General');
});

test('stops on a missing or unknown component, heading or loose text', () => {
  fails(() => parse(GOOD.replace('<!-- Events --> ', ''), '1.7.0', true), /CHANGELOG\.md:\d+: an entry without a component/);
  fails(() => parse(GOOD.replace('<!-- SDK -->', '<!-- Eventcalendar -->'), '1.7.0', true), /"Eventcalendar" is not a Connect component/);
  fails(
    () => parse(GOOD.replace('### Fixed\n\n- <!-- SDK', '### Security\n\n- <!-- SDK'), '1.7.0', true),
    /the ### headings are Added, Fixed, Changed and Removed/,
  );
  fails(() => parse(GOOD.replace('### Added\n', 'Some loose text\n'), '1.7.0', true), /text outside an entry|before its ###/);
});

test('summary file', () => {
  const html = parseSummary('### Event lookup\n\nEvents can be read\nby ID.\n\n### Webhook mode\n\nA `singleEvents` choice.\n', 's.md');
  assert.equal(
    html,
    '<h3>Event lookup</h3>\n<p>Events can be read by ID.</p>\n<h3>Webhook mode</h3>\n<p>A <code>singleEvents</code> choice.</p>',
  );
  fails(() => parseSummary('', 's.md'), /no summary/);
  fails(() => parseSummary('- a list\n', 's.md'), /### titles and paragraphs only/);
});

const ENTRIES = [
  { component: 'Events', type: 'New', text: 'The list accepts <code>eventIds</code> and returns only those events from every provider.' },
];

test('checkSummary: the summary rules', () => {
  checkSummary('<h3>Lookup</h3>\n<p>Events can be read by ID.</p>', ENTRIES, 's.md');
  const stops = (summary, pattern) => fails(() => checkSummary(summary, ENTRIES, 's.md'), pattern);
  stops('<h3>Lookup</h3>\n<p>Events can be read. By ID too.</p>', /one short sentence/);
  stops('<h3>Lookup</h3>\n<p>The list accepts eventIds and returns only those events.</p>', /repeats an entry/);
  stops('<h3>Lookup</h3>', /without its sentence/);
});

test('inlineHtml', () => {
  assert.equal(inlineHtml("`events()->list()` it's"), '<code>events()-&gt;list()</code> it&rsquo;s');
});
