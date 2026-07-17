package kr.kyg.ijplugin.egovframe.crud

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

internal data class CrudAuxiliaryFile(
  val target: Path,
  val content: String,
)

internal data class CrudAuxiliaryWriteResult(
  val written: List<Path>,
  val overwritten: List<Path>,
  val cleanupFailures: List<Path>,
)

internal fun interface CrudAuxiliaryWriter {
  fun write(files: List<CrudAuxiliaryFile>): CrudAuxiliaryWriteResult
}

internal class TransactionalCrudAuxiliaryWriter(
  private val fileOps: CrudFileOps = NioCrudFileOps,
) : CrudAuxiliaryWriter {
  override fun write(files: List<CrudAuxiliaryFile>): CrudAuxiliaryWriteResult {
    require(files.isNotEmpty()) { "Select at least one auxiliary output" }
    val normalized = files.map { it.copy(target = it.target.toAbsolutePath().normalize()) }
    require(normalized.map(CrudAuxiliaryFile::target).distinct().size == normalized.size) {
      "Auxiliary output targets must be unique"
    }
    normalized.forEach(::validate)

    val stages = linkedMapOf<Path, Path>()
    val backups = linkedMapOf<Path, Path>()
    val attempted = mutableListOf<CrudAuxiliaryFile>()
    try {
      for (file in normalized) {
        val stage = fileOps.createTempFile(file.target.parent, ".egov-aux-", ".stage")
        stages[file.target] = stage
        fileOps.writeString(stage, file.content)
      }
      for (file in normalized) {
        validate(file)
        if (fileOps.exists(file.target)) {
          val backup = fileOps.createTempFile(file.target.parent, ".egov-aux-", ".backup")
          backups[file.target] = backup
          fileOps.copyReplacing(file.target, backup)
        }
        attempted += file
        fileOps.moveReplacing(stages.getValue(file.target), file.target)
      }
    } catch (error: Exception) {
      rollback(error, attempted, stages, backups)
      throw error
    }

    val cleanupFailures = mutableListOf<Path>()
    (stages.values + backups.values).forEach { path ->
      try {
        fileOps.deleteIfExists(path)
      } catch (_: Exception) {
        cleanupFailures.add(path)
      }
    }
    return CrudAuxiliaryWriteResult(
      written = normalized.map(CrudAuxiliaryFile::target),
      overwritten = normalized.filter { it.target in backups }.map(CrudAuxiliaryFile::target),
      cleanupFailures = cleanupFailures,
    )
  }

  private fun validate(file: CrudAuxiliaryFile) {
    val parent = file.target.parent ?: throw IllegalArgumentException("Auxiliary output must have a parent directory")
    require(fileOps.exists(parent) && fileOps.isDirectory(parent)) { "Auxiliary output parent is not a directory: $parent" }
    var ancestor: Path? = parent
    while (ancestor != null && fileOps.exists(ancestor)) {
      require(!fileOps.isSymbolicLink(ancestor)) {
        "Auxiliary output parent resolves through a symbolic link: $ancestor"
      }
      ancestor = ancestor.parent
    }
    fileOps.toRealPath(parent)
    if (fileOps.exists(file.target)) {
      require(!fileOps.isSymbolicLink(file.target)) { "Auxiliary output must not be a symbolic link: ${file.target}" }
      require(fileOps.isRegularFile(file.target)) { "Auxiliary output is not a regular file: ${file.target}" }
    }
  }

  private fun rollback(
    originalError: Exception,
    attempted: List<CrudAuxiliaryFile>,
    stages: Map<Path, Path>,
    backups: Map<Path, Path>,
  ) {
    val preservedBackups = mutableSetOf<Path>()
    attempted.asReversed().forEach { file ->
      val backup = backups[file.target]
      try {
        if (backup != null) {
          fileOps.moveReplacing(backup, file.target)
        } else if (fileOps.exists(file.target)) {
          fileOps.deleteIfExists(file.target)
        }
      } catch (restoreError: Exception) {
        if (backup != null) preservedBackups.add(backup)
        originalError.addSuppressed(
          IOException("Failed to restore auxiliary output; backup preserved at $backup", restoreError),
        )
      }
    }
    stages.values.forEach { deleteDuringRollback(it, originalError) }
    backups.values.filterNot(preservedBackups::contains).forEach { deleteDuringRollback(it, originalError) }
  }

  private fun deleteDuringRollback(path: Path, originalError: Exception) {
    try {
      fileOps.deleteIfExists(path)
    } catch (cleanupError: Exception) {
      originalError.addSuppressed(IOException("Failed to clean up auxiliary temporary path: $path", cleanupError))
    }
  }
}

internal class CrudAuxiliaryWorkflow(
  private val writer: CrudAuxiliaryWriter,
  private val runWriteCommand: ((() -> CrudAuxiliaryWriteResult) -> CrudAuxiliaryWriteResult),
  private val openInEditor: (Path) -> Unit,
  private val readText: (Path) -> String = Files::readString,
) {
  fun renderCustom(prepared: PreparedCrud, templates: List<Path>): CrudAuxiliaryWriteResult {
    require(templates.isNotEmpty()) { "Select at least one .hbs file" }
    require(templates.all { it.fileName.toString().endsWith(".hbs") }) {
      "Every custom template must end with .hbs"
    }
    val parents = templates.map { it.toAbsolutePath().normalize().parent }.toSet()
    require(parents.size == 1) { "All selected .hbs files must be in the same folder" }

    val files = templates.map { template ->
      val baseName = template.fileName.toString().removeSuffix(".hbs")
      CrudAuxiliaryFile(
        target = template.resolveSibling("$baseName.generated"),
        content = prepared.renderCustom(readText(template)),
      )
    }
    return writeAndOpen(files)
  }

  fun exportContext(prepared: PreparedCrud, directory: Path): CrudAuxiliaryWriteResult = writeAndOpen(
    listOf(CrudAuxiliaryFile(directory.resolve(prepared.contextFileName), prepared.contextJson())),
  )

  private fun writeAndOpen(files: List<CrudAuxiliaryFile>): CrudAuxiliaryWriteResult {
    val result = runWriteCommand { writer.write(files) }
    result.written.forEach(openInEditor)
    return result
  }
}
