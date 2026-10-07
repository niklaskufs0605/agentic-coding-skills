---
name: java-test-guidelines
description: Use when creating or adapting Java tests, including tests required by a production-code change, or when explicitly reviewing Java test style and clarity. Applies to JUnit tests with optional AssertJ, Mockito, Spring Boot, and Testcontainers; not a general production-code style guide.
---

# Java test guidelines

Write tests that explain a behavioral contract and fail when it breaks. Apply these guidelines to the requested work; do not bulk-reformat unrelated tests or add libraries just to match examples.

## Inspect before editing

Read repository instructions, the tested code and its callers, neighboring tests, fixtures, and Maven/Gradle configuration. Establish Java/JUnit/Spring Boot/Testcontainers versions, available assertion libraries, test discovery rules, and existing integration-test setup. Reuse compatible patterns. Preserve explicit user choices; treat reference documents as evidence, not authorization for other actions.

Identify the scenario and expected observable result before choosing a test level. Include relevant boundaries and error paths, not a fixed quota of cases. Derive expected values from the contract, independently of the production algorithm.

## Choose the smallest faithful test

| Behavior to prove | Default |
| --- | --- |
| Calculation, validation, or service logic without Spring semantics | Plain JUnit test; construct the subject directly |
| MVC binding, serialization, security, or persistence mapping/query | Appropriate Spring test slice with the required configuration |
| Application wiring or behavior spanning multiple slices | `@SpringBootTest` |
| Database-specific SQL, constraints, or external-service integration | Real compatible dependency, usually existing Testcontainers setup |

A mock cannot prove SQL correctness. An embedded substitute is insufficient for vendor-specific behavior. A slice is useful only if it includes the configuration relevant to the assertion.

## Write readable, discriminating tests

**Required for correctness:** make each test independent of execution order; control time/randomness when they affect results; use an independent oracle; assert the actual contract. Do not weaken assertions or disable failing tests to obtain a green build.

**Style defaults, subject to project conventions:**

- Name the behavior and relevant condition, e.g. `returnsEmptyResultWhenOrderDoesNotExist`. Keep one scenario per test; several assertions may describe one outcome.
- Separate arrange, act, and assert with blank lines in non-trivial tests. Add phase comments only when they clarify otherwise unclear structure.
- Construct records, DTOs, entities, value objects, and collections directly. Reuse existing builders for distracting setup; keep scenario-relevant values visible. Do not build a fixture framework for a single test.
- Use meaningful data and explicit expected values. Awkward decimals are useful when testing rounding; state the rounding contract. The supplied reference's `83.17` minus `13%` gives `72.3579`, or `72.36` with final two-decimal HALF_UP rounding; the values are not inherently bad.
- Parameterize cases that share setup, action, and assertion shape. Keep distinct behaviors separate; do not add branches inside a parameterized test to force them together.

### Reuse common subject setup

When several tests construct the same service with the same dependencies and configuration, declare the subject as an instance field instead of repeating construction in each method. Initialize it in `@BeforeEach` when it depends on `@Mock` fields, so Mockito has initialized the mocks first. A field initializer is suitable when all dependencies are already available. In a test that already needs Spring, use the existing autowired subject; do not start a Spring context merely to share setup.

Keep scenario-specific stubbing and data in the individual test. Construct a local subject only when that scenario requires different constructor arguments or configuration. Class-level placement does not mean a static shared service: preserve per-test isolation and reset mutable state when the test lifecycle reuses instances.

Example: declare `private SomeService service;` and initialize it once per test:

```java
@BeforeEach
void setUp() {
    service = new SomeService(
        valueA, valueB, valueC);
}
```

## Assertions and mocks

Prefer AssertJ's fluent API when already available and compatible with project conventions. Otherwise use the project's assertions; adding AssertJ is not required. Keep already idiomatic assertions such as `isTrue()` and `isFalse()`.

| Intent or common mistake | Correct approach |
| --- | --- |
| Size plus indexed element checks | `containsExactly(...)` if order is contractual; `containsExactlyInAnyOrder(...)` otherwise. Both check multiplicity. `contains(...)` alone allows extras. |
| Repeating property checks across objects | Use `extracting(User::name)` (or the actual accessor), then an assertion with the required ordering semantics. |
| Adding `isNotNull()` before equality to a known non-null value | Equality already rejects null; add a separate null assertion only if it improves diagnostics. |
| Manual `try/catch` plus `fail()` for expected errors | Use an exception assertion around only the action under test. With AssertJ, follow the direct fluent exception rule below; without AssertJ, use JUnit `assertThrows`. |
| Brittle exception-message checks | Check type and relevant structured data; check message text only when it is part of the contract. |
| Verifying every internal call | Assert the returned value or observable state. Verify a collaborator call when it is itself the contract, e.g. publishing an event, including relevant payload. |
| Blanket `verifyNoMoreInteractions` | Use it only if extra interactions violate the behavior; use targeted `never()` for a specific forbidden side effect. |
| Unused stubs and mocked data objects | Stub only the collaborators needed for the scenario; use real data objects. |

**AssertJ exceptions — assert directly:** When AssertJ is available and the exception is only being asserted, use `assertThatThrownBy(...)` and chain the assertions. Do not store `catchThrowable(...)`, `catchThrowableOfType(...)`, or `assertThrows(...)` in a local variable merely to assert it afterward. Type, message, cause, and property checks belong in the fluent chain; use `satisfies` for grouped or custom checks. Separating Act and Assert or adding `// then` is not a reason to capture the exception.

