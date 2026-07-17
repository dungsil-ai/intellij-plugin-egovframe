package kr.kyg.ijplugin.egovframe.crud

internal enum class CrudArtifact(
  internal val templateFile: String,
  val language: String,
) {
  VO("sample-vo-template.hbs", "java"),
  DEFAULT_VO("sample-default-vo-template.hbs", "java"),
  CONTROLLER("sample-controller-template.hbs", "java"),
  SERVICE("sample-service-template.hbs", "java"),
  SERVICE_IMPL("sample-service-impl-template.hbs", "java"),
  MAPPER_INTERFACE("sample-mapper-interface-template.hbs", "java"),
  MAPPER_XML("sample-mapper-template.hbs", "xml"),
  THYMELEAF_LIST("sample-thymeleaf-list.hbs", "html"),
  THYMELEAF_REGISTER("sample-thymeleaf-register.hbs", "html"),
  JSP_LIST("sample-jsp-list.hbs", "html"),
  JSP_REGISTER("sample-jsp-register.hbs", "html");

  internal fun relativePath(tableName: String, packagePath: String): String {
    val lowerCamel = tableName[0].lowercaseChar() + tableName.substring(1)
    return when (this) {
      VO -> "src/main/java/$packagePath/service/${tableName}VO.java"
      DEFAULT_VO -> "src/main/java/$packagePath/service/${tableName}DefaultVO.java"
      CONTROLLER -> "src/main/java/$packagePath/web/${tableName}Controller.java"
      SERVICE -> "src/main/java/$packagePath/service/${tableName}Service.java"
      SERVICE_IMPL -> "src/main/java/$packagePath/service/impl/${tableName}ServiceImpl.java"
      MAPPER_INTERFACE -> "src/main/java/$packagePath/service/impl/${tableName}Mapper.java"
      MAPPER_XML -> "src/main/resources/mapper/${tableName}_SQL.xml"
      THYMELEAF_LIST -> "src/main/resources/templates/thymeleaf/$lowerCamel/${lowerCamel}List.html"
      THYMELEAF_REGISTER -> "src/main/resources/templates/thymeleaf/$lowerCamel/${lowerCamel}Register.html"
      JSP_LIST -> "src/main/webapp/WEB-INF/jsp/$packagePath/${lowerCamel}List.jsp"
      JSP_REGISTER -> "src/main/webapp/WEB-INF/jsp/$packagePath/${lowerCamel}Register.jsp"
    }
  }
}
