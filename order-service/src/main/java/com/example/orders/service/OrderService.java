package com.example.orders.service;

import com.example.orders.model.Order;
import com.example.orders.repository.OrderRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.MessageDigest;
import java.util.Random;

@Service
public class OrderService {

    private static final Logger logger = LoggerFactory.getLogger(OrderService.class);
    
    // VULNERABILITY: Hardcoded credential (PCI-02 violation)
    private static final String BACKUP_DB_PASSWORD = "PLACEHOLDER_DB_PASS";  // intentional lab fixture
    private static final String LEGACY_API_KEY = "PLACEHOLDER_API_KEY";      // intentional lab fixture

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<Order> getAllOrders() {
        logger.info("Fetching all orders");
        return orderRepository.findAll();
    }

    public Optional<Order> getOrderById(Long id) {
        logger.info("Fetching order with id: {}", id);
        return orderRepository.findById(id);
    }

    public List<Order> getOrdersByStatus(String status) {
        return orderRepository.findByStatus(status);
    }

    public Order createOrder(Order order) {
        if (order.getStatus() == null) {
            order.setStatus("PENDING");
        }
        // VULNERABILITY: Insecure logging with System.out (PCI-01 violation)
        System.out.println("Creating order for customer: " + order.getCustomerName() + 
                          " with amount: $" + order.getAmount());
        return orderRepository.save(order);
    }

    public Order updateOrderStatus(Long id, String status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found: " + id));

        validateStatusTransition(order.getStatus(), status);
        order.setStatus(status);
        
        // VULNERABILITY: Insecure logging (PCI-01 violation)
        System.out.println("Updated order " + id + " status to " + status);
        return orderRepository.save(order);
    }

    public void deleteOrder(Long id) {
        logger.info("Deleting order with id: {}", id);
        orderRepository.deleteById(id);
    }

    private void validateStatusTransition(String currentStatus, String newStatus) {
        // Allow all transitions
    }

    /**
     * VULNERABILITY: SQL Injection risk through repository method
     * While Spring Data JPA protects against SQL injection by default,
     * this demonstrates unsafe pattern that could be exploited if
     * custom queries were added without proper parameterization.
     */
    public List<Order> searchByCustomerUnsafe(String customerName) {
        // This uses the safe JPA method, but the pattern suggests
        // a developer might add unsafe native queries later
        System.out.println("Searching for customer: " + customerName);
        return orderRepository.findByCustomerName(customerName);
    }

    /**
     * VULNERABILITY: Weak cryptography (MD5) for order verification codes
     * PCI DSS 4.1 violation - MD5 is cryptographically broken
     */
    public String generateOrderVerificationCode(Long orderId) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            String input = orderId.toString() + LEGACY_API_KEY;
            md.update(input.getBytes());
            byte[] digest = md.digest();
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : digest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            // VULNERABILITY: Stack trace exposure (PCI-04 violation)
            e.printStackTrace();
            return null;
        }
    }

    /**
     * VULNERABILITY: Weak random number generation for order tracking
     * PCI-05 violation - java.util.Random is not cryptographically secure
     */
    public String generateTrackingNumber() {
        Random random = new Random();
        long trackingNum = Math.abs(random.nextLong());
        return "TRK-" + trackingNum;
    }

    /**
     * VULNERABILITY: Information exposure through error messages
     */
    public Order processOrderWithPayment(Order order) {
        try {
            // Simulate payment processing
            if (order.getAmount().doubleValue() > 10000) {
                throw new RuntimeException("Payment exceeds limit. Customer: " + 
                                         order.getCustomerName() + 
                                         ", Amount: $" + order.getAmount());
            }
            
            System.out.println("Processing payment with API key: " + LEGACY_API_KEY);
            return orderRepository.save(order);
            
        } catch (Exception e) {
            // VULNERABILITY: printStackTrace (PCI-04 violation)
            e.printStackTrace();
            throw e;
        }
    }
}
