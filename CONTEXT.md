# eGovFrame Initializr Domain Context

## CRUD generation

### Canonical DDL analysis

CRUD와 ERD가 공유하는 단일 DDL 분석 결과다.

- 정상적인 입력 오류는 예외가 아니라 성공 또는 진단을 담은 분석 결과로 표현한다.
- ERD는 여러 `CREATE TABLE`을 표현할 수 있다.
- CRUD 준비는 분석된 테이블이 정확히 하나일 때만 성공한다.
- upstream CRUD parity는 canonical 분석 결과 위의 CRUD adapter가 보존한다.
- ERD adapter는 같은 분석 결과에서 테이블과 관계를 만든다.

### CrudArtifact

CRUD 생성이 지원하는 11개 산출물의 닫힌 domain 목록이다.

각 artifact는 안정된 identity와 다음 구현 정보를 module 내부에서 소유한다.

- Handlebars template
- 상대 출력 경로 규칙
- 표시 파일명
- language

사용자 선택 identity는 template filename이나 계산된 path에 의존하지 않는다.

### PreparedCrud

DDL, package name, canonical DDL analysis, ERD, template context, 렌더 결과를 묶은 불변 스냅샷이다.

- UTC system `Clock` adapter에서 준비 시점의 날짜를 한 번 캡처한다.
- DDL, package name, 날짜가 같으면 preview, custom render, context export, generation planning이 같은 스냅샷을 재사용한다.
- raw template context map은 interface에 노출하지 않는다.
- custom Handlebars rendering과 canonical context JSON export는 유지한다.

### GenerationPlan

`PreparedCrud`와 출력 root를 결합해 11개 artifact의 target과 collision 상태를 계산한 불변 계획이다.

- target은 출력 root의 실경로 안에 있어야 한다.
- 아직 존재하지 않는 parent는 가장 가까운 기존 조상의 실경로부터 confinement를 검증한다.
- 기존 파일과 충돌하는 artifact는 기본 선택에서 제외한다.
- 계획은 preflight 시점의 target 존재 상태를 기록한다.

### CrudWritePlan

사용자가 선택한 `CrudArtifact` identity와 overwrite 승인을 `GenerationPlan`에 적용해 만든 최종 불변 계획이다.

- module이 선택값이 원래 계획에 속하는지 검증한다.
- UI는 rendered file이나 path를 임의 조합하지 않는다.
- 실행 직전에 target 상태가 preflight와 달라지면 전체 쓰기를 중단하고 재계획을 요구한다.

### CRUD write adapter

`CrudWritePlan`을 실제 filesystem에 적용하는 adapter다.

- IntelliJ UI에서는 전체 실행을 하나의 `WriteCommandAction` 안에서 수행한다.
- 여러 artifact 쓰기는 all-or-nothing이다.
- 실패하면 새 파일을 제거하고 overwrite 대상의 원본을 복원한다.
- 성공 후에만 VFS refresh, editor open, notification을 수행한다.

## Architecture decisions

- Deep CRUD generation module의 interface가 분석, 준비, 계획, 선택 검증의 test surface다.
- Canonical parser tests는 SQL 문법 경계 사례를 방어한다.
- Golden tests는 isolated context parity를 유지하면서 production `PreparedCrud` 렌더 경로도 검증한다.
- 기존 `DdlParser.validateDDL`/`parseDDL`, `ErdParser.parseErdModel`, `CrudGenerator.generate`, top-level aliases처럼 역할이 흡수된 shallow interface는 clean cutover로 제거한다.
- 호환 shim이나 deprecated alias는 남기지 않는다.
