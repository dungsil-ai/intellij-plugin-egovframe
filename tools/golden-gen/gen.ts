#!/usr/bin/env node
/**
 * Golden fixture generator for intellij-plugin-egovframe tests.
 * Run: npm run generate:golden
 */
import Handlebars from 'handlebars'
import * as fs from 'fs'
import * as path from 'path'
import { parseDDL } from '../../vendor/egovframe-vscode-initializr/src/shared/ddlParser'
import { getTemplateContext } from '../../vendor/egovframe-vscode-initializr/src/shared/templateContext'

// ─── paths ───────────────────────────────────────────────────────────
const ROOT = path.resolve(__dirname, '../..')
const UPSTREAM_DIR = path.join(ROOT, 'vendor/egovframe-vscode-initializr')
const CODE_TPL_DIR = path.join(UPSTREAM_DIR, 'templates/code')
const CONFIG_TPL_DIR = path.join(UPSTREAM_DIR, 'templates/config')
const GOLDEN_DIR = path.join(ROOT, 'src/test/resources/golden')
const CATALOG_PATH = path.join(UPSTREAM_DIR, 'templates/templates-context-xml.json')

// ─── helpers ─────────────────────────────────────────────────────────
function mkdirp(dir: string) {
  fs.mkdirSync(dir, { recursive: true })
}

function writeFile(p: string, content: string) {
  mkdirp(path.dirname(p))
  fs.writeFileSync(p, content, 'utf-8')
}

function readFile(p: string): string {
  return fs.readFileSync(p, 'utf-8')
}

// =====================================================================
//  CRUD helpers – verbatim from upstream codeGenerator.ts
// =====================================================================
function registerCrudHelpers() {
  Handlebars.registerHelper('error', function (message: any) {
    console.error(message)
    return new Handlebars.SafeString(`<span class="error">${message}</span>`)
  })

  Handlebars.registerHelper('empty', function (value: any) {
    return value === null || value === ''
  })

  Handlebars.registerHelper('eq', function (a: any, b: any) {
    return a === b
  })

  Handlebars.registerHelper('concat', function (...args: any[]) {
    return args.slice(0, -1).join('')
  })

  Handlebars.registerHelper('setVar', function (varName: any, varValue: any, options: any) {
    options.data.root[varName] = varValue
  })

  Handlebars.registerHelper('lowercase', function (str: any) {
    if (typeof str !== 'string') {
      return ''
    }
    return str.toLowerCase()
  })

  Handlebars.registerHelper('unless', function (this: any, conditional: any, options: any) {
    if (!conditional) {
      return options.fn(this)
    } else {
      return options.inverse(this)
    }
  })

  Handlebars.registerHelper('add', function (a: any, b: any) {
    return (Number(a) || 0) + (Number(b) || 0)
  })
}

// =====================================================================
//  CONFIG helpers – verbatim from upstream configGenerator.ts
// =====================================================================
function registerConfigHelpers() {
  // Re-register eq (same logic, safe to overwrite)
  Handlebars.registerHelper('eq', function (a: any, b: any) {
    return a === b
  })

  Handlebars.registerHelper('ne', function (a: any, b: any) {
    return a !== b
  })

  Handlebars.registerHelper('capitalize', function (str: string) {
    if (!str) {
      return ''
    }
    return str.charAt(0).toUpperCase() + str.slice(1)
  })

  Handlebars.registerHelper('trim', (v: any) => String(v ?? '').trim())

  Handlebars.registerHelper('or', function () {
    const args = Array.from(arguments)
    const opts = args.pop() // last arg = options
    return args.some((v: any) => !!v) // any truthy → true
  })
}

// =====================================================================
//  CONFIG renderTemplate – mirrors upstream configGenerator.ts
// =====================================================================
async function renderConfigTemplate(templateFilePath: string, context: any): Promise<string> {
  const defaultPackageName = 'egovframework.example.sample'

  const enrichedContext = {
    ...context,
    defaultPackageName,
  }

  let template = readFile(templateFilePath)

  // Handle #parse directives (simple include mechanism)
  const parseRegex = /#parse\("(.+)"\)/g
  let match
  while ((match = parseRegex.exec(template)) !== null) {
    const includeFilePath = path.join(path.dirname(templateFilePath), match[1])
    if (fs.existsSync(includeFilePath)) {
      const includeTemplate = readFile(includeFilePath)
      template = template.replace(match[0], includeTemplate)
    }
  }

  const compiledTemplate = Handlebars.compile(template)
  return compiledTemplate(enrichedContext)
}

