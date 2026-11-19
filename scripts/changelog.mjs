/**
 * Turns an SDK's changelog into its release notes card in the web repo, which the releases page, the feeds and the C#
 * sites read: <websitePath>/MobiscrollFront/data/release-notes/connect/<language>-sdk/<version>.json (the format is in
 * the README.md there).
 *
 * Reads sdks/<sdk>/CHANGELOG.md (Keep a Changelog: the entries, each with its component) and
 * sdks/<sdk>/summary-<version>.md (the release summary). See CLAUDE.md -> Release cards.
 *
 *   node scripts/changelog.mjs --sdk node                     Write the card of the newest release of the Node.js SDK
 *   node scripts/changelog.mjs --sdk all --version 1.7.0      Write the 1.7.0 card of every SDK
 *   node scripts/changelog.mjs --sdk go --version 1.7.0       Take the entries of [Unreleased] when there is no [1.7.0] yet
 *   node scripts/changelog.mjs --sdk node --date 2026-11-20   Write the card with this release date
 *   node scripts/changelog.mjs --sdk node --print             Show the card, write nothing
 *   node scripts/changelog.mjs --sdk all --check              Check the changelogs and the summaries, write nothing
 *   node scripts/changelog.mjs --sdk node --out <folder>      Write it into another folder (with --sdk all, one subfolder per SDK;
 *                                                             the web repo is websitePath in config.json, or ../mobiscroll-web)
 *
 * --sdk is node, python, php, dotnet, java, go, ruby or all. Without --version the newest [X.Y.Z] section is used: the
 * patch numbers are independent per SDK. The date is the one in the section's heading (## [1.6.1] — 2026-11-11), else
 * --date, else today.
 *
 * The changelog (see sdks/node/CHANGELOG.md):
 *
 *   ## [1.7.0] — 2026-11-20
 *   ### Added                        Added (the card's New), Fixed, Changed or Removed
 *   - <!-- Events --> text           the entry's component, one of COMPONENTS: required in every entry; the comment is
 *                                    invisible in the rendered Markdown and in the GitHub release notes
 *
 * and sdks/node/summary-1.7.0.md: one "### title" and a paragraph of two or three sentences per topic. An entry can wrap onto the next
 * lines; a blank line, a heading or the next entry ends it.
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');

/** The SDKs: the folder in sdks/ and the folder of its cards in the web repo. */
export const SDKS = {
  node: 'nodejs-sdk',
  python: 'python-sdk',
  php: 'php-sdk',
  dotnet: 'dotnet-sdk',
  java: 'java-sdk',
  go: 'go-sdk',
  ruby: 'ruby-sdk',
};

/** The components of a Connect card (ids in components.json); SDK is what no API area owns. */
export const COMPONENTS = ['Events', 'Webhooks', 'OAuth', 'SDK', 'General', 'All'];

/** The ### headings of Keep a Changelog, as the card writes the type. */
const TYPE_HEADINGS = { added: 'New', fixed: 'Fixed', changed: 'Changed', removed: 'Removed' };

/** A changelog the script can't read: the message names the file and line. */
export class ChangelogError extends Error {}

function fail(message) {
  throw new ChangelogError(message);
}

function readArgs(argv) {
  const args = { print: false, check: false };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === '--print' || argv[i] === '--check') {
      args[argv[i].slice(2)] = true;
    } else if (['--sdk', '--date', '--out', '--version'].includes(argv[i])) {
      if (!argv[i + 1]) {
        fail(argv[i] + ' needs a value');
      }
      args[argv[i].slice(2)] = argv[++i];
    } else {
      fail('unknown argument ' + argv[i] + ' (use --sdk <name|all>, --date YYYY-MM-DD, --out <folder>, --version X.Y.Z, --print, --check)');
    }
  }
  if (!args.sdk) {
    fail('--sdk is needed: ' + Object.keys(SDKS).join(', ') + ' or all');
  }
  if (args.sdk !== 'all' && !SDKS[args.sdk]) {
    fail('--sdk ' + args.sdk + ' is not an SDK (' + Object.keys(SDKS).join(', ') + ', all)');
  }
  return args;
}

function today() {
  const now = new Date();
  return [now.getFullYear(), now.getMonth() + 1, now.getDate()].map((n) => String(n).padStart(2, '0')).join('-');
}

/** A date like 2026-11-20, checked. */
function validDate(date) {
  return /^\d{4}-\d{2}-\d{2}$/.test(date) && new Date(date + 'T00:00:00Z').toISOString().startsWith(date);
}

