---
status: accepted
date: 2024-06-01
---
# ADR-0001: Clean Architecture in a Single `:app` Module

## Context and Problem Statement

Recall needed a maintainable architecture for a moderately complex Android app with data, domain, and presentation concerns. The question was whether to enforce layer separation via Gradle multi-module boundaries or via package-level conventions inside a single module. Multi-module projects reduce incremental build times at scale and enforce hard compile-time dependency rules, but add significant Gradle configuration overhead for a small team early in development.

## Decision

All code lives in the single `:app` module. Clean Architecture layers (Presentation → Domain ← Data) are enforced by package structure and code review convention rather than Gradle module boundaries.

## Rationale

- Single module keeps Gradle configuration simple; no inter-module dependency graphs to maintain during rapid iteration.
- Package-level separation (`presentation`, `domain`, `data`) is sufficient to enforce the inward dependency rule during early development.
- Hilt DI modules per layer (`DatabaseModule`, `RepositoryModule`, `UseCaseModule`, `OcrModule`) provide logical cohesion.
- Multi-module extraction can be done incrementally later if build times or team size justifies it — package boundaries make the extraction mechanical.
- At v1.0 scope, the build graph is not large enough for incremental compilation wins to outweigh configuration cost.

## Consequences

### Positive
- Fast project setup and minimal Gradle complexity.
- Easy cross-layer refactoring during active feature development.
- No risk of circular Gradle dependencies between modules.

### Negative / Trade-offs
- No compile-time enforcement of layer boundaries — a developer can accidentally import a Room entity in a composable without a build error.
- Incremental compilation benefits of multi-module are not available; full module recompilation on any change.
- Future multi-module extraction will require careful refactoring.
