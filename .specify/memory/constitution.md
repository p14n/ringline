<!--
Sync Impact Report:
- Version change: INITIAL → 1.0.0
- Added principles:
  * I. Namespace-First Architecture
  * II. Data-Driven Design
  * III. Test-First Development (NON-NEGOTIABLE)
  * IV. REPL-Driven Development
  * V. Schema Validation
  * VI. Simplicity & Immutability
- Added sections:
  * Technology Standards
  * Development Workflow
- Templates status:
  ✅ .specify/templates/plan-template.md - Constitution Check section ready
  ✅ .specify/templates/spec-template.md - Requirements alignment verified
  ✅ .specify/templates/tasks-template.md - Task categorization aligned
  ✅ .specify/templates/agent-file-template.md - No updates needed
  ✅ .specify/templates/checklist-template.md - No updates needed
- Follow-up TODOs: None
-->

# Ringline Constitution

## Core Principles

### I. Namespace-First Architecture

Every feature MUST be organized as a self-contained namespace with clear boundaries.
Namespaces MUST be independently loadable, testable, and documented. Each namespace
MUST have a single, well-defined purpose. Avoid creating organizational-only namespaces
that merely group unrelated functions.

**Rationale**: Clojure's namespace system provides natural modularity. Enforcing
namespace-first design ensures code remains composable, reusable, and maintainable
as the system grows.

### II. Data-Driven Design

All domain logic MUST operate on immutable data structures. Business entities MUST be
represented as plain Clojure maps with explicit schemas. Prefer pure functions that
transform data over stateful objects. Database interactions MUST use Datomic's
datalog queries and entity API.

**Rationale**: Data-driven design leverages Clojure's strengths in data manipulation
and enables easier testing, debugging, and reasoning about system behavior. Immutable
data structures prevent entire classes of bugs.

### III. Test-First Development (NON-NEGOTIABLE)

TDD is mandatory for all features. The workflow MUST be: Write tests → Get user
approval → Verify tests fail → Implement code → Verify tests pass. Use Kaocha for
test execution. Tests MUST be organized in parallel directory structure under `test/`.
Red-Green-Refactor cycle is strictly enforced.

**Rationale**: Test-first development catches bugs early, documents intended behavior,
and ensures code meets requirements. This is non-negotiable because it directly
impacts code quality and maintainability.

### IV. REPL-Driven Development

Development MUST leverage the REPL for interactive exploration and validation. All
namespaces MUST be reloadable without restarting the REPL. Use `tools.namespace`
for safe reloading. Functions MUST be designed to be easily testable from the REPL.
Provide rich comment blocks with example usage.

**Rationale**: REPL-driven development is a core Clojure workflow that enables rapid
feedback, experimentation, and debugging. It significantly improves developer
productivity and code quality.

### V. Schema Validation

All data entering or leaving system boundaries MUST be validated using Malli schemas.
This includes: API inputs/outputs (GraphQL), database transactions, inter-namespace
contracts, and configuration data. Schemas MUST be defined alongside the data they
validate and MUST be documented.

**Rationale**: Explicit schema validation catches errors at boundaries, provides
living documentation, and enables better tooling support. Malli's composable schemas
align with Clojure's data-first philosophy.

### VI. Simplicity & Immutability

Start simple and add complexity only when justified. Follow YAGNI (You Aren't Gonna
Need It) principles. Prefer pure functions over stateful operations. Avoid premature
abstraction. When complexity is necessary, it MUST be documented with clear rationale
in the implementation plan's Complexity Tracking section.

**Rationale**: Simplicity reduces cognitive load, makes code easier to understand and
maintain, and prevents over-engineering. Immutability eliminates entire categories
of bugs related to shared mutable state.

## Technology Standards

**Language**: Clojure 1.12.0 or higher
**GraphQL**: Lacinia 1.3.0-beta-1 for all API endpoints
**Validation**: Malli 0.20.0 for schema definition and validation
**Database**: Datomic Free 0.9.5697 for data persistence
**Testing**: Kaocha 1.91.1392 for test execution
**Build**: tools.build 0.10.5 for build automation

All dependencies MUST be managed through `deps.edn`. Version updates MUST be
documented with rationale. New dependencies MUST be justified in the implementation
plan before adoption.

## Development Workflow

### Code Organization

- Source code: `src/ringline/`
- Tests: `test/ringline/` (mirroring source structure)
- Resources: `resources/`
- Build: `build.clj` at repository root

### Quality Gates

1. **Pre-implementation**: Constitution Check MUST pass (verified in plan.md)
2. **During development**: All tests MUST pass before committing
3. **Pre-merge**: Code review MUST verify constitutional compliance
4. **Post-implementation**: Integration tests MUST validate feature contracts

### Testing Requirements

- **Unit tests**: Required for all pure functions
- **Integration tests**: Required for database interactions, GraphQL resolvers
- **Contract tests**: Required for API endpoints and inter-namespace boundaries
- **Test organization**: Tests MUST be grouped by feature/namespace

### REPL Workflow

1. Start REPL with `:dev` alias for development dependencies
2. Load namespace under development
3. Evaluate functions interactively
4. Run tests from REPL using Kaocha
5. Reload modified namespaces using `tools.namespace`

## Governance

This constitution supersedes all other development practices and guidelines. All
feature specifications, implementation plans, and code reviews MUST verify compliance
with these principles.

**Amendment Process**: Constitutional changes require:
1. Documented proposal with rationale
2. Impact analysis on existing code and templates
3. Version bump following semantic versioning
4. Update of all dependent templates and documentation
5. Migration plan for existing features if needed

**Compliance Review**: All pull requests MUST include a Constitution Check section
verifying adherence to core principles. Violations MUST be justified in the
Complexity Tracking section of the implementation plan.

**Complexity Justification**: Any deviation from constitutional principles MUST
document: (a) why the complexity is needed, (b) what simpler alternatives were
considered, and (c) why those alternatives were insufficient.

**Runtime Guidance**: Use `.augment/rules/specify-rules.md` (or equivalent
agent-specific files) for AI agent development guidance that complements this
constitution.

**Version**: 1.0.0 | **Ratified**: 2026-01-23 | **Last Amended**: 2026-01-23