function readFile(file) {
  return fs.existsSync(file) ? fs.readFileSync(file, 'utf8') : null;
}

/** The web repo: websitePath in config.json, or the mobiscroll-web checkout next to this one. */
function webRepo() {
  const config = readFile(path.join(ROOT, 'config.json'));
  const configured = config && JSON.parse(config).websitePath;
  const web = configured || path.join(ROOT, '..', 'mobiscroll-web');
  return fs.existsSync(path.join(web, 'MobiscrollFront', 'data', 'release-notes')) ? web : null;
}

/** A code span as text: `<Eventcalendar />` is shown, not read as HTML. */
function escapeHtml(text) {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/**
 * Markdown inside a line as HTML, as the cards write it: `code` (its text escaped), **bold** as <strong>, [links](url)
 * with https://mobiscroll.com cut off, and ' as &rsquo;. Other HTML stays as written.
 */
export function inlineHtml(text) {
  const codes = [];
  return text
    .replace(/`([^`]+)`/g, (span, code) => '' + (codes.push('<code>' + escapeHtml(code) + '</code>') - 1) + '')
    .replace(/&(?!#?\w+;)/g, '&amp;')
    .replace(/'/g, '&rsquo;')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(
      /\[([^\]]+)\]\(([^)\s]+)\)/g,
      (link, label, url) => '<a href="' + url.replace(/^https:\/\/mobiscroll\.com(?=\/)/, '') + '">' + label + '</a>',
    )
    .replace(/(\d+)/g, (mark, index) => codes[index]);
}

const COMMENT = /<!--\s*([A-Za-z]+)\s*-->/;

/**
 * The entries of one release in a CHANGELOG.md: the section "## [version]", or "## [Unreleased]" when there is no such
 * section and `allowUnreleased` is set. Without a version the newest "## [X.Y.Z]" section. Returns the version, the
 * heading's date (or null) and the entries in the order they are written.
 */
export function parseChangelog(text, file, version, allowUnreleased) {
  const lines = text.split(/\r?\n/);
  const sections = [];
  lines.forEach((line, i) => {
    const heading = line.match(/^##\s+\[([^\]]+)\](?:\s+[—–-]\s+(\d{4}-\d{2}-\d{2}))?\s*$/);
    if (heading) {
      sections.push({ name: heading[1], date: heading[2] || null, from: i + 1 });
    }
  });
  sections.forEach((section, i) => {
    section.to = i + 1 < sections.length ? sections[i + 1].from - 1 : lines.length;
  });

  const released = sections.filter((s) => /^\d+\.\d+\.\d+$/.test(s.name));
  let section = version ? released.find((s) => s.name === version) : released[0];
  if (!section && version && allowUnreleased) {
    section = sections.find((s) => s.name === 'Unreleased');
  }
  if (!section) {
    fail(file + ': no section ' + (version ? '## [' + version + '] (or ## [Unreleased])' : 'with a version'));
  }
  const sectionVersion = /^\d+\.\d+\.\d+$/.test(section.name) ? section.name : version;

  const entries = [];
  let type = null;
  let bullet = null;
  let bulletLine = 0;

  const where = (line) => file + ':' + line + ': ';

  function endBullet() {
    if (bullet === null) {
      return;
    }
    const comment = bullet.match(COMMENT);
    if (!comment) {
      fail(where(bulletLine) + 'an entry without a component: start it with <!-- Events --> (' + COMPONENTS.join(', ') + ')');
    }
    if (!COMPONENTS.includes(comment[1])) {
      fail(where(bulletLine) + '"' + comment[1] + '" is not a Connect component (' + COMPONENTS.join(', ') + ')');
    }
    const entryText = bullet.replace(COMMENT, '').replace(/\s+/g, ' ').trim();
    if (!entryText) {
      fail(where(bulletLine) + 'an entry without text');
    }
    entries.push({ component: comment[1], type: type, text: inlineHtml(entryText) });
    bullet = null;
  }

  for (let nr = section.from + 1; nr <= section.to; nr++) {
    const line = lines[nr - 1];
    const h3 = line.match(/^###\s+(.+?)\s*$/);
    const item = line.match(/^\s*-\s+(.*)$/);
    if (h3 || item || !line.trim()) {
      endBullet();
    }
    if (h3) {
      type = TYPE_HEADINGS[h3[1].toLowerCase()];
      if (!type) {
        fail(where(nr) + '### ' + h3[1] + ': the ### headings are Added, Fixed, Changed and Removed');
      }
    } else if (item) {
      if (!type) {
        fail(where(nr) + 'an entry before its ### Added, Fixed, Changed or Removed heading');
      }
      bullet = item[1].trim();
      bulletLine = nr;
    } else if (!line.trim()) {
      // A blank line ends the bullet (above).
    } else if (bullet !== null) {
      bullet += ' ' + line.trim();
    } else {
      fail(where(nr) + 'text outside an entry: ' + line.trim());
    }
  }
  endBullet();

  if (!entries.length) {
    fail(file + ': no entries in ## [' + section.name + ']');
  }
  return { version: sectionVersion, date: section.date, entries: entries };
}

/** The summary file: "### title" and the sentence under it, as <h3> and <p>. */
export function parseSummary(text, file) {
  const summary = [];
  let paragraph = null;
  let lineNr = 0;

  function endParagraph() {
    if (paragraph !== null) {
      summary.push('<p>' + inlineHtml(paragraph.join(' ')) + '</p>');
      paragraph = null;
    }
  }

  text.split(/\r?\n/).forEach((line) => {
    lineNr++;
    const h3 = line.match(/^###\s+(.+?)\s*$/);
    if (h3 || !line.trim()) {
      endParagraph();
    }
    if (h3) {
      summary.push('<h3>' + inlineHtml(h3[1]) + '</h3>');
    } else if (/^#{1,2}\s/.test(line) || /^\s*[-*]\s/.test(line)) {
      fail(file + ':' + lineNr + ': the summary has ### titles and paragraphs only');
    } else if (line.trim()) {
      paragraph = (paragraph || []).concat(line.trim());
    }
  });
  endParagraph();

  if (!summary.length) {
    fail(file + ': no summary (one ### title and a paragraph of two or three sentences per topic)');
  }
  return summary.join('\n');
}

/** The summary: a paragraph of 2 to 3 sentences (at most 60 words) per topic, at most 200 words in all. */
const MIN_SENTENCES = 2;
const MAX_SENTENCES = 3;
const MAX_TOPIC_WORDS = 60;
const MAX_WORDS = 200;

const plain = (html) =>
  html
    .replace(/<[^>]+>/g, ' ')
    .replace(/&rsquo;/g, "'")
    .replace(/&gt;/g, '>')
    .replace(/&lt;/g, '<')
    .replace(/&amp;/g, '&');

/** Words of a text, lowercase, without punctuation. */
const words = (html) =>
  plain(html)
    .toLowerCase()
    .split(/[^\p{L}\p{N}]+/u)
    .filter(Boolean);

/**
 * The summary rules (mobiscroll-web/CLAUDE.md -> Connect release summaries): one <h3> per topic with a paragraph of two or
 * three sentences (like Mobiscroll UI 6.2.0), at most 200 words in all, and no sentence and no run of five words shared with an entry of the card.
 */
export function checkSummary(summary, entries, file) {
  const parts = summary.split('\n');
  parts.forEach((part, i) => {
    const expected = i % 2 === 0 ? '<h3>' : '<p>';
    if (!part.startsWith(expected)) {
      fail(file + ': the summary has one <h3> title and one <p> paragraph per topic, nothing else (" ' + part.slice(0, 40) + ' ")');
    }
  });
  if (parts.length % 2) {
    fail(file + ': a summary title without its paragraph');
  }
  parts
    .filter((part) => part.startsWith('<p>'))
    .forEach((part) => {
      const sentences = plain(part)
        .trim()
        .split(/(?<=[.!?])\s+(?=\S)/);
      if (sentences.length < MIN_SENTENCES || sentences.length > MAX_SENTENCES) {
        fail(
          file +
            ': a summary topic has ' +
            MIN_SENTENCES +
            ' to ' +
            MAX_SENTENCES +
            ' sentences, this has ' +
            sentences.length +
            ': ' +
            plain(part).trim(),
        );
      }
      const length = words(part).length;
      if (length > MAX_TOPIC_WORDS) {
        fail(file + ': a summary topic has at most ' + MAX_TOPIC_WORDS + ' words, this has ' + length + ': ' + plain(part).trim());
      }
    });
  const total = words(summary).length;
  if (total > MAX_WORDS) {
    fail(file + ': the summary has ' + total + ' words, keep it to ' + MAX_WORDS + ' at most');
  }

  const runs = new Set();
  const sentences = new Set();
  entries.forEach((entry) => {
    const w = words(entry.text);
    for (let i = 0; i + 5 <= w.length; i++) {
      runs.add(w.slice(i, i + 5).join(' '));
    }
    plain(entry.text)
      .split(/(?<=[.!?])\s+(?=\S)/)
      .forEach((s) => sentences.add(words(s).join(' ')));
  });
  parts
    .filter((part) => part.startsWith('<p>'))
    .forEach((part) => {
      const w = words(part);
      for (let i = 0; i + 5 <= w.length; i++) {
        if (runs.has(w.slice(i, i + 5).join(' '))) {
          fail(file + ': the summary repeats an entry ("' + w.slice(i, i + 5).join(' ') + ' ..."): say it at a higher level');
        }
      }
      if (sentences.has(w.join(' '))) {
        fail(file + ': a summary sentence is an entry sentence: ' + plain(part).trim());
      }
    });
}

/** The card of one SDK, or null with --check/--print having shown it. */
function createCard(sdk, args, releaseNotes) {
  const dir = path.join(ROOT, 'sdks', sdk);
  const changelogFile = path.join(dir, 'CHANGELOG.md');
  const changelog = readFile(changelogFile);
  if (changelog === null) {
    fail('no sdks/' + sdk + '/CHANGELOG.md');
  }
  const name = path.relative(process.cwd(), changelogFile);
  const parsed = parseChangelog(changelog, name, args.version, true);
  if (!/^\d+\.\d+\.\d+$/.test(parsed.version)) {
    fail(name + ': the version is not like 1.7.0');
  }

  const summaryFile = path.join(dir, 'summary-' + parsed.version + '.md');
  const summaryText = readFile(summaryFile);
  if (summaryText === null) {
    fail('no sdks/' + sdk + '/summary-' + parsed.version + '.md (one ### title and a paragraph of two or three sentences per topic)');
  }
  const summaryName = path.relative(process.cwd(), summaryFile);
  const summary = parseSummary(summaryText, summaryName);
  checkSummary(summary, parsed.entries, summaryName);

  const date = args.date || parsed.date || today();
  if (!validDate(date)) {
    fail('--date ' + date + ' is not a date like 2026-11-20');
  }

  if (releaseNotes) {
    const known = JSON.parse(readFile(path.join(releaseNotes, 'components.json'))).map((c) => c.id);
    parsed.entries.forEach((entry) => {
      if (!known.includes(entry.component)) {
        fail('"' + entry.component + '" is not a component in ' + path.join(releaseNotes, 'components.json'));
      }
    });
  }
  return { sdk: sdk, name: name, card: { version: parsed.version, date: date, summary: summary, entries: parsed.entries } };
}

function run() {
  const args = readArgs(process.argv.slice(2));
  if (args.version && !/^\d+\.\d+\.\d+$/.test(args.version)) {
    fail('--version ' + args.version + ' is not a version like 1.7.0');
  }
  if (args.date && !validDate(args.date)) {
    fail('--date ' + args.date + ' is not a date like 2026-11-20');
  }
  const sdks = args.sdk === 'all' ? Object.keys(SDKS) : [args.sdk];
  const web = args.out ? null : webRepo();
  const releaseNotes = web && path.join(web, 'MobiscrollFront', 'data', 'release-notes');
  if (!args.out && !web && !args.print && !args.check) {
    fail('set websitePath in config.json (the web repo) or pass --out <folder>');
  }

  // Build every card first: one that can't be read stops the run before anything is written.
  const cards = sdks.map((sdk) => createCard(sdk, args, releaseNotes));

  if (args.check) {
    cards.forEach(({ name, card }) =>
      console.log('changelog: ' + name + ' ' + card.version + ' is fine: ' + card.entries.length + ' entries, summary checked'),
    );
    return;
  }
  if (args.print) {
    cards.forEach(({ card }) => process.stdout.write(JSON.stringify(card, null, 2) + '\n'));
    return;
  }
  cards.forEach(({ sdk, card }) => {
    const folder = args.out
      ? args.sdk === 'all'
        ? path.join(path.resolve(args.out), SDKS[sdk])
        : path.resolve(args.out)
      : path.join(releaseNotes, 'connect', SDKS[sdk]);
    const cardFile = path.join(folder, card.version + '.json');
    const existed = fs.existsSync(cardFile);
    fs.mkdirSync(folder, { recursive: true });
    fs.writeFileSync(cardFile, JSON.stringify(card, null, 2) + '\n');
    console.log((existed ? 'Updated ' : 'Wrote ') + cardFile + ': ' + card.entries.length + ' entries, ' + card.date);
  });
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    run();
  } catch (e) {
    if (!(e instanceof ChangelogError)) {
      throw e;
    }
    console.error('changelog: ' + e.message);
    process.exit(1);
  }
}
