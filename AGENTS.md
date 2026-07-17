# Repository Guidelines

## Project Overview

This repository builds **eGovFrame Support**, a Kotlin IntelliJ IDEA plugin that replaces the official eGovFrame VS Code Initializr 5.0.6 workflow. It provides:

- a New Project wizard for 22 official project templates;
- DDL-driven CRUD generation for 11 artifacts;
- 21 configuration generators with XML, JavaConfig, YAML, and Properties variants;
- checksum-verified bundled/downloaded upstream assets.

The upstream asset baseline is the official `v5.0.6` tag recorded in `NOTICE` and `asset-manifest.json`.

## Architecture & Data Flow

The plugin is a single Gradle/Kotlin IntelliJ Platform project.

- **Project creation:** `EgovProjectWizard` → `TemplateCatalog` → `TemplateStore.ensure()` → `ProjectGenerator` → optional `MavenProjectLinker` → JDK 17 assignment and IDE notifications.
- **Config generation:** `ConfigPanel` → `ConfigFormDialog` → `ConfigGenerator.FormDefinition` / `PreparedConfig` → `EgovHandlebars` → guarded disk write → VFS refresh and editor open.
- **CRUD generation:** `CrudPanel` → `DdlParser` / `ErdParser` → `CrudGenerator.PreparedCrud` → 11 Handlebars templates → preview/selection → guarded writes and editor open.
- **Rendering parity:** original `.hbs` text is hashed; `EgovHandlebars` loads the matching normalized resource from `handlebars-normalized.properties`, registers JavaScript-compatible helpers, and renders with Handlebars.java.
- **Asset delivery:** `AssetManifest` describes pinned assets. `TemplateStore` uses bundled ZIPs when available, otherwise downloads into the IntelliJ system cache, verifies SHA-256, and writes atomically.

IntelliJ registrations live in `src/main/resources/META-INF/plugin.xml`. Maven integration is optional and isolated in `src/main/resources/META-INF/egovframe-maven.xml`.

## Key Directories

- `src/main/kotlin/kr/kyg/intellij/plugin/egovframe/`
  - `assets/`: catalogs, manifest parsing, checked cache/download storage.
  - `config/`: config form model, validation, rendering, and UI.
  - `crud/`: CRUD context preparation, template paths, preview, and generation UI.
  - `ddl/`: upstream-compatible DDL, SQL type, and ERD parsers.
  - `project/`: New Project wizard, ZIP extraction, POM replacement, optional Maven linking.
  - `render/`: Handlebars compatibility layer and template renderer.
  - `settings/`: application-level persistent defaults and Settings UI.
  - `ui/`: `eGovFrame` tool-window factory.
- `vendor/egovframe-vscode-initializr/`: Git submodule pinned to the official v5.0.6 tag target; authoritative upstream templates, catalogs, POMs, and LFS ZIPs.
- `src/main/resources/egovframe/`: plugin-owned manifest, generated defaults, normalized runtime templates, and notices. Raw upstream assets are packaged from the submodule by Gradle and are not duplicated here.
- `src/test/kotlin/`: behavioral tests grouped by the production package contracts.
- `src/test/resources/golden/`: committed JavaScript reference outputs and fixture index.
- `tools/golden-gen/`: development-only Node.js/npm manifest and derivative generators; not packaged into the plugin.
- `.github/workflows/`: build and tagged-release pipelines.

## Development Commands

Use the checked-in Gradle wrapper. JDK 21 is required.

```bash
# Linux/macOS
./gradlew build
./gradlew test
./gradlew runIde
./gradlew buildPlugin

# Windows (cmd.exe)
gradlew.bat build
gradlew.bat test
gradlew.bat runIde
gradlew.bat buildPlugin
```

Targeted tests:

```bash
./gradlew test --tests '*GoldenRenderTest' --tests '*HandlebarsSyntaxTest'
./gradlew test --tests '*DdlParserTest' --tests '*ErdParserTest' --tests '*DataTypesTest'
./gradlew test --tests '*AssetIntegrityTest' --tests '*TemplateCatalogTest' --tests '*TemplateStoreTest'
```

Full CI gate:

```bash
./gradlew buildPlugin check verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
```

The packaged plugin is written under `build/distributions/`. No dedicated lint or coverage task is configured.

## Code Conventions & Common Patterns

- Honor `.editorconfig`: UTF-8, LF, spaces, 120 columns, final newline, no trailing whitespace. Preserve surrounding Kotlin indentation when editing existing code.
- Prefer small `object` services for stateless parsers/generators and `data class` values for contracts.
- Keep generation in two phases where applicable: prepare/validate/render first, then write. Examples: `ConfigGenerator.PreparedConfig` and `CrudGenerator.PreparedCrud`.
- Core code uses `require` / `IllegalArgumentException` for invalid inputs. UI code converts failures into inline validation and `EgovNotifications` via `runCatching` or `try/catch`.
- Filesystem writes initiated by the IDE UI belong in `WriteCommandAction`, followed by `LocalFileSystem` refresh and optional editor opening.
- Preserve path-confinement, ZIP-slip prevention, filename sanitization, SHA-256 verification, and atomic cache writes. Do not bypass these for convenience.
- Dependency injection is mostly explicit constructor/function input plus IntelliJ application/project services (`@Service`). Persistent user defaults use application-level `PersistentStateComponent` in `EgovSettings`.
- The UI is Swing/IntelliJ UI DSL and synchronous; there is no coroutine architecture. CRUD input validation uses a 500 ms Swing timer debounce.
- Treat catalog keys, template field names, generated paths, and output bytes as compatibility contracts. Do not rename upstream-derived fields or “fix” pinned quirks without updating fixtures and parity tests.
- Maven classes must remain behind the optional Maven descriptor so the core plugin still loads without Maven support.