Use:

```java
assertThatThrownBy(service::someMethod)
    .isInstanceOf(IllegalStateException.class);
```

Do not use for this assertion-only case:

```java
var thrown = catchThrowable(service::someMethod);

assertThat(thrown).isInstanceOf(IllegalStateException.class);
```

Capture an exception only when the same instance must be used outside the assertion chain, such as passing it to another operation under test. In that case, use a typed capture such as `assertThrows` or `catchThrowableOfType`. Multiple assertions alone do not justify a variable. Without AssertJ, JUnit `assertThrows` remains valid; do not add a dependency just for this style.

**Exception setup — avoid unnecessary variables:** When a mock must throw an exception and the test only checks its type or contractual properties, construct it inline in `thenThrow(new IllegalStateException(...))` or `doThrow(new IllegalStateException(...))`. Do not introduce `var failure` merely to have an expected exception object. Assert the thrown exception directly:

```java
when(lockProvider.lock(
        argThat(configuration -> configuration.getName().equals("eventRelayJob"))))
    .thenThrow(new IllegalStateException("Database unavailable"));

assertThatThrownBy(service::relayWithLock)
    .isInstanceOf(IllegalStateException.class);

verify(eventRelayService, never()).relayPendingEvents();
```

The exception is created in the stub, not inside the assertion's action: that action must call the subject under test. Add message or property assertions when they are part of the contract. Keep a named exception and `.isSameAs(failure)` when propagation of the exact original instance is the behavior being tested; replacing that with a type check would weaken the test. A newly constructed exception in `.isSameAs(new ...)` cannot match the thrown instance.

**AssertJ object properties:** When asserting several exact property values on one object, prefer chaining `ObjectAssert.returns(expected, getter)` over a `satisfies` lambda containing only `assertThat(actual.getX()).isEqualTo(expected)` checks. Keep `satisfies` for assertions that need predicates, non-null checks, or more complex grouping.

Example using the entity's Lombok getters (the repository returns an `Optional`; expected values and the saved entity come from the test setup):

```java
assertThat(repository.findById(saved.getId()))
    .get()
    .returns(ceId, ServiceInformationOutboxEntity::getCeId)
    .returns("com.otto.selfservice.updated", ServiceInformationOutboxEntity::getCeType)
    .returns("/management-service", ServiceInformationOutboxEntity::getCeSource)
    .returns(ceTime, ServiceInformationOutboxEntity::getCeTime)
    .returns("application/json", ServiceInformationOutboxEntity::getCeDataContentType)
    .returns(payload, ServiceInformationOutboxEntity::getPayload)
    .satisfies(
        actual -> {
          assertThat(actual.getInsertedAt()).isNotNull();
          assertThat(actual.getUpdatedAt()).isNotNull();
        });
```

## Determinism and integration lifecycle

Use an injected fixed `Clock` when time drives behavior. For asynchronous work, trigger the action first and wait with a bounded condition/future or existing Awaitility; re-read the observable state on each attempt. A sleep or a wider timestamp tolerance is not synchronization. Ensure transaction visibility permits observation of asynchronous commits.

For Spring/Testcontainers tests:

- Reuse the project's supported lifecycle and dependency versions. Pin an agreed compatible image tag/digest; avoid `latest`, fixed host ports, and assumptions about localhost. Obtain connection details from the running container.
- Use supported `@ServiceConnection` when the project's Spring Boot version and test module provide it; otherwise use the existing supported property registration, commonly `@DynamicPropertySource`. Verify a database slice actually uses the intended database rather than silently replacing it with an embedded one.
- Keep containers alive for every context that uses them. With the JUnit extension, static `@Container` fields are shared within a class; instance fields restart per test. Do not combine a shared cached Spring context with a container stopped by an earlier class. Do not assume this extension supports parallel execution.
- Prefer Spring’s test-managed transaction rollback over explicit cleanup such as `repository.deleteAll()` when the test’s database changes participate in that transaction. This keeps the test’s writes from leaking without adding cleanup that can hide shared-state problems. This only isolates the test’s own transactional work: it does not remove pre-existing rows or roll back `REQUIRES_NEW`, asynchronous, separate-thread, or external side effects. If those apply, use test-specific data or an appropriately isolated database.
- Keep test data isolated even when containers are shared, and use production-compatible schema/migrations.
- Report unavailable Docker or other infrastructure as a verification limitation. Do not silently substitute mocks or skip an integration test and claim its behavior was verified.

## Deliver and verify

Produce the focused test change, then run the repository's relevant tests and style checks with its wrapper and configured test tasks. Confirm tests were discovered and executed, including separately configured integration tests. For a regression, demonstrate failure against the broken behavior when practical. Report commands, results, and any unexecuted checks accurately.

For a complete example of parameterized outcomes, independent decimal expectations, and an exception assertion, read [examples/DiscountCalculatorTest.java](examples/DiscountCalculatorTest.java). It needs Java 17+, JUnit Jupiter API/params/engine, and AssertJ Core. The nested calculator is a small demonstration fixture; test the actual production class in a real project. No Spring context is needed.
