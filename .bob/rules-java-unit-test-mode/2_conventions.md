# Java Unit Test Writer — Repo Conventions

These conventions are derived from the existing tests in `order-service/src/test/`.
**Do not deviate from them.**

---

## Assertion library — AssertJ only

Use `org.assertj.core.api.Assertions` static imports exclusively:

```java
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

Never use:
- `org.junit.jupiter.api.Assertions.assertEquals` / `assertTrue` / `assertThrows`
- `org.hamcrest.Matchers` / `assertThat` from Hamcrest

---

## Mocking — two patterns, pick the right one

### Service tests (unit, no Spring context)

```java
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private OrderService orderService;
```

- No `@SpringBootTest`, no application context
- `@Mock` on every dependency
- `@InjectMocks` on the class under test

### Controller tests (Spring MVC slice)

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @Autowired
    private ObjectMapper objectMapper;
```

- `@WebMvcTest(TheController.class)` — loads only the web layer
- `@MockBean` (Spring-managed mock) for service dependencies
- `@Autowired MockMvc` and `@Autowired ObjectMapper` wired automatically

---

## Mockito imports

```java
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
```

Use `any(Type.class)` when the exact argument value is not significant to the test.

---

## Test method naming

Pattern: `methodName_scenario_expectedOutcome`

All lowercase camelCase within each part. Underscores separate the three parts only.

| Good | Bad |
|---|---|
| `getAllOrders_returnsAllOrders` | `testGetAllOrders` |
| `getOrderById_existingId_returnsOrder` | `getOrderById_should_return_order` |
| `updateOrderStatus_invalidTransition_throwsException` | `updateOrderStatus_throws` |

---

## Fixture setup with @BeforeEach

Declare a field for the primary test object at class level, then populate it in `@BeforeEach`:

```java
private Order testOrder;

@BeforeEach
void setUp() {
    testOrder = new Order();
    testOrder.setId(1L);
    testOrder.setCustomerName("John Doe");
    testOrder.setProduct("Widget");
    testOrder.setAmount(new BigDecimal("29.99"));
    testOrder.setStatus("PENDING");
}
```

- Method name must be `setUp()` — matches the existing convention
- Only fields that are shared across multiple tests go in `setUp()`
- One-off objects for a single test are constructed inline in that test method

---

## MockMvc assertions

Use Spring MockMvc result matcher static imports:

```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
```

Assertions chain off `.andExpect(…)`:

```java
mockMvc.perform(get("/api/orders/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.customerName").value("Jane"));
```

---

## Exception assertions

```java
assertThatThrownBy(() -> orderService.updateOrderStatus(1L, "DELIVERED"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Cannot transition from PENDING to DELIVERED");
```

Always assert both the exception type and a meaningful substring of the message.

---

## What NOT to do

- Do not add `public` to test methods — package-private is the convention
- Do not add `public` to the test class — package-private is the convention
- Do not import `org.junit.jupiter.api.Assertions` — use AssertJ
- Do not use `@SpringBootTest` for service-layer unit tests — use `@ExtendWith(MockitoExtension.class)`
- Do not use `@Mock` in `@WebMvcTest` tests — use `@MockBean`
- Do not modify production source files