// =====================================================================
//  CRUD section
// =====================================================================
const CRUD_TEMPLATES = [
  'sample-vo-template.hbs',
  'sample-default-vo-template.hbs',
  'sample-controller-template.hbs',
  'sample-service-template.hbs',
  'sample-service-impl-template.hbs',
  'sample-mapper-interface-template.hbs',
  'sample-mapper-template.hbs',
  'sample-thymeleaf-list.hbs',
  'sample-thymeleaf-register.hbs',
  'sample-jsp-list.hbs',
  'sample-jsp-register.hbs',
]

const CRUD_CASES: Record<string, string> = {
  single_pk: `
    CREATE TABLE board_article
    (
      article_id  INT PRIMARY KEY,
      title       VARCHAR(255) NOT NULL COMMENT '제목',
      content     TEXT COMMENT '본문 내용',
      view_count  INT     DEFAULT 0,
      created_at  DATETIME     NOT NULL COMMENT '작성일시',
      price       DECIMAL(10, 2) COMMENT '가격',
      use_yn      CHAR(1) DEFAULT 'Y' COMMENT '사용여부',
      writer_name VARCHAR(255)
    ) COMMENT='게시판';
  `,
  composite_pk: `
    CREATE TABLE order_item
    (
      order_id     INT          NOT NULL,
      item_seq     INT          NOT NULL,
      product_name VARCHAR(255) NOT NULL COMMENT '상품명',
      quantity     INT DEFAULT 1,
      unit_price   DECIMAL(10, 2),
      addr_1       VARCHAR(255) COMMENT '주소1',
      addr_2       VARCHAR(255) COMMENT '주소2',
      PRIMARY KEY (order_id, item_seq)
    );
  `,
  no_pk_two_cols: `
    CREATE TABLE tmp_log
    (
      log_message TEXT,
      logged_at   DATETIME
    );
  `,
  special_chars: `
    CREATE TABLE \`event_info\`
    (
      \`event_id\`    INT PRIMARY KEY COMMENT 'Event''s ID & <identifier>',
      \`event_name\`  VARCHAR(255) NOT NULL COMMENT 'Name with ''quotes'' & <angle> brackets',
      \`description\` TEXT COMMENT 'A & B < C'
    ) COMMENT='이벤트 정보';
  `,
}

async function generateCrud() {
  console.log('=== Generating CRUD golden fixtures ===')
  registerCrudHelpers()

  // Pre-compile all templates
  const compiled: Record<string, HandlebarsTemplateDelegate> = {}
  for (const tpl of CRUD_TEMPLATES) {
    const src = readFile(path.join(CODE_TPL_DIR, tpl))
    compiled[tpl] = Handlebars.compile(src)
  }

  for (const [caseName, ddl] of Object.entries(CRUD_CASES)) {
    const parsed = parseDDL(ddl)
    const { tableName, dbTableName, attributes, pkAttributes } = parsed
    const ctx: any = {
      ...getTemplateContext(tableName, attributes, pkAttributes, 'egovframework.example.sample', dbTableName),
      packagePath: 'egovframework/example/sample',
    }
    ctx.date = '2026-07-16'

    const caseDir = path.join(GOLDEN_DIR, 'crud', caseName)
    const outDir = path.join(caseDir, 'rendered')
    mkdirp(outDir)

    // Write DDL
    writeFile(path.join(caseDir, 'ddl.sql'), ddl.trim() + '\n')

    // Write context
    writeFile(path.join(caseDir, 'context.json'), JSON.stringify(ctx, null, 2) + '\n')

    // Render templates
    for (const tpl of CRUD_TEMPLATES) {
      const rendered = compiled[tpl](ctx)
      const basename = tpl.replace(/\.hbs$/, '')
      writeFile(path.join(outDir, `${basename}.golden`), rendered)
    }
    console.log(`  [CRUD] ${caseName}: ddl.sql + context.json + ${CRUD_TEMPLATES.length} golden files`)
  }
}

// =====================================================================
//  CONFIG section
// =====================================================================
interface CatalogEntry {
  displayName: string;
  templateFolder: string;
  templateFile: string;
  webView: string;
  fileNameProperty: string;
  javaConfigTemplate: string;
  yamlTemplate: string;
  propertiesTemplate: string;
  description?: string;
}

