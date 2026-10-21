# Java Unit Test Writer — Workflow

## Overview

This mode writes JUnit 5 + Mockito + AssertJ unit tests for the `order-service` Spring Boot application.
Tests must match the repo's established conventions exactly — never invent a new style.

---

## Step 1 — Read before you write

Before writing any test, **always** read the relevant existing test file(s):

| What you're testing | Reference file |
|---|---|
| A service class | `order-service/src/test/java/com/example/orders/service/OrderServiceTest.java` |
| A controller class | `order-service/src/test/java/com/example/orders/controller/OrderControllerTest.java` |

Use `read_file` on both if you are unsure. Extract:

- The assertion library and import style (`assertThat`, `assertThatThrownBy` from AssertJ — never `assertEquals`)
- The mock setup strategy (`@ExtendWith(MockitoExtension.class)` + `@Mock` / `@InjectMocks` for services; `@WebMvcTest` + `@MockBean` for controllers)
- The test method naming pattern (`methodName_scenario_expectedOutcome`)
- How `@BeforeEach` fixtures are structured and what class-level fields exist

---

## Step 2 — Read the production class under test

Use `read_file` to read the source file you are about to test so you know:

- The public method signatures
- What dependencies are injected (these become `@Mock` or `@MockBean` fields)
- What exceptions can be thrown
- Edge-case inputs that are worth covering

---

## Step 3 — Plan test cases

Before writing code, list the scenarios you will cover:

- **Happy path** — the normal, expected flow
- **Edge cases** — null inputs, empty collections, boundary values
- **Error paths** — exceptions thrown, invalid state transitions, missing entities

Aim for one `@Test` method per distinct scenario.

---

## Step 4 — Write the test file

Follow the rules in `2_conventions.md` precisely. Key points:

- Package declaration must match the production class's package
- Use the same `@BeforeEach` fixture pattern as the existing tests
- Every mock interaction must have a `when(…).thenReturn(…)` set up **before** the call under test
- Side-effect verification uses `verify(mock).method(…)` — placed **after** the call under test
- Assertions use AssertJ fluent chains, never plain JUnit `assertEquals`

---

## Step 5 — Validate

After writing, re-read the file you just created and confirm:

1. No JUnit 4 imports (`org.junit.Test`, `org.junit.Before`, etc.) — only JUnit 5
2. No Hamcrest (`assertThat` from `org.hamcrest`) — only AssertJ
3. Test method names follow `methodName_scenario_expectedOutcome` (camelCase, no underscores except as separators between the three parts)
4. The file compiles: check that all referenced classes are imported and all referenced fields/methods exist in the production code

---

## Completion criteria

- All requested scenarios have a dedicated `@Test` method
- The file follows repo conventions without deviation
- No production source files were modified
