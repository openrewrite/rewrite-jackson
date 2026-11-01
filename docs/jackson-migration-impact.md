# Jackson migration impact

Run `org.openrewrite.java.jackson.FindJacksonMigrationImpact` before the Boot 4 or Jackson 3 migration:

```sh
mod run moderne://<organization-id> --recipe org.openrewrite.java.jackson.FindJacksonMigrationImpact
mod study moderne://changeset/<run-id> --data-table org.openrewrite.java.jackson.table.JacksonMigrationBoundaries
```

The composite includes the standalone `FindJacksonMigrationBoundaries` recipe and existing Maven/Gradle
`DependencyInsight` recipes for Jackson 2, Jackson 3, and Azure dependencies, including transitive dependencies.
The dependency recipes produce their own dependency insight tables. Inspect dependency paths and scopes there;
an Azure dependency is context, not a reason to retain every Jackson 2 usage in its module.
The boundary recipe emits only a data table; dependency searches can add search markers. Neither migrates code.

## Interpreting the evidence

Each boundary row includes the source path, project name and source set (when build markers are available),
category, resolved Jackson types, API signature, and source expression. Repository identity is supplied by Moderne.
Use the source path as well as project name to distinguish modules with identical names.

- `EXTERNAL_API`: a called API or constructor, or a method reference, exposes Jackson types and its declaring
  type is absent from that build project's supplied sources. Review as a possible retained-library boundary. Missing/generated
  sources or another build module can also explain this category.
- `EXTERNAL_GENERIC_API`: an external generic method or parameterized declaring type. Jackson in its resolved
  signature may come from call-site inference rather than a fixed library requirement. Inspect the original
  library declaration before deciding to retain Jackson 2.
- `SOURCE_API`: the declaring type is present in that build project's supplied sources. Consider its callers before migrating
  a shared mapper or serializer. This is not whole-program data-flow analysis.
- `SPRING_API`: a Spring API signature exposes Jackson types. Inspect explicit converters/codecs, builders,
  and Redis/session serializers rather than assuming every Spring usage requires Jackson 3.
- `JACKSON_API` / `MAPPER_CONSTRUCTION`: direct Jackson API usage and mapper instantiation.
- `DECLARATION`: Jackson-typed variables and method signatures, custom serializer/module/mapper inheritance,
  and version-specific annotations. Method evidence includes annotations such as `@Bean` and `@Qualifier`.
- `SHARED_ANNOTATION`: `com.fasterxml.jackson.annotation` is shared with Jackson 3 and does not require Jackson 2.
- `UNRESOLVED_CALL`: missing method attribution with a known Jackson receiver or argument. Review rather than
  treating an unresolved call as safe to migrate.

The detector follows generic arguments, arrays, bounded type variables, and mapper/serializer subtypes.
It does not detect Jackson dependencies hidden entirely inside an SDK, reflection, runtime bean selection,
external configuration, generated code absent from the LST, or untyped expressions. No findings is not proof
that Jackson 2 can be removed. Run the dependency inventory alongside source analysis.

## Follow-up migration composition

Do not add a Jackson selection option to the public Spring Boot upgrade recipe. Keep detection separate and
use its evidence to design module-scoped composition:

1. Separate Spring/Boot structural upgrades from Jackson adoption internally, auditing the complete composed
   graph (including Spring-specific type/property changes and code generators).
2. For a reviewed retained-Jackson-2 module, preserve source APIs and required dependencies. If Boot-managed
   Jackson 2 beans are needed, add `spring-boot-jackson2`, adapt applicable configuration to `spring.jackson2.*`,
   and explicitly choose Jackson 2 for the applicable HTTP stack. Preserve explicit custom configurations.
3. Run the existing Jackson 3 adoption recipes for reviewed eligible modules. In mixed modules, retaining
   Jackson 2 means adding Jackson 3 alongside it, not replacing every Jackson 2 dependency. Account for parent
   dependency management, cross-module signatures, and module-wide `lombok.config`/generator configuration.
4. Do not infer automatic exception-driven Jackson 3 fallback. Boot's preferred-mapper properties choose a
   mapper; they do not promise retry after serialization failure.

An external Jackson-typed signature is evidence about that API, not enough to select every source file in a
module automatically. Leave ambiguous boundaries for review until representative customer findings establish
safe rules. Compile and test consumers; wire-format and persisted-session compatibility need application tests.

References:

- [Spring Boot 4 Jackson 2 compatibility](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide#jackson-2-compatibility)
- [Boot Jackson 2 configuration and preferences](https://docs.spring.io/spring-boot/4.0/reference/features/json.html#features.json.jackson2)
- [Jackson 3 shared annotations](https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0#major-changesfeatures-in-30)
