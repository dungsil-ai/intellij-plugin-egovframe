package kr.kyg.ijplugin.egovframe.crud

/** 1:1 port of upstream `src/utils/codeGenerator.ts#getTemplateFilesConfig`. */
data class TemplateFileInfo(
  val templateFile: String,
  val outputPath: String,
  val fileName: String,
  val language: String,
)

object CrudTemplates {

  fun templateFilesConfig(tableName: String, packagePath: String): List<TemplateFileInfo> {
    val tableNameCamelCase = tableName[0].lowercaseChar() + tableName.substring(1)
    return listOf(
      TemplateFileInfo(
        "sample-vo-template.hbs",
        "src/main/java/$packagePath/service/${tableName}VO.java",
        "${tableName}VO.java",
        "java",
      ),
      TemplateFileInfo(
        "sample-default-vo-template.hbs",
        "src/main/java/$packagePath/service/${tableName}DefaultVO.java",
        "${tableName}DefaultVO.java",
        "java",
      ),
      TemplateFileInfo(
        "sample-controller-template.hbs",
        "src/main/java/$packagePath/web/${tableName}Controller.java",
        "${tableName}Controller.java",
        "java",
      ),
      TemplateFileInfo(
        "sample-service-template.hbs",
        "src/main/java/$packagePath/service/${tableName}Service.java",
        "${tableName}Service.java",
        "java",
      ),
      TemplateFileInfo(
        "sample-service-impl-template.hbs",
        "src/main/java/$packagePath/service/impl/${tableName}ServiceImpl.java",
        "${tableName}ServiceImpl.java",
        "java",
      ),
      TemplateFileInfo(
        "sample-mapper-interface-template.hbs",
        "src/main/java/$packagePath/service/impl/${tableName}Mapper.java",
        "${tableName}Mapper.java",
        "java",
      ),
      TemplateFileInfo(
        "sample-mapper-template.hbs",
        "src/main/resources/mapper/${tableName}_SQL.xml",
        "${tableName}_SQL.xml",
        "xml",
      ),
      TemplateFileInfo(
        "sample-thymeleaf-list.hbs",
        "src/main/resources/templates/thymeleaf/$tableNameCamelCase/${tableNameCamelCase}List.html",
        "${tableNameCamelCase}List.html",
        "html",
      ),
      TemplateFileInfo(
        "sample-thymeleaf-register.hbs",
        "src/main/resources/templates/thymeleaf/$tableNameCamelCase/${tableNameCamelCase}Register.html",
        "${tableNameCamelCase}Register.html",
        "html",
      ),
      TemplateFileInfo(
        "sample-jsp-list.hbs",
        "src/main/webapp/WEB-INF/jsp/$packagePath/${tableNameCamelCase}List.jsp",
        "${tableNameCamelCase}List.jsp",
        "html",
      ),
      TemplateFileInfo(
        "sample-jsp-register.hbs",
        "src/main/webapp/WEB-INF/jsp/$packagePath/${tableNameCamelCase}Register.jsp",
        "${tableNameCamelCase}Register.jsp",
        "html",
      ),
    )
  }
}
