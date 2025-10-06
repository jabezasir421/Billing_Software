// File: BillingApp.java
package com.billing;

import jakarta.persistence.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@SpringBootApplication
public class BillingApp {
    public static void main(String[] args) {
        SpringApplication.run(BillingApp.class, args);
    }
}

/* -------------------- ENTITIES -------------------- */
@Entity
@Table(name="customers")
class Customer {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String email;
    private String phone;
    // Getters & Setters
    public Long getId() { return id; } public void setId(Long id) { this.id=id; }
    public String getName() { return name; } public void setName(String name) { this.name=name; }
    public String getEmail() { return email; } public void setEmail(String email) { this.email=email; }
    public String getPhone() { return phone; } public void setPhone(String phone) { this.phone=phone; }
}

@Entity
@Table(name="products")
class Product {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private String name;
    private double price;
    private int stock;
    // Getters & Setters
    public Long getId() { return id; } public void setId(Long id) { this.id=id; }
    public String getName() { return name; } public void setName(String name) { this.name=name; }
    public double getPrice() { return price; } public void setPrice(double price) { this.price=price; }
    public int getStock() { return stock; } public void setStock(int stock) { this.stock=stock; }
}

@Entity
@Table(name="invoices")
class Invoice {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private Long customerId;
    private double totalAmount;
    // Getters & Setters
    public Long getId() { return id; } public void setId(Long id) { this.id=id; }
    public Long getCustomerId() { return customerId; } public void setCustomerId(Long customerId) { this.customerId=customerId; }
    public double getTotalAmount() { return totalAmount; } public void setTotalAmount(double totalAmount) { this.totalAmount=totalAmount; }
}

@Entity
@Table(name="ledgers")
class Ledger {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    private String account;
    private double balance;
    // Getters & Setters
    public Long getId() { return id; } public void setId(Long id) { this.id=id; }
    public String getAccount() { return account; } public void setAccount(String account) { this.account=account; }
    public double getBalance() { return balance; } public void setBalance(double balance) { this.balance=balance; }
}

/* -------------------- REPOSITORIES -------------------- */
@Repository interface CustomerRepo extends JpaRepository<Customer, Long> {}
@Repository interface ProductRepo extends JpaRepository<Product, Long> {}
@Repository interface InvoiceRepo extends JpaRepository<Invoice, Long> {}
@Repository interface LedgerRepo extends JpaRepository<Ledger, Long> {}

/* -------------------- CONTROLLERS -------------------- */
@RestController
@RequestMapping("/api/customers")
class CustomerController {
    @Autowired CustomerRepo repo;
    @GetMapping List<Customer> getAll() { return repo.findAll(); }
    @PostMapping Customer create(@RequestBody Customer c) { return repo.save(c); }
    @GetMapping("/{id}") Customer get(@PathVariable Long id) { return repo.findById(id).orElseThrow(); }
    @PutMapping("/{id}") Customer update(@PathVariable Long id, @RequestBody Customer c) {
        Customer cust = repo.findById(id).orElseThrow();
        cust.setName(c.getName()); cust.setEmail(c.getEmail()); cust.setPhone(c.getPhone());
        return repo.save(cust);
    }
    @DeleteMapping("/{id}") void delete(@PathVariable Long id) { repo.delete(repo.findById(id).orElseThrow()); }
}

@RestController
@RequestMapping("/api/products")
class ProductController {
    @Autowired ProductRepo repo;
    @GetMapping List<Product> getAll() { return repo.findAll(); }
    @PostMapping Product create(@RequestBody Product p) { return repo.save(p); }
    @GetMapping("/{id}") Product get(@PathVariable Long id) { return repo.findById(id).orElseThrow(); }
    @PutMapping("/{id}") Product update(@PathVariable Long id, @RequestBody Product p) {
        Product prod = repo.findById(id).orElseThrow();
        prod.setName(p.getName()); prod.setPrice(p.getPrice()); prod.setStock(p.getStock());
        return repo.save(prod);
    }
    @DeleteMapping("/{id}") void delete(@PathVariable Long id) { repo.delete(repo.findById(id).orElseThrow()); }
}

@RestController
@RequestMapping("/api/invoices")
class InvoiceController {
    @Autowired InvoiceRepo repo;
    @GetMapping List<Invoice> getAll() { return repo.findAll(); }
    @PostMapping Invoice create(@RequestBody Invoice i) { return repo.save(i); }
    @GetMapping("/{id}") Invoice get(@PathVariable Long id) { return repo.findById(id).orElseThrow(); }
    @PutMapping("/{id}") Invoice update(@PathVariable Long id, @RequestBody Invoice i) {
        Invoice inv = repo.findById(id).orElseThrow();
        inv.setCustomerId(i.getCustomerId()); inv.setTotalAmount(i.getTotalAmount());
        return repo.save(inv);
    }
    @DeleteMapping("/{id}") void delete(@PathVariable Long id) { repo.delete(repo.findById(id).orElseThrow()); }
}

@RestController
@RequestMapping("/api/ledgers")
class LedgerController {
    @Autowired LedgerRepo repo;
    @GetMapping List<Ledger> getAll() { return repo.findAll(); }
    @PostMapping Ledger create(@RequestBody Ledger l) { return repo.save(l); }
    @GetMapping("/{id}") Ledger get(@PathVariable Long id) { return repo.findById(id).orElseThrow(); }
    @PutMapping("/{id}") Ledger update(@PathVariable Long id, @RequestBody Ledger l) {
        Ledger led = repo.findById(id).orElseThrow();
        led.setAccount(l.getAccount()); led.setBalance(l.getBalance());
        return repo.save(led);
    }
    @DeleteMapping("/{id}") void delete(@PathVariable Long id) { repo.delete(repo.findById(id).orElseThrow()); }
}
