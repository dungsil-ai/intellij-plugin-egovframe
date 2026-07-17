#!/usr/bin/env node
/**
 * Generates Handlebars.java-compatible copies of the pinned templates.
 *
 * handlebars.js mutates ContentStatement values during its whitespace-control
 * pass. Handlebars.java 4.5.3 differs for standalone blocks and explicit `~`
 * stripping. We preserve the original assets and materialize the JS-processed
 * source text under egovframe/normalized as <template-name>@<source-sha256>.hbs.
 */
import Handlebars from 'handlebars'
import * as crypto from 'crypto'
import * as fs from 'fs'
import * as path from 'path'

const ROOT = path.resolve(__dirname, '../..')
const UPSTREAM_DIR = path.join(ROOT, 'vendor/egovframe-vscode-initializr')
const TEMPLATE_DIRS = [
  path.join(UPSTREAM_DIR, 'templates/code'),
  path.join(UPSTREAM_DIR, 'templates/config'),
]
const EGOVFRAME_DIR = path.join(ROOT, 'src/main/resources/egovframe')
const OUTPUT_DIR = path.join(EGOVFRAME_DIR, 'normalized')
const INDEX_PATH = path.join(EGOVFRAME_DIR, 'handlebars-normalized.properties')

interface Position {
  line: number;
  column: number;
}

interface ContentNode {
  type: 'ContentStatement';
  original: string;
  value: string;
  loc: { start: Position; end: Position };
}

function listTemplates(directory: string): string[] {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const absolute = path.join(directory, entry.name)
    if (entry.isDirectory()) {
      return listTemplates(absolute)
    }
    return entry.isFile() && entry.name.endsWith('.hbs') ? [absolute] : []
  })
}

function contentNodes(ast: unknown): ContentNode[] {
  const nodes = new Map<string, ContentNode>()
  const visit = (value: unknown): void => {
    if (!value || typeof value !== 'object') return
    const record = value as Record<string, unknown>
    if (record.type === 'ContentStatement') {
      const node = value as ContentNode
      const key = `${node.loc.start.line}:${node.loc.start.column}-${node.loc.end.line}:${node.loc.end.column}`
      const previous = nodes.get(key)
      if (previous && previous.value !== node.value) {
        throw new Error(`Conflicting ContentStatement values at ${key}`)
      }
      nodes.set(key, node)
    }
    for (const [name, child] of Object.entries(record)) {
      if (name === 'loc') continue
      if (Array.isArray(child)) child.forEach(visit)
      else visit(child)
    }
  }
  visit(ast)
  return [...nodes.values()]
}

function lineOffsets(source: string): number[] {
  const offsets = [0]
  for (let i = 0; i < source.length; i += 1) {
    if (source[i] === '\n') offsets.push(i + 1)
  }
  return offsets
}

function offsetAt(offsets: number[], position: Position): number {
  const lineOffset = offsets[position.line - 1]
  if (lineOffset === undefined) throw new Error(`Invalid source line ${position.line}`)
  return lineOffset + position.column
}

function normalize(source: string): string {
  const ast = Handlebars.parse(source)
  const offsets = lineOffsets(source)
  const replacements = contentNodes(ast)
    .map((node) => ({
      start: offsetAt(offsets, node.loc.start),
      end: offsetAt(offsets, node.loc.end),
      original: node.original,
      value: node.value,
    }))
    .sort((left, right) => right.start - left.start)

  let normalized = source
  for (const replacement of replacements) {
    const actual = source.slice(replacement.start, replacement.end)
    if (actual !== replacement.original) {
      throw new Error(
        `ContentStatement location mismatch: expected ${JSON.stringify(replacement.original)}, got ${JSON.stringify(actual)}`,
      )
    }
    normalized = normalized.slice(0, replacement.start) + replacement.value + normalized.slice(replacement.end)
  }

  // JS already applied these markers to ContentStatement values. Removing them
  // prevents Handlebars.java from applying whitespace stripping a second time.
  normalized = normalized
    .replace(/\{\{\{~/g, '{{{')
    .replace(/\{\{~/g, '{{')
    .replace(/~\}\}\}/g, '}}}')
    .replace(/~\}\}/g, '}}')

  // A stripped newline can place a block close directly before generated Java
  // text beginning with `}`. Separate that exact lexical boundary without
  // corrupting legitimate triple-stash expressions such as `{{{value}}}`.
  return normalized.replace(/(\{\{\/[^{}\r\n]+\}\})(?=\})/g, '$1{{!java-lexer-separator}}')
}

fs.rmSync(OUTPUT_DIR, { recursive: true, force: true })
fs.mkdirSync(OUTPUT_DIR, { recursive: true })

const entries: string[] = []
for (const templatePath of TEMPLATE_DIRS.flatMap(listTemplates).sort()) {
  const source = fs.readFileSync(templatePath, 'utf8')
  const digest = crypto.createHash('sha256').update(source).digest('hex')
  const normalized = normalize(source)
  const outputName = `${path.basename(templatePath, '.hbs')}@${digest}.hbs`
  fs.writeFileSync(path.join(OUTPUT_DIR, outputName), normalized, 'utf8')
  entries.push(`${digest}=egovframe/normalized/${outputName}`)
}

fs.writeFileSync(INDEX_PATH, `${entries.join('\n')}\n`, 'utf8')
console.log(`Generated ${entries.length} normalized templates.`)