## Important Files

- `build.gradle.kts`: dependencies, IntelliJ target, verifier/signing/publishing, JDK toolchain.
- `settings.gradle.kts`, `gradle.properties`: plugin/tool versions and repository configuration.
- `src/main/resources/META-INF/plugin.xml`: extension points, dependencies, tool window, wizard, settings, actions.
- `src/main/kotlin/kr/kyg/intellij/plugin/egovframe/project/EgovProjectWizard.kt`: project wizard entry point.
- `src/main/kotlin/kr/kyg/intellij/plugin/egovframe/config/ConfigGenerator.kt`: config form, validation, render, and write contract.
- `src/main/kotlin/kr/kyg/intellij/plugin/egovframe/crud/CrudGenerator.kt`: immutable CRUD context and 11-file rendering contract.
- `src/main/kotlin/kr/kyg/intellij/plugin/egovframe/render/EgovHandlebars.kt`: JavaScript parity helpers and normalized-template lookup.
- `src/main/kotlin/kr/kyg/intellij/plugin/egovframe/assets/TemplateStore.kt`: cache, download, hash, and atomic-write rules.
- `src/main/resources/egovframe/asset-manifest.json`: pinned asset inventory and ZIP metadata.
- `src/test/resources/golden/index.json`: fixture inventory used by renderer and CRUD-context parity tests.
- `NOTICE`: upstream provenance, licenses, pinned commit, and normalized-asset explanation.

## Runtime/Tooling Preferences

- **JDK:** 21 for building and CI. Generated eGovFrame projects prefer an installed JDK 17.
- **Gradle:** wrapper-pinned 9.5.0; do not depend on a system Gradle installation.
- **Kotlin:** 2.1.20. The plugin relies on the IntelliJ-bundled Kotlin stdlib.
- **IntelliJ Platform:** IDEA Community 2025.1.6, `sinceBuild = 251`, with no upper bound.
- **Rendering:** Handlebars.java 4.5.3 at runtime; Handlebars.js 4.7.9 is pinned only for parity generation.
- **Golden tooling:** Node.js 24 LTS with npm 11. TypeScript 7.0.2, `@types/node` 24.13.3, and tsx 4.23.1 are pinned in `tools/golden-gen/package.json`; use `npm ci` and do not casually update `package-lock.json`.
- **Maven:** optional at plugin runtime. Keep integration in `egovframe-maven.xml`.
- The fixed plugin ID and Marketplace name intentionally mute `TemplateWordInPluginId` and `TemplateWordInPluginName` in Plugin Verifier. Do not change these identifiers casually.

The submodule must be initialized before building. Git LFS smudge should stay disabled except for the two bundled ZIPs:

```bash
git submodule update --init
git -C vendor/egovframe-vscode-initializr lfs pull --include="templates/projects/examples/egovframe-boot-web.zip,templates/projects/examples/egovframe-boot-simple-backend.zip"
```

Manifest and derivative regeneration rewrites committed resources. Run from `tools/golden-gen/` only when relevant
inputs change:

```bash
npm ci
npm run refresh                # rebuild manifest, golden fixtures, defaults, and normalized templates from submodule
npm run manifest               # rebuild asset-manifest.json from submodule and Git LFS pointers
npm run generate               # golden fixtures + normalized templates
npm run generate:golden        # fixtures/default form data only
npm run generate:normalized    # normalized Handlebars resources only
```

Generated golden files, `normalized/` runtime resources, and `handlebars-normalized.properties` are committed; CI
consumes them without Node.js. Normalized runtime resources use `<template-base>@<source-sha256>.hbs`; the properties
index maps the full source digest to that readable path. Do not replace this with opaque hash-only filenames.

## Testing & QA

Tests use JUnit Jupiter 5.14.4. IntelliJ 2025.1's JUnit 5 environment still requires JUnit 4.13.2 at runtime only (IJPL-159134); test sources must not use JUnit 4 APIs. There is no configured coverage threshold; protect observable contracts instead.

- Renderer/template/helper/normalization changes: run `GoldenRenderTest` and `HandlebarsSyntaxTest`.
- DDL/type/ERD changes: run parser tests plus `CrudContextEquivalenceTest` and CRUD renderer tests.
- Config generator/form/default changes: run `ConfigGeneratorTest` and `GoldenRenderTest`.
- CRUD path/context/write changes: run `CrudGeneratorTest`, `CrudContextEquivalenceTest`, and relevant golden tests.
- Project ZIP/POM/path validation changes: run `ProjectGeneratorTest`.
- Catalog/manifest/resource/cache changes: run `AssetIntegrityTest`, `TemplateCatalogTest`, and `TemplateStoreTest`.
- Before delivery: run the full CI gate and, for UI behavior, smoke-test with `runIde`.

`GoldenRenderTest` is byte-for-byte, not semantic. If output changes intentionally, regenerate with the pinned JavaScript tooling and review the resulting fixture diff. Preserve the known upstream catalog edge where `timeBasedRollingFile-java.hbs` is referenced but absent; tests intentionally document it.
