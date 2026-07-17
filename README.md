# eGovFrame Initializr for IDEA (Community)

eGovFrame Initializr for IDEA는 IntelliJ IDEA에서 전자정부프레임워크 기반의 어플리케이션 개발을 지원하는 플러그인입니다.

> [!IMPORTANT]
> 이 프로젝트는 eGovFrame 공식 프로젝트나 공식 배포 플러그인이 아닌, 독립적으로 개발·유지보수되는
> **비공식 커뮤니티 플러그인**입니다. eGovFramework의 공식 지원, 보증 또는 승인을 의미하지 않습니다.

## 개발 환경 준비

상류 eGovFrame Initializr v5.0.6 리소스는 Git 서브모듈로 관리합니다. 저장소를 일반 clone한 경우 다음 명령으로
서브모듈과 플러그인에 번들되는 Git LFS ZIP 두 개를 준비합니다.

```bash
git submodule update --init
git -C vendor/egovframe-vscode-initializr lfs pull --include="templates/projects/examples/egovframe-boot-web.zip,templates/projects/examples/egovframe-boot-simple-backend.zip"
```

빌드 및 테스트:

```bash
# Linux/macOS
./gradlew build

# Windows
gradlew.bat build
```

## 라이선스

이 프로젝트는 Apache 2.0 라이선스로 배포되는 [VS Code Extension (eGovFrame Initializr)]를 참조하여 개발되었습니다.
따라서 이 프로젝트의 라이선스는 동일하게 [Apache 2.0] 라이선스를 따릅니다.

변경사항이 궁금하신 경우 [NOTICE] 파일을 참고하시기 바랍니다.

<!-- 변수 -->

[VS Code Extension (eGovFrame Initializr)]: https://github.com/eGovFramework/egovframe-vscode-initializr
[Apache 2.0]: (./LICENSE)
[NOTICE]: ./NOTICE
