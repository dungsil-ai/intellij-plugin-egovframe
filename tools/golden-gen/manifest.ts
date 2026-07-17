#!/usr/bin/env node
import * as crypto from 'crypto'
import * as fs from 'fs'
import * as path from 'path'

const UPSTREAM_TAG = 'v5.0.6'
const ROOT = path.resolve(__dirname, '../..')
const UPSTREAM_DIR = path.join(ROOT, 'vendor/egovframe-vscode-initializr')
const EGOVFRAME_DIR = path.join(ROOT, 'src/main/resources/egovframe')
const BUNDLED_ZIPS: Record<string, true> = {
  'egovframe-boot-simple-backend.zip': true,
  'egovframe-boot-web.zip': true,
}

interface ProjectTemplate {
  displayName: string;
  fileName: string;
}

interface LfsPointer {
  sha256: string;
  size: number;
}

function collectFiles(directory: string): string[] {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const absolute = path.join(directory, entry.name)
    if (entry.isDirectory()) return collectFiles(absolute)
    return entry.isFile() ? [absolute] : []
  })
}

function sha256(bytes: Buffer): string {
  return crypto.createHash('sha256').update(bytes).digest('hex')
}

function parseLfsPointer(bytes: Buffer): LfsPointer | null {
  const text = bytes.toString('utf8')
  if (!text.startsWith('version https://git-lfs.github.com/spec/v1\n')) return null
  const digest = /^oid sha256:([0-9a-f]{64})$/m.exec(text)?.[1]
  const size = Number(/^size (\d+)$/m.exec(text)?.[1])
  if (!digest || !Number.isSafeInteger(size)) throw new Error(`Invalid Git LFS pointer:\n${text}`)
  return { sha256: digest, size }
}

function addDirectoryAssets(
  bundled: Record<string, string>,
  sourceDirectory: string,
  resourcePrefix: string,
  extension: string,
): number {
  const files = collectFiles(sourceDirectory).filter((file) => file.endsWith(extension)).sort()
  for (const sourceFile of files) {
    const relative = path.relative(sourceDirectory, sourceFile).replace(/\\/g, '/')
    bundled[`${resourcePrefix}/${relative}`] = sha256(fs.readFileSync(sourceFile))
  }
  return files.length
}

function main(): void {
  const packagePath = path.join(UPSTREAM_DIR, 'package.json')
  if (!fs.existsSync(packagePath)) {
    throw new Error('Missing upstream submodule. Run: git submodule update --init')
  }
  const packageMetadata = JSON.parse(fs.readFileSync(packagePath, 'utf8')) as { version: string }
  if (`v${packageMetadata.version}` !== UPSTREAM_TAG) {
    throw new Error(`Submodule/package version mismatch: expected ${UPSTREAM_TAG}, found ${packageMetadata.version}`)
  }

  const bundled: Record<string, string> = {}
  const codeCount = addDirectoryAssets(
    bundled,
    path.join(UPSTREAM_DIR, 'templates/code'),
    'egovframe/code',
    '.hbs',
  )
  const configCount = addDirectoryAssets(
    bundled,
    path.join(UPSTREAM_DIR, 'templates/config'),
    'egovframe/config',
    '.hbs',
  )
  const pomCount = addDirectoryAssets(
    bundled,
    path.join(UPSTREAM_DIR, 'templates/projects/pom'),
    'egovframe/projects/pom',
    '.xml',
  )

  const catalogNames = ['templates-context-xml.json', 'templates-projects.json']
  for (const catalogName of catalogNames) {
    bundled[`egovframe/${catalogName}`] = sha256(
      fs.readFileSync(path.join(UPSTREAM_DIR, 'templates', catalogName)),
    )
  }

  const projectCatalog = JSON.parse(
    fs.readFileSync(path.join(UPSTREAM_DIR, 'templates/templates-projects.json'), 'utf8'),
  ) as ProjectTemplate[]
  const zips: Record<string, object> = {}
  for (const project of [...projectCatalog].sort((left, right) => left.fileName.localeCompare(right.fileName))) {
    const zipPath = path.join(UPSTREAM_DIR, 'templates/projects/examples', project.fileName)
    const bytes = fs.readFileSync(zipPath)
    const pointer = parseLfsPointer(bytes)
    const bundledZip = BUNDLED_ZIPS[project.fileName] === true
    if (bundledZip && pointer) {
      throw new Error(
        `Bundled ZIP is still an LFS pointer: ${project.fileName}. Run git lfs pull for the two bundled ZIPs.`,
      )
    }
    const digest = pointer?.sha256 ?? sha256(bytes)
    const size = pointer?.size ?? bytes.length
    const url = `https://media.githubusercontent.com/media/eGovFramework/egovframe-vscode-initializr/${UPSTREAM_TAG}/templates/projects/examples/${project.fileName}`
    if (bundledZip) bundled[`egovframe/projects/examples/${project.fileName}`] = digest
    zips[project.fileName] = {
      bundled: bundledZip,
      displayName: project.displayName,
      sha256: digest,
      size,
      url,
    }
  }

  if (codeCount !== 11 || configCount !== 52 || pomCount !== 18 || projectCatalog.length !== 22) {
    throw new Error(
      `Unexpected ${UPSTREAM_TAG} inventory: code=${codeCount}, config=${configCount}, pom=${pomCount}, zip=${projectCatalog.length}`,
    )
  }

  const manifest = {
    bundled: Object.fromEntries(Object.entries(bundled).sort(([left], [right]) => left.localeCompare(right))),
    upstreamTag: UPSTREAM_TAG,
    zips,
  }
  fs.writeFileSync(
    path.join(EGOVFRAME_DIR, 'asset-manifest.json'),
    `${JSON.stringify(manifest, null, 2)}\n`,
    'utf8',
  )
  console.log(`Generated ${UPSTREAM_TAG} manifest from submodule: ${codeCount} code, ${configCount} config, ${pomCount} POM, ${projectCatalog.length} ZIP.`)
}

main()