function slugify(displayName: string): string {
  return displayName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

// Form default data transcribed from upstream React forms
function getFormDefaults(entry: CatalogEntry): Record<string, any> {
  const wv = entry.webView

  // --- Cache ---
  if (wv.includes('cache-cache')) {
    return {
      generationType: 'xml',
      txtFileName: 'ehcache-default',
      txtDiskStore: 'user.dir/second',
      txtDftEternal: 'false',
      txtDftLiveTime: '10',
      txtDftHeapEntries: '100',
      txtDftOffheapSize: '',
      txtDftDiskPersistence: 'true',
      txtCacheName: 'cache',
      txtEternal: 'false',
      txtIdleTime: '300',
      txtHeapEntries: '10',
    }
  }

  // --- Ehcache ---
  if (wv.includes('cache-ehcacheConfig')) {
    return {
      generationType: 'xml',
      txtConfigPackage: 'egovframework.example.config',
      txtComponentScanBasePackage: 'egovframework.example',
      txtFileName: 'context-cache',
      txtConfigLocation: '',
    }
  }

  // --- Datasource ---
  if (wv.includes('datasource-datasource')) {
    return {
      generationType: 'xml',
      txtConfigPackage: 'egovframework.example.config',
      txtFileName: 'context-datasource',
      txtDatasourceName: 'dataSource',
      rdoType: 'DBCP',
      txtDriver: 'com.mysql.cj.jdbc.Driver',
      txtUrl: 'jdbc:mysql://127.0.0.1:3306/myDB',
      txtUser: 'root',
      txtPasswd: '',
    }
  }

  // --- JNDI Datasource ---
  if (wv.includes('datasource-jndiDatasource')) {
    return {
      generationType: 'xml',
      txtConfigPackage: 'egovframework.example.config',
      txtFileName: 'context-jndi-datasource',
      txtDatasourceName: 'dataSource',
      txtJndiName: 'java:comp/env/jdbc/myDataSource',
    }
  }

  // --- ID Generation (sequence / table / uuid) ---
  if (wv.includes('id-gnr-')) {
    const formType = wv.includes('sequence') ? 'sequence'
      : wv.includes('table') ? 'table'
        : 'uuid'

    const baseNames: Record<string, Record<string, string>> = {
      sequence: { xml: 'context-idgn-sequence', java: 'EgovIdgnSequenceConfig' },
      table: { xml: 'context-idgn-table', java: 'EgovIdgnTableConfig' },
      uuid: { xml: 'context-idgn-uuid', java: 'EgovIdgnUuidConfig' },
    }

    return {
      generationType: 'xml',
      txtConfigPackage: 'egovframework.example.config',
      txtFileName: baseNames[formType].xml,
      txtIdServiceName: 'idGnrService',
      txtDatasourceName: 'dataSource',
      rdoIdType: 'Base',
      txtQuery: 'SELECT SEQ_SAMPLE.NEXTVAL FROM DUAL',
      txtTable: 'IDS',
      txtTableNameFieldValue: 'SAMPLE',
      txtBlockSize: '10',
      chkStrategy: false,
      txtStrategyName: 'prefixIdGnrStrategy',
      txtPrefix: 'egov-',
      txtCipers: '10',
      txtFillChar: '0',
      txtAddress: '12:34:56:78:9A:AB',
      _formType: formType,
      _javaFileName: baseNames[formType].java,
    }
  }

  // --- Logging (console/file/rollingFile/timeBasedRollingFile/jdbc) ---
  if (wv.includes('logging-')) {
    const formType = wv.includes('console') ? 'console'
      : wv.includes('file') && !wv.includes('rolling') && !wv.includes('Rolling') ? 'file'
        : wv.includes('rollingFile') && !wv.includes('timeBased') && !wv.includes('time') ? 'rollingFile'
          : wv.includes('timeBasedRollingFile') ? 'timeBasedRollingFile'
            : wv.includes('jdbc') ? 'jdbc'
              : 'console'

    const fileNameMap: Record<string, Record<string, string>> = {
      console: { xml: 'log4j2-console', java: 'EgovLog4j2ConsoleConfig' },
      file: { xml: 'log4j2-file', java: 'EgovLog4j2FileConfig' },
      rollingFile: { xml: 'log4j2-rollingFile', java: 'EgovLog4j2RollingFileConfig' },
      timeBasedRollingFile: { xml: 'log4j2-timeBasedRollingFile', java: 'EgovLog4j2TimeBasedRollingFileConfig' },
      jdbc: { xml: 'log4j2-jdbc', java: 'EgovLog4j2JdbcConfig' },
    }

    const appenderNames: Record<string, string> = {
      console: 'console',
      file: 'file',
      rollingFile: 'rolling-file',
      timeBasedRollingFile: 'rolling-file-time',
      jdbc: 'jdbc',
    }

    const logFileNames: Record<string, string> = {
      file: './logs/file/sample.log',
      rollingFile: './logs/rolling/rollingSample.log',
      timeBasedRollingFile: './logs/time/timeBasedRollingSample.log',
    }

    const logFilePatterns: Record<string, string> = {
      rollingFile: './logs/rolling/rollingSample.%i.log',
      timeBasedRollingFile: './logs/time/timeBasedRollingSample.%d{yyyy-MM-dd_HH-mm}.log',
    }

    return {
      generationType: 'xml',
      txtConfigPackage: 'egovframework.example.config',
      txtFileName: fileNameMap[formType].xml,
      txtAppenderName: appenderNames[formType],
      txtConversionPattern: '%d %5p [%c] %m%n',
      txtLogFileName: logFileNames[formType] || '',
      cboAppend: true,
      txtLogFileNamePattern: logFilePatterns[formType] || '',
      txtMaxIndex: '20',
      txtMaxFileSize: '3000',
      txtInterval: '1',
      cboModulate: true,
      txtTableName: 'LOG',
      rdoConnectionType: 'DriverManager',
      txtDriver: 'com.mysql.cj.jdbc.Driver',
      txtUrl: 'jdbc:mysql://localhost:3306/log',
      txtUser: 'log',
      txtPasswrd: 'log01',
      txtConnectionFactoryClass: 'org.egovframe.rte.fdl.logging.db.EgovConnectionFactory',
      txtConnectionFactoryMethod: 'getDatabaseConnection',
      _formType: formType,
      _javaFileName: fileNameMap[formType].java,
    }
  }

  // --- Property ---
  if (wv.includes('property-')) {
    return {
      generationType: 'xml',
      txtConfigPackage: 'egovframework.example.config',
      txtFileName: 'context-properties',
      txtPropertyServiceName: 'propertiesService',
      rdoType: 'Internal Properties',
      txtKey: 'pageUnit',
      txtValue: '20',
      cboEncoding: 'UTF-8',
      txtPropertyFile: 'classpath*:/egovframework/egovProps/conf/config.properties',
    }
  }

  // --- Scheduling ---
  if (wv.includes('scheduling-')) {
    const formType = wv.includes('beanJob') ? 'beanJob'
      : wv.includes('methodJob') ? 'methodJob'
        : wv.includes('simpleTrigger') ? 'simpleTrigger'
          : wv.includes('cronTrigger') ? 'cronTrigger'
            : wv.includes('scheduler') ? 'scheduler'
              : 'beanJob'

    const fileNameMap: Record<string, Record<string, string>> = {
      beanJob: { xml: 'context-scheduling-jobDetail', java: 'EgovSchedulingJobDetailConfig' },
      methodJob: {
        xml: 'context-scheduling-methodInvokingJobDetail',
        java: 'EgovSchedulingMethodInvokingJobDetailConfig',
      },
      simpleTrigger: { xml: 'context-scheduling-simpleTrigger', java: 'EgovSchedulingSimpleTriggerConfig' },
      cronTrigger: { xml: 'context-scheduling-cronTrigger', java: 'EgovSchedulingCronTriggerConfig' },
      scheduler: { xml: 'context-scheduling-scheduler', java: 'EgovSchedulingSchedulerConfig' },
    }

    const jobNames: Record<string, string> = {
      beanJob: 'jobDetail',
      methodJob: 'methodInvokingJobDetail',
      simpleTrigger: 'jobDetail',
      cronTrigger: 'jobDetail',
    }

    const triggerNames: Record<string, string> = {
      simpleTrigger: 'simpleTrigger',
      cronTrigger: 'cronTrigger',
      scheduler: 'simpleTrigger',
    }

    return {
      generationType: 'xml',
      txtConfigPackage: 'egovframework.example.config',
      txtFileName: fileNameMap[formType].xml,
      txtJobName: jobNames[formType] || '',
      txtServiceClass: 'egovframework.example.schedule.JobService',
      chkProperty: true,
      txtPropertyName: 'paramSampleJob',
      txtPropertyValue: 'SampleJobValue',
      txtServiceName: 'jobService',
      txtServiceMethod: 'executeSample',
      cboConcurrent: 'false',
      cboJobDetailType: 'JobDetailFactoryBean',
      txtTriggerName: triggerNames[formType] || '',
      txtStartDelay: '2000',
      txtRepeatInterval: '10000',
      txtCronExpression: '*/10 * * * * ?',
      txtSchedulerName: 'scheduler',
      cboTriggerType: 'SimpleTriggerFactoryBean',
      _formType: formType,
      _javaFileName: fileNameMap[formType].java,
    }
  }

  // --- Transaction ---
  if (wv.includes('transaction-')) {
    const formType = wv.includes('transaction-datasource') ? 'datasource'
      : wv.includes('jpa') ? 'jpa'
        : wv.includes('jta') ? 'jta'
          : 'datasource'

    const fileNameMap: Record<string, Record<string, string>> = {
      datasource: { xml: 'context-transaction', java: 'EgovTransactionConfig' },
      jpa: { xml: 'context-transaction-jpa', java: 'EgovTransactionJpaConfig' },
      jta: { xml: 'context-transaction-jta', java: 'EgovTransactionJtaConfig' },
    }

    return {
      generationType: 'xml',
      txtConfigPackage: 'egovframework.example.config',
      txtFileName: fileNameMap[formType].xml,
      txtTransactionTemplateName: 'transactionTemplate',
      txtTransactionName: formType === 'datasource' ? 'txManager' : 'transactionManager',
      txtDataSourceName: 'dataSource',
      chkAopConfigTransaction: true,
      chkAnnotationTransaction: true,
      txtEntityManagerFactory: 'entityManagerFactory',
      txtPackagesToScan: 'egovframework.example.sample.domain',
      cmbDialectName: 'org.hibernate.dialect.H2Dialect',
      txtSpringDataJpaRepositoriesPackage: 'egovframework.example.sample.repository',
      txtJtaImplementation: 'Atomikos',
      txtGlobalTimeout: '20',
      txtPointCutName: 'requiredTx',
      txtPointCutExpression: 'execution(* egovframework.example..*Impl.*(..)) || execution(* org.egovframe.rte.fdl.excel.impl.*Impl.*(..))',
      txtAdviceName: 'txAdvice',
      txtMethodName: '*',
      chkReadOnly: false,
      txtRollbackFor: 'Exception',
      txtNoRollbackFor: 'RuntimeException',
      txtTimeout: '20',
      cmbPropagation: 'REQUIRED',
      cmbIsolation: 'DEFAULT',
      _formType: formType,
      _javaFileName: fileNameMap[formType].java,
    }
  }

  throw new Error(`Unknown webView: ${wv}`)
}

// Determine which variants exist for an entry
interface VariantInfo {
  variant: string; // xml | javaConfig | yaml | properties
  templateFile: string;
  extension: string; // for golden file naming
}

function getVariants(entry: CatalogEntry): VariantInfo[] {
  const variants: VariantInfo[] = []
  // xml is always present
  variants.push({ variant: 'xml', templateFile: entry.templateFile, extension: 'xml' })
  if (entry.javaConfigTemplate) {
    variants.push({ variant: 'java', templateFile: entry.javaConfigTemplate, extension: 'java' })
  }
  if (entry.yamlTemplate) {
    variants.push({ variant: 'yaml', templateFile: entry.yamlTemplate, extension: 'yaml' })
  }
  if (entry.propertiesTemplate) {
    variants.push({ variant: 'properties', templateFile: entry.propertiesTemplate, extension: 'properties' })
  }
  return variants
}

// Alt context definitions
interface AltContextDef {
  typeSlug: string; // matches a slugified displayName
  contextName: string;
  overrides: Record<string, any>;
}

const ALT_CONTEXTS: AltContextDef[] = [
  // datasource rdoType=C3P0
  {
    typeSlug: 'datasource-new-datasource',
    contextName: 'alt-c3p0',
    overrides: { rdoType: 'C3P0' },
  },
  // datasource rdoType=JDBC
  {
    typeSlug: 'datasource-new-datasource',
    contextName: 'alt-jdbc',
    overrides: { rdoType: 'JDBC' },
  },
  // property rdoType='External File'
  {
    typeSlug: 'property-new-property',
    contextName: 'alt-external-file',
    overrides: { rdoType: 'External File' },
  },
  // idGeneration-table chkStrategy=true
  {
    typeSlug: 'id-generation-new-table-id-generation',
    contextName: 'alt-strategy',
    overrides: { chkStrategy: true },
  },
  // logging-jdbc rdoConnectionType='ConnectionFactory'
  {
    typeSlug: 'logging-new-jdbc-appender',
    contextName: 'alt-connection-factory',
    overrides: { rdoConnectionType: 'ConnectionFactory' },
  },
  // idGeneration-uuid rdoIdType='Address'
  {
    typeSlug: 'id-generation-new-uuid-generation',
    contextName: 'alt-address',
    overrides: { rdoIdType: 'Address' },
  },
  // transaction-datasource chkAopConfigTransaction=true/chkAnnotationTransaction=false
  {
    typeSlug: 'transaction-new-datasource-transaction',
    contextName: 'alt-aop-only',
    overrides: { chkAopConfigTransaction: true, chkAnnotationTransaction: false },
  },
  // transaction-datasource chkAopConfigTransaction=false/chkAnnotationTransaction=true
  {
    typeSlug: 'transaction-new-datasource-transaction',
    contextName: 'alt-annotation-only',
    overrides: { chkAopConfigTransaction: false, chkAnnotationTransaction: true },
  },
]

// Get java-specific fileName & configPackage for a formData
function getJavaOverrides(formData: Record<string, any>): Record<string, any> {
  const overrides: Record<string, any> = { generationType: 'javaConfig' }
  // Set java fileName
  if (formData._javaFileName) {
    overrides.txtFileName = formData._javaFileName
  }
  // Set configPackage
  if (formData.txtConfigPackage) {
    overrides.txtConfigPackage = formData.txtConfigPackage
  }
  return overrides
}

async function generateConfig() {
  console.log('\n=== Generating CONFIG golden fixtures ===')
  registerConfigHelpers()

  const catalog: CatalogEntry[] = JSON.parse(readFile(CATALOG_PATH))
  const indexEntries: any[] = []
  const runtimeDefaults: Record<string, Record<string, any>> = {}
  for (const entry of catalog) {
    runtimeDefaults[entry.displayName] = getFormDefaults(entry)
  }
  writeFile(
    path.join(ROOT, 'src/main/resources/egovframe/config-defaults.json'),
    JSON.stringify(runtimeDefaults, null, 2) + '\n',
  )
  let totalGolden = 0

  // Track missing template files
  const missingTemplates: string[] = []

  for (const entry of catalog) {
    const typeSlug = slugify(entry.displayName)
    const defaults = getFormDefaults(entry)
    const variants = getVariants(entry)

    // Check which variant templates actually exist
    const validVariants = variants.filter(v => {
      const tplPath = path.join(CONFIG_TPL_DIR, entry.templateFolder, v.templateFile)
      if (!fs.existsSync(tplPath)) {
        missingTemplates.push(`${entry.templateFolder}/${v.templateFile}`)
        return false
      }
      return true
    })

    // --- default context ---
    const contexts: { name: string; formData: Record<string, any> }[] = [
      { name: 'default', formData: { ...defaults } },
    ]

    // --- alt contexts ---
    for (const alt of ALT_CONTEXTS) {
      if (alt.typeSlug === typeSlug) {
        contexts.push({
          name: alt.contextName,
          formData: { ...defaults, ...alt.overrides },
        })
      }
    }

    for (const ctx of contexts) {
      const templates: Record<string, string> = {}
      const outputs: Record<string, string> = {}
      const variantOverrides: Record<string, Record<string, any>> = {}
      for (const v of validVariants) {
        let overrides: Record<string, any>
        if (v.variant === 'java') {
          overrides = getJavaOverrides(ctx.formData)
        } else if (v.variant === 'yaml') {
          overrides = { generationType: 'yaml' }
        } else if (v.variant === 'properties') {
          overrides = { generationType: 'properties' }
        } else {
          overrides = { generationType: 'xml' }
        }
        const formData = { ...ctx.formData, ...overrides }
        variantOverrides[v.variant] = overrides

        const tplPath = path.join(CONFIG_TPL_DIR, entry.templateFolder, v.templateFile)
        const rendered = await renderConfigTemplate(tplPath, formData)

        const contextDir = path.join(GOLDEN_DIR, 'config', typeSlug, ctx.name)
        mkdirp(contextDir)

        // Write formData.json (only once per context, not per variant)
        const formDataPath = path.join(contextDir, 'formData.json')
        if (!fs.existsSync(formDataPath)) {
          // Strip internal fields
          const cleanData = { ...ctx.formData }
          delete cleanData._formType
          delete cleanData._javaFileName
          writeFile(formDataPath, JSON.stringify(cleanData, null, 2) + '\n')
        }

        // Write golden
        const goldenFile = `${v.variant}.golden`
        writeFile(path.join(contextDir, goldenFile), rendered)
        totalGolden++

        templates[v.variant] = `${entry.templateFolder}/${v.templateFile}`
        outputs[v.variant] = `golden/config/${typeSlug}/${ctx.name}/${goldenFile}`
      }
      indexEntries.push({
        slug: typeSlug,
        displayName: entry.displayName,
        templateFolder: entry.templateFolder,
        contextName: ctx.name,
        formData: `golden/config/${typeSlug}/${ctx.name}/formData.json`,
        templates,
        outputs,
        variantOverrides,
      })
    }

    console.log(`  [CONFIG] ${typeSlug}: ${contexts.length} context(s) × ${validVariants.length} variant(s)`)
  }

  if (missingTemplates.length > 0) {
    console.log(`\n  [WARN] Missing template files (skipped):`)
    for (const m of missingTemplates) {
      console.log(`    - ${m}`)
    }
  }

  return { indexEntries, totalGolden }
}

// =====================================================================
//  CRUD index entries
// =====================================================================
function getCrudIndexEntries(): any[] {
  const entries: any[] = []
  for (const caseName of Object.keys(CRUD_CASES)) {
    const outputs: Record<string, string> = {}
    for (const templateFile of CRUD_TEMPLATES) {
      const basename = templateFile.replace(/\.hbs$/, '')
      outputs[templateFile] = `golden/crud/${caseName}/rendered/${basename}.golden`
    }
    entries.push({
      case: caseName,
      ddl: `golden/crud/${caseName}/ddl.sql`,
      context: `golden/crud/${caseName}/context.json`,
      outputs,
    })
  }
  return entries
}

// =====================================================================
//  Check for 'undefined' in generated files
// =====================================================================
function checkUndefined(): string[] {
  const issues: string[] = []

  function walk(dir: string) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      const fullPath = path.join(dir, entry.name)
      if (entry.isDirectory()) {
        walk(fullPath)
      } else if (entry.name.endsWith('.golden') || entry.name === 'context.json' || entry.name === 'formData.json') {
        const content = readFile(fullPath)
        if (content.includes('undefined')) {
          issues.push(path.relative(GOLDEN_DIR, fullPath))
        }
      }
    }
  }

  if (fs.existsSync(GOLDEN_DIR)) walk(GOLDEN_DIR)
  return issues
}

