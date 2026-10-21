# Java Unit Test Writer — Ready-to-Use Patterns

Copy-paste starting points for the two test archetypes in this repo.
Adapt field names and method calls to match the class under test.

---

## Pattern A — Service unit test (no Spring context)

```java
package com.example.orders.service;

import com.example.orders.model.Order;
import com.example.orders.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MyServiceTest {

    @Mock
    private OrderRepository orderRepository;        // ← replace with actual dependency

    @InjectMocks
    private MyService myService;                    // ← replace with class under test

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

    @Test
    void someMethod_happyPath_returnsExpected() {
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));

        Order result = myService.someMethod(1L);

        assertThat(result).isNotNull();
        assertThat(result.getCustomerName()).isEqualTo("John Doe");
    }

    @Test
    void someMethod_missingEntity_throwsException() {
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> myService.someMethod(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void someMethod_sideEffect_callsRepository() {
        myService.someMethod(1L);

        verify(orderRepository).deleteById(1L);     // ← replace with actual verification
    }
}
```

---

## Pattern B — Controller MVC slice test

```java
package com.example.orders.controller;

import com.example.orders.model.Order;
import com.example.orders.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MyController.class)                     // ← replace with controller under test
class MyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;              // ← replace with actual service dependency

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getById_existingId_returns200WithBody() throws Exception {
        Order order = new Order();
        order.setId(1L);
        order.setCustomerName("Jane");
        order.setProduct("Gadget");
        order.setAmount(new BigDecimal("49.99"));
        order.setStatus("PENDING");

        when(orderService.getOrderById(1L)).thenReturn(Optional.of(order));

        mockMvc.perform(get("/api/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Jane"));
    }

    @Test
    void getById_nonExistingId_returns404() throws Exception {
        when(orderService.getOrderById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_validBody_returns201() throws Exception {
        Order order = new Order();
        order.setCustomerName("Bob");
        order.setProduct("Thing");
        order.setAmount(new BigDecimal("19.99"));
        order.setStatus("PENDING");

        when(orderService.createOrder(any(Order.class))).thenReturn(order);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customerName").value("Bob"));
    }

    @Test
    void create_missingRequiredFields_returns400() throws Exception {
        Order empty = new Order();

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(empty)))
                .andExpect(status().isBadRequest());
    }
}
```

---

## Common assertion snippets

```java
// Simple value equality
assertThat(result.getStatus()).isEqualTo("CONFIRMED");

// Optional present / absent
assertThat(result).isPresent();
assertThat(result).isEmpty();

// Collection size
assertThat(orders).hasSize(1);

// Exception type + message
assertThatThrownBy(() -> service.doThing(id))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expected fragment");

// Verify void side-effect
verify(repository).deleteById(1L);
```