// =====================================================================
//  Main
// =====================================================================
async function main() {
  // Clean golden dir
  if (fs.existsSync(GOLDEN_DIR)) {
    fs.rmSync(GOLDEN_DIR, { recursive: true })
  }

  await generateCrud()
  const { indexEntries: configIndex, totalGolden: configGoldenCount } = await generateConfig()
  const crudIndex = getCrudIndexEntries()

  // Write index.json
  const index = {
    crud: crudIndex,
    config: configIndex,
  }
  writeFile(path.join(GOLDEN_DIR, 'index.json'), JSON.stringify(index, null, 2) + '\n')

  // Summary
  console.log('\n=== Summary ===')
  console.log(`CRUD: ${Object.keys(CRUD_CASES).length} cases × (ddl.sql + context.json + ${CRUD_TEMPLATES.length} golden)`)
  console.log(`CONFIG: ${configGoldenCount} .golden files`)

  // Undefined check
  const undefinedIssues = checkUndefined()
  if (undefinedIssues.length > 0) {
    console.error(`\n[ERROR] Files containing 'undefined':`)
    for (const f of undefinedIssues) {
      console.error(`  - ${f}`)
    }
    process.exit(1)
  } else {
    console.log(`\n[OK] No 'undefined' found in any generated file.`)
  }
}

main().catch(err => {
  console.error(err)
  process.exit(1)
})
