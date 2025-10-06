// File: BillingMonolith.java
// Single-file Spring Boot application containing a compact, runnable (demo) implementation
// of a multi-tenant billing backend. This is a single-file scaffold for study/prototyping.
// For production, split into modules/files and replace stubs with production-grade implementations.
//
// Dependencies (Maven/Gradle):
// - Spring Boot Starter Web, Data JPA, Security (optional), AOP (optional)
// - HikariCP
// - PostgreSQL JDBC (or use H2 for local demo)
// - Apache PDFBox
// - jackson-databind
//
// NOTE: This single-file demo uses JPA entities, Spring Data repositories, a tenant-aware
// datasource wrapper (uses SET search_path), a very small JWT stub, PDF export via PDFBox,
// SPI plugin interfaces and a sample plugin loader. It focuses on showing structural code
// in one file — adapt for production accordingly.

package com.example.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.stereotype.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import jakarta.persistence.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.stream.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.io.*;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariConfig;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/**
 * Public application class.
 */
@SpringBootApplication
class BillingMonolith {

    public static void main(String[] args) {
        SpringApplication.run(BillingMonolith.class, args);
    }

    // -----------------
    // Configuration Beans
    // -----------------
    @Bean
    @Primary
    public DataSource dataSource(@Value("${app.datasource.url:jdbc:postgresql://localhost:5432/billing}") String url,
                                 @Value("${app.datasource.username:postgres}") String username,
                                 @Value("${app.datasource.password:postgres}") String password) {
        // Configure Hikari (this DataSource will be wrapped by TenantAwareDataSource in a moment)
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setUsername(username);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(10);
        HikariDataSource ds = new HikariDataSource(cfg);
        return ds;
    }

    @Bean
    public TenantAwareDataSource tenantAwareDataSource(DataSource dataSource) {
        return new TenantAwareDataSource(dataSource);
    }

    @Bean
    public FilterRegistrationBean<JwtFilter> jwtFilterRegistration(JwtFilter jwtFilter) {
        FilterRegistrationBean<JwtFilter> reg = new FilterRegistrationBean<>(jwtFilter);
        reg.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        reg.addUrlPatterns("/api/*");
        return reg;
    }

    @Bean
    public CommandLineRunner bootstrapData(CompanyRepository compRepo, GlobalUserRepository guRepo, TenantRepository tRepo) {
        return args -> {
            // bootstrap a default tenant in public schema (for demo)
            if (tRepo.count() == 0) {
                TenantMeta tm = new TenantMeta();
                tm.setSlug("tenant_default");
                tm.setName("Default Tenant");
                tm.setDbSchema("tenant_default");
                tRepo.save(tm);
            }
            if (guRepo.count() == 0) {
                GlobalUser g = new GlobalUser();
                g.setEmail("admin@example.com");
                g.setPasswordHash("admin"); // demo only: plaintext stub
                guRepo.save(g);
            }
        };
    }
}

// ============================
// Tenant context holder
// ============================
class TenantContext {
    private static final ThreadLocal<String> TENANT = new ThreadLocal<>();
    public static void setTenant(String t) { TENANT.set(t); }
    public static String getTenant() { return TENANT.get(); }
    public static void clear() { TENANT.remove(); }
}

// ============================
// Tenant-aware DataSource wrapper
// Sets search_path to tenant schema per-connection
// ============================
class TenantAwareDataSource implements DataSource {
    private final DataSource delegate;
    public TenantAwareDataSource(DataSource delegate) { this.delegate = delegate; }

    private void setSchemaForConnection(Connection conn) throws SQLException {
        String tenant = TenantContext.getTenant();
        if (tenant != null && !tenant.isBlank()) {
            try (Statement s = conn.createStatement()) {
                // sanitize tenant name minimally
                String safe = tenant.replaceAll("[^a-zA-Z0-9_]", "");
                s.execute("SET search_path TO " + safe + ", public");
            }
        }
    }

    @Override public Connection getConnection() throws SQLException {
        Connection c = delegate.getConnection();
        setSchemaForConnection(c);
        return c;
    }
    @Override public Connection getConnection(String username, String password) throws SQLException {
        Connection c = delegate.getConnection(username, password);
        setSchemaForConnection(c);
        return c;
    }
    // delegate other methods
    @Override public <T> T unwrap(Class<T> iface) throws SQLException { return delegate.unwrap(iface); }
    @Override public boolean isWrapperFor(Class<?> iface) throws SQLException { return delegate.isWrapperFor(iface); }
    @Override public PrintWriter getLogWriter() throws SQLException { return delegate.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter out) throws SQLException { delegate.setLogWriter(out); }
    @Override public void setLoginTimeout(int seconds) throws SQLException { delegate.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() throws SQLException { return delegate.getLoginTimeout(); }
    @Override public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException { return delegate.getParentLogger(); }
}

// ============================
// JWT Filter (very small stub for demo)
// Expect token format: "tenant:tenant_slug;user:userId;roles:admin,cashier"
// In production replace with RS256 verification.
// ============================
@Component
class JwtFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        try {
            HttpServletRequest req = (HttpServletRequest) request;
            String auth = req.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                String token = auth.substring(7);
                parseAndSet(token);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private void parseAndSet(String token) {
        // very simple parser: key:value;key2:value2
        String[] parts = token.split(";");
        for (String p : parts) {
            if (p.startsWith("tenant:")) {
                TenantContext.setTenant(p.substring("tenant:".length()));
            }
        }
    }
}

// ============================
// JPA Entities - public and tenant-scoped
// ============================
@Entity
@Table(name = "tenant")
class TenantMeta {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(unique = true) private String slug;
    private String name;
    @Column(name = "db_schema") private String dbSchema;
    private String plan;
    // getters/setters
    public UUID getId(){return id;}
    public void setId(UUID id){this.id=id;}
    public String getSlug(){return slug;}
    public void setSlug(String s){this.slug=s;}
    public String getName(){return name;}
    public void setName(String n){this.name=n;}
    public String getDbSchema(){return dbSchema;}
    public void setDbSchema(String s){this.dbSchema=s;}
    public String getPlan(){return plan;}
    public void setPlan(String p){this.plan=p;}
}

@Entity
@Table(name = "global_user")
class GlobalUser {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    @Column(unique = true) private String email;
    private String passwordHash;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    // getters/setters
    public UUID getId(){return id;}
    public void setId(UUID id){this.id=id;}
    public String getEmail(){return email;}
    public void setEmail(String e){this.email=e;}
    public String getPasswordHash(){return passwordHash;}
    public void setPasswordHash(String p){this.passwordHash=p;}
    public OffsetDateTime getCreatedAt(){return createdAt;}
}

// Tenant-scoped entities - in real deployment they will live in tenant schema
@Entity
@Table(name = "company")
class Company {
    @Id
    private String id = UUID.randomUUID().toString();
    private String name;
    private String configJson;
    // getters/setters
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public String getName(){return name;}
    public void setName(String n){this.name=n;}
    public String getConfigJson(){return configJson;}
    public void setConfigJson(String c){this.configJson=c;}
}

@Entity
@Table(name = "app_user")
class AppUser {
    @Id private String id = UUID.randomUUID().toString();
    private String companyId;
    private String username;
    private String passwordHash;
    private String role; // admin|cashier|auditor
    // getters/setters
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public String getCompanyId(){return companyId;}
    public void setCompanyId(String c){this.companyId=c;}
    public String getUsername(){return username;}
    public void setUsername(String u){this.username=u;}
    public String getPasswordHash(){return passwordHash;}
    public void setPasswordHash(String p){this.passwordHash=p;}
    public String getRole(){return role;}
    public void setRole(String r){this.role=r;}
}

@Entity
@Table(name = "customer")
class Customer {
    @Id private String id = UUID.randomUUID().toString();
    private String companyId;
    private String name;
    private String phone;
    private String email;
    private String address;
    private String metadataJson;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    // getters/setters
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public String getCompanyId(){return companyId;}
    public void setCompanyId(String c){this.companyId=c;}
    public String getName(){return name;}
    public void setName(String n){this.name=n;}
    public String getPhone(){return phone;}
    public void setPhone(String p){this.phone=p;}
    public String getEmail(){return email;}
    public void setEmail(String e){this.email=e;}
    public String getAddress(){return address;}
    public void setAddress(String a){this.address=a;}
    public String getMetadataJson(){return metadataJson;}
    public void setMetadataJson(String m){this.metadataJson=m;}
    public OffsetDateTime getCreatedAt(){return createdAt;}
}

@Entity
@Table(name = "product")
class Product {
    @Id private String id = UUID.randomUUID().toString();
    private String companyId;
    private String sku;
    private String name;
    private String description;
    private BigDecimal price = BigDecimal.ZERO;
    private String taxId;
    private Integer stock = 0;
    private String metadataJson;
    private OffsetDateTime createdAt = OffsetDateTime.now();
    // getters/setters omitted for brevity (generate in IDE)
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public String getCompanyId(){return companyId;}
    public void setCompanyId(String c){this.companyId=c;}
    public String getSku(){return sku;}
    public void setSku(String s){this.sku=s;}
    public String getName(){return name;}
    public void setName(String n){this.name=n;}
    public String getDescription(){return description;}
    public void setDescription(String d){this.description=d;}
    public BigDecimal getPrice(){return price;}
    public void setPrice(BigDecimal p){this.price=p;}
    public String getTaxId(){return taxId;}
    public void setTaxId(String t){this.taxId=t;}
    public Integer getStock(){return stock;}
    public void setStock(Integer s){this.stock=s;}
    public String getMetadataJson(){return metadataJson;}
    public void setMetadataJson(String m){this.metadataJson=m;}
}

@Entity
@Table(name = "tax")
class Tax {
    @Id private String id = UUID.randomUUID().toString();
    private String companyId;
    private String name;
    private BigDecimal rate = BigDecimal.ZERO; // percent
    private String type; // percentage|flat
    // getters/setters
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public String getCompanyId(){return companyId;}
    public void setCompanyId(String c){this.companyId=c;}
    public String getName(){return name;}
    public void setName(String n){this.name=n;}
    public BigDecimal getRate(){return rate;}
    public void setRate(BigDecimal r){this.rate=r;}
    public String getType(){return type;}
    public void setType(String t){this.type=t;}
}

@Entity
@Table(name = "invoice")
class Invoice {
    @Id private String id = UUID.randomUUID().toString();
    private String companyId;
    private String invoiceNumber;
    private String customerId;
    private OffsetDateTime date = OffsetDateTime.now();
    private String currency = "INR";
    private BigDecimal subtotal = BigDecimal.ZERO;
    private BigDecimal taxTotal = BigDecimal.ZERO;
    private BigDecimal discount = BigDecimal.ZERO;
    private BigDecimal total = BigDecimal.ZERO;
    private String status = "draft"; // draft|issued|paid|cancelled
    private String metadataJson;
    // Lines are stored in invoice_line table (separate entity)
    // getters/setters
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public String getCompanyId(){return companyId;}
    public void setCompanyId(String c){this.companyId=c;}
    public String getInvoiceNumber(){return invoiceNumber;}
    public void setInvoiceNumber(String n){this.invoiceNumber=n;}
    public String getCustomerId(){return customerId;}
    public void setCustomerId(String c){this.customerId=c;}
    public OffsetDateTime getDate(){return date;}
    public void setDate(OffsetDateTime d){this.date=d;}
    public String getCurrency(){return currency;}
    public void setCurrency(String cur){this.currency=cur;}
    public BigDecimal getSubtotal(){return subtotal;}
    public void setSubtotal(BigDecimal s){this.subtotal=s;}
    public BigDecimal getTaxTotal(){return taxTotal;}
    public void setTaxTotal(BigDecimal t){this.taxTotal=t;}
    public BigDecimal getDiscount(){return discount;}
    public void setDiscount(BigDecimal d){this.discount=d;}
    public BigDecimal getTotal(){return total;}
    public void setTotal(BigDecimal t){this.total=t;}
    public String getStatus(){return status;}
    public void setStatus(String s){this.status=s;}
    public String getMetadataJson(){return metadataJson;}
    public void setMetadataJson(String m){this.metadataJson=m;}
}

@Entity
@Table(name = "invoice_line")
class InvoiceLine {
    @Id private String id = UUID.randomUUID().toString();
    private String invoiceId;
    private String productId;
    private String description;
    private BigDecimal qty = BigDecimal.ONE;
    private BigDecimal unitPrice = BigDecimal.ZERO;
    private BigDecimal lineTotal = BigDecimal.ZERO;
    private String taxId;
    // getters/setters
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public String getInvoiceId(){return invoiceId;}
    public void setInvoiceId(String i){this.invoiceId=i;}
    public String getProductId(){return productId;}
    public void setProductId(String p){this.productId=p;}
    public String getDescription(){return description;}
    public void setDescription(String d){this.description=d;}
    public BigDecimal getQty(){return qty;}
    public void setQty(BigDecimal q){this.qty=q;}
    public BigDecimal getUnitPrice(){return unitPrice;}
    public void setUnitPrice(BigDecimal u){this.unitPrice=u;}
    public BigDecimal getLineTotal(){return lineTotal;}
    public void setLineTotal(BigDecimal l){this.lineTotal=l;}
    public String getTaxId(){return taxId;}
    public void setTaxId(String t){this.taxId=t;}
}

@Entity
@Table(name = "payment")
class Payment {
    @Id private String id = UUID.randomUUID().toString();
    private String invoiceId;
    private String companyId;
    private BigDecimal amount = BigDecimal.ZERO;
    private String method; // cash|card|upi|bank
    private String reference;
    private OffsetDateTime paidAt = OffsetDateTime.now();
    // getters/setters
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public String getInvoiceId(){return invoiceId;}
    public void setInvoiceId(String i){this.invoiceId=i;}
    public String getCompanyId(){return companyId;}
    public void setCompanyId(String c){this.companyId=c;}
    public BigDecimal getAmount(){return amount;}
    public void setAmount(BigDecimal a){this.amount=a;}
    public String getMethod(){return method;}
    public void setMethod(String m){this.method=m;}
    public String getReference(){return reference;}
    public void setReference(String r){this.reference=r;}
    public OffsetDateTime getPaidAt(){return paidAt;}
}

// Audit log (append-only)
@Entity
@Table(name = "audit_log")
class AuditLog {
    @Id private String id = UUID.randomUUID().toString();
    private String companyId;
    private String entityName;
    private String entityId;
    private String action;
    private String actorId;
    private String detailsJson;
    private OffsetDateTime ts = OffsetDateTime.now();
    // getters/setters
    public String getId(){return id;}
    public void setId(String id){this.id=id;}
    public String getCompanyId(){return companyId;}
    public void setCompanyId(String c){this.companyId=c;}
    public String getEntityName(){return entityName;}
    public void setEntityName(String e){this.entityName=e;}
    public String getEntityId(){return entityId;}
    public void setEntityId(String i){this.entityId=i;}
    public String getAction(){return action;}
    public void setAction(String a){this.action=a;}
    public String getActorId(){return actorId;}
    public void setActorId(String a){this.actorId=a;}
    public String getDetailsJson(){return detailsJson;}
    public void setDetailsJson(String d){this.detailsJson=d;}
    public OffsetDateTime getTs(){return ts;}
}

// ============================
// Spring Data JPA repositories
// ============================
interface TenantRepository extends JpaRepository<TenantMeta, UUID> {
    Optional<TenantMeta> findBySlug(String slug);
}

interface GlobalUserRepository extends JpaRepository<GlobalUser, UUID> {
    Optional<GlobalUser> findByEmail(String email);
}

// Tenant-scoped repositories - JPA will pick them up using the tenant-aware datasource (search_path)
interface CompanyRepository extends JpaRepository<Company, String> {}
interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByUsernameAndCompanyId(String username, String companyId);
}
interface CustomerRepository extends JpaRepository<Customer, String> {
    List<Customer> findByCompanyIdAndNameContainingIgnoreCase(String companyId, String q);
}
interface ProductRepository extends JpaRepository<Product, String> {
    List<Product> findByCompanyIdAndNameContainingIgnoreCase(String companyId, String q);
}
interface TaxRepository extends JpaRepository<Tax, String> {}
interface InvoiceRepository extends JpaRepository<Invoice, String> {
    List<Invoice> findByCompanyIdAndDateBetween(String companyId, OffsetDateTime from, OffsetDateTime to);
}
interface InvoiceLineRepository extends JpaRepository<InvoiceLine, String> {
    List<InvoiceLine> findByInvoiceId(String invoiceId);
}
interface PaymentRepository extends JpaRepository<Payment, String> {}
interface AuditLogRepository extends JpaRepository<AuditLog, String> {}

// ============================
// Plugin SPI interfaces (ServiceLoader)
// ============================
interface InvoicePlugin {
    Invoice beforePersist(Invoice invoice);
    void afterPersist(Invoice invoice);
}

interface ExportPlugin {
    boolean export(String entityType, String entityId, OutputStream out) throws Exception;
}

interface TaxStrategy {
    BigDecimal calculateTax(InvoiceLine line, Tax tax);
}

// ============================
// Simple sample plugin loader that uses ServiceLoader
// ============================
@Component
class PluginManager {
    private final List<InvoicePlugin> invoicePlugins;
    private final List<ExportPlugin> exportPlugins;
    private final List<TaxStrategy> taxStrategies;

    public PluginManager() {
        invoicePlugins = new ArrayList<>();
        exportPlugins = new ArrayList<>();
        taxStrategies = new ArrayList<>();
        ServiceLoader.load(InvoicePlugin.class).forEach(invoicePlugins::add);
        ServiceLoader.load(ExportPlugin.class).forEach(exportPlugins::add);
        ServiceLoader.load(TaxStrategy.class).forEach(taxStrategies::add);
    }

    public List<InvoicePlugin> getInvoicePlugins(){ return invoicePlugins; }
    public List<ExportPlugin> getExportPlugins(){ return exportPlugins; }
    public List<TaxStrategy> getTaxStrategies(){ return taxStrategies; }
}

// ============================
// Services: InvoiceService with basic billing engine + plugin hooks
// ============================
@Service
class InvoiceService {
    @Autowired InvoiceRepository invoiceRepo;
    @Autowired InvoiceLineRepository lineRepo;
    @Autowired TaxRepository taxRepo;
    @Autowired PluginManager pluginManager;
    @Autowired AuditService auditService;

    @Transactional
    public Invoice createAndPersist(Invoice invoice, String actorId) {
        // compute subtotal
        BigDecimal subtotal = BigDecimal.ZERO;
        for (InvoiceLine l : lineRepo.findAllById(
                invoice.getId()!=null ? Collections.emptyList() : Collections.emptyList())) {
            // not used; assuming lines in separate payload; for demo we'll compute below
        }

        // assume invoice has lines attached externally, compute using repository payload
        // In this simplified demo, caller will send Invoice and lines separately in controller.
        // We'll compute totals when controller invokes this service after saving lines.

        // plugin hooks (before persist)
        for (InvoicePlugin p : pluginManager.getInvoicePlugins()) {
            invoice = p.beforePersist(invoice);
        }

        Invoice saved = invoiceRepo.save(invoice);

        // after persist hooks
        for (InvoicePlugin p : pluginManager.getInvoicePlugins()) {
            p.afterPersist(saved);
        }

        auditService.log(saved.getCompanyId(), "Invoice", saved.getId(), "CREATE", actorId, "{}");
        return saved;
    }

    // compute totals given invoice id
    @Transactional(readOnly = true)
    public Invoice computeTotals(Invoice invoice) {
        List<InvoiceLine> lines = lineRepo.findByInvoiceId(invoice.getId());
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (InvoiceLine l : lines) {
            BigDecimal line = l.getUnitPrice().multiply(l.getQty());
            l.setLineTotal(line);
            subtotal = subtotal.add(line);

            // tax
            if (l.getTaxId() != null) {
                Optional<Tax> ot = taxRepo.findById(l.getTaxId());
                if (ot.isPresent()) {
                    Tax tax = ot.get();
                    BigDecimal t = BigDecimal.ZERO;
                    if ("percentage".equalsIgnoreCase(tax.getType())) {
                        t = line.multiply(tax.getRate()).divide(BigDecimal.valueOf(100));
                    } else {
                        t = tax.getRate();
                    }
                    // plugin tax strategies override
                    for (TaxStrategy ts : pluginManager.getTaxStrategies()) {
                        t = ts.calculateTax(l, tax);
                    }
                    taxTotal = taxTotal.add(t);
                }
            }
        }
        invoice.setSubtotal(subtotal);
        invoice.setTaxTotal(taxTotal);
        BigDecimal discount = invoice.getDiscount() == null ? BigDecimal.ZERO : invoice.getDiscount();
        BigDecimal total = subtotal.add(taxTotal).subtract(discount);
        // rounding (example: round to 2 decimals)
        total = total.setScale(2, BigDecimal.ROUND_HALF_UP);
        invoice.setTotal(total);
        return invoice;
    }
}

// ============================
// Audit service
// ============================
@Service
class AuditService {
    @Autowired AuditLogRepository auditRepo;
    @Transactional
    public void log(String companyId, String entity, String entityId, String action, String actorId, String detailsJson) {
        AuditLog a = new AuditLog();
        a.setCompanyId(companyId);
        a.setEntityName(entity);
        a.setEntityId(entityId);
        a.setAction(action);
        a.setActorId(actorId);
        a.setDetailsJson(detailsJson);
        auditRepo.save(a);
    }
}

// ============================
// PDF Export service (Apache PDFBox)
// ============================
@Service
class PdfExportService {
    @Autowired InvoiceRepository invoiceRepo;
    @Autowired InvoiceLineRepository lineRepo;
    @Autowired CustomerRepository customerRepo;

    public byte[] exportInvoicePdf(String invoiceId) throws IOException {
        Invoice inv = invoiceRepo.findById(invoiceId).orElseThrow(() -> new RuntimeException("Not found"));
        List<InvoiceLine> lines = lineRepo.findByInvoiceId(invoiceId);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(40, 780);
                cs.showText("Invoice: " + inv.getInvoiceNumber());
                cs.endText();

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(40, 760);
                cs.showText("Date: " + inv.getDate().toString());
                cs.endText();

                if (inv.getCustomerId() != null) {
                    customerRepo.findById(inv.getCustomerId()).ifPresent(c -> {
                        try {
                            cs.beginText();
                            cs.setFont(PDType1Font.HELVETICA, 11);
                            cs.newLineAtOffset(40, 740);
                            cs.showText("Bill To: " + c.getName() + " | " + c.getPhone());
                            cs.endText();
                        } catch (IOException e) { /* ignore for demo */ }
                    });
                }

                float y = 700;
                for (InvoiceLine l : lines) {
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 10);
                    cs.newLineAtOffset(40, y);
                    String lineText = String.format("%s x%s @ %s = %s",
                            l.getDescription(),
                            l.getQty().toPlainString(),
                            l.getUnitPrice().toPlainString(),
                            l.getLineTotal().toPlainString());
                    cs.showText(lineText);
                    cs.endText();
                    y -= 14;
                    if (y < 80) { break; }
                }
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(40, y - 10);
                cs.showText("Subtotal: " + inv.getSubtotal() + " Tax: " + inv.getTaxTotal() + " Total: " + inv.getTotal());
                cs.endText();
            }
            doc.save(out);
        }
        return out.toByteArray();
    }
}

// ============================
// Tenant provisioning service (creates schema + tables)
// ============================
@Service
class TenantProvisionService {
    @Autowired DataSource dataSource; // base datasource (not wrapped)
    @Autowired TenantRepository tenantRepo;

    @Transactional
    public TenantMeta provisionTenant(String slug, String name) throws SQLException {
        String schema = "tenant_" + slug.replaceAll("[^a-zA-Z0-9_]", "");
        // create metadata row
        TenantMeta meta = new TenantMeta();
        meta.setSlug(slug);
        meta.setName(name);
        meta.setDbSchema(schema);
        tenantRepo.save(meta);

        // create schema and tables
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            // create minimal tables in tenant schema - for demo create customer & product & invoice
            String createCustomer = "CREATE TABLE IF NOT EXISTS " + schema + ".customer (" +
                    "id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text, phone text, email text)";
            String createProduct = "CREATE TABLE IF NOT EXISTS " + schema + ".product (" +
                    "id uuid PRIMARY KEY DEFAULT gen_random_uuid(), name text, price numeric)";
            String createInvoice = "CREATE TABLE IF NOT EXISTS " + schema + ".invoice (" +
                    "id uuid PRIMARY KEY DEFAULT gen_random_uuid(), invoice_number text, subtotal numeric, total numeric)";
            s.execute(createCustomer);
            s.execute(createProduct);
            s.execute(createInvoice);
        }
        return meta;
    }
}

// ============================
// Controllers: Auth, Tenant Provision, CRUD, Sync, Export
// ============================
@RestController
@RequestMapping("/auth")
class AuthController {
    @Autowired GlobalUserRepository guRepo;
    @Autowired TenantRepository tenantRepo;

    @PostMapping("/login")
    public ResponseEntity<Map<String,String>> login(@RequestBody Map<String,String> body) {
        String email = body.get("email");
        String tenantSlug = body.get("tenant");
        Optional<GlobalUser> gu = guRepo.findByEmail(email);
        if (gu.isPresent() && gu.get().getPasswordHash().equals(body.getOrDefault("password",""))) {
            // demo token: tenant:<slug>;user:<email>;roles:admin
            String token = "tenant:" + (tenantSlug == null ? "tenant_default" : tenantSlug) + ";user:" + email + ";roles:admin";
            Map<String,String> out = new HashMap<>();
            out.put("token", token);
            return ResponseEntity.ok(out);
        } else {
            return ResponseEntity.status(401).build();
        }
    }
}

@RestController
@RequestMapping("/admin")
class AdminController {
    @Autowired TenantProvisionService provisionService;

    @PostMapping("/provision")
    public ResponseEntity<TenantMeta> provision(@RequestBody Map<String,String> body) {
        try {
            String slug = body.get("slug");
            String name = body.get("name");
            TenantMeta t = provisionService.provisionTenant(slug, name);
            return ResponseEntity.ok(t);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }
}

@RestController
@RequestMapping("/api/customers")
class CustomerController {
    @Autowired CustomerRepository customerRepo;
    @Autowired AuditService auditService;

    // tenant context is derived from JWT filter
    @GetMapping("/search")
    public List<Customer> search(@RequestParam("q") String q) {
        String tenant = TenantContext.getTenant();
        String companyId = tenant; // demo: map tenant->companyId; adapt in prod
        if (tenant == null) companyId = "default_company";
        return customerRepo.findByCompanyIdAndNameContainingIgnoreCase(companyId, q);
    }

    @PostMapping
    public Customer create(@RequestBody Customer c, @RequestHeader(value="X-Actor", required=false) String actor) {
        if (TenantContext.getTenant()==null) c.setCompanyId("tenant_default");
        else c.setCompanyId(TenantContext.getTenant());
        Customer saved = customerRepo.save(c);
        auditService.log(saved.getCompanyId(), "Customer", saved.getId(), "CREATE", actor==null?"system":actor, "{}");
        return saved;
    }
}

@RestController
@RequestMapping("/api/products")
class ProductController {
    @Autowired ProductRepository productRepo;
    @Autowired AuditService audit;
    @GetMapping("/search")
    public List<Product> search(@RequestParam("q") String q) {
        String tenant = TenantContext.getTenant();
        String companyId = tenant==null? "tenant_default" : tenant;
        return productRepo.findByCompanyIdAndNameContainingIgnoreCase(companyId, q);
    }
    @PostMapping
    public Product create(@RequestBody Product p, @RequestHeader(value="X-Actor", required=false) String actor) {
        p.setCompanyId(TenantContext.getTenant());
        Product s = productRepo.save(p);
        audit.log(s.getCompanyId(), "Product", s.getId(), "CREATE", actor==null?"system":actor, "{}");
        return s;
    }
}

@RestController
@RequestMapping("/api/invoices")
class InvoiceController {
    @Autowired InvoiceRepository invoiceRepo;
    @Autowired InvoiceLineRepository lineRepo;
    @Autowired InvoiceService invoiceService;
    @Autowired PdfExportService pdfExportService;
    @Autowired AuditService auditService;

    @PostMapping("/create")
    @Transactional
    public ResponseEntity<Invoice> createInvoice(@RequestBody Map<String,Object> payload,
                                                 @RequestHeader(value="X-Actor", required=false) String actor) {
        // payload expected: invoice: {...}, lines: [{...},...]
        Map<String,Object> invoiceMap = (Map<String,Object>) payload.get("invoice");
        List<Map<String,Object>> lines = (List<Map<String,Object>>) payload.getOrDefault("lines", Collections.emptyList());
        Invoice inv = mapToInvoice(invoiceMap);
        inv.setCompanyId(TenantContext.getTenant()==null?"tenant_default":TenantContext.getTenant());
        // generate invoice number (simple)
        inv.setInvoiceNumber("INV-" + System.currentTimeMillis());
        inv = invoiceRepo.save(inv);
        // save lines
        for (Map<String,Object> lmap : lines) {
            InvoiceLine l = mapToInvoiceLine(lmap);
            l.setInvoiceId(inv.getId());
            // compute line total
            BigDecimal lineTotal = l.getUnitPrice().multiply(l.getQty());
            l.setLineTotal(lineTotal);
            lineRepo.save(l);
        }
        // compute totals
        inv = invoiceService.computeTotals(inv);
        inv = invoiceRepo.save(inv);
        auditService.log(inv.getCompanyId(), "Invoice", inv.getId(), "CREATE", actor==null?"system":actor, "{}");
        return ResponseEntity.ok(inv);
    }

    @GetMapping("/{id}/export/pdf")
    public ResponseEntity<byte[]> exportPdf(@PathVariable String id) {
        try {
            byte[] pdf = pdfExportService.exportInvoicePdf(id);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=invoice-" + id + ".pdf")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // simple sync push endpoint (client pushes invoices + lines)
    @PostMapping("/sync/push")
    @Transactional
    public ResponseEntity<Map<String,Object>> syncPush(@RequestBody Map<String,Object> payload) {
        // payload contains arrays: invoices, invoice_lines, customers, products
        // For demo implement invoices only
        List<Map<String,Object>> invoices = (List<Map<String,Object>>) payload.getOrDefault("invoices", Collections.emptyList());
        List<String> ids = new ArrayList<>();
        for (Map<String,Object> im : invoices) {
            Invoice inv = mapToInvoice(im);
            inv.setCompanyId(TenantContext.getTenant()==null?"tenant_default":TenantContext.getTenant());
            invoiceRepo.save(inv);
            ids.add(inv.getId());
        }
        Map<String,Object> resp = new HashMap<>();
        resp.put("synced", ids.size());
        return ResponseEntity.ok(resp);
    }

    // helper mappers
    private Invoice mapToInvoice(Map<String,Object> m) {
        Invoice i = new Invoice();
        if (m.containsKey("id")) i.setId((String)m.get("id"));
        if (m.containsKey("customerId")) i.setCustomerId((String)m.get("customerId"));
        if (m.containsKey("currency")) i.setCurrency((String)m.get("currency"));
        if (m.containsKey("discount")) i.setDiscount(new BigDecimal(m.get("discount").toString()));
        return i;
    }
    private InvoiceLine mapToInvoiceLine(Map<String,Object> m) {
        InvoiceLine l = new InvoiceLine();
        if (m.containsKey("id")) l.setId((String)m.get("id"));
        if (m.containsKey("productId")) l.setProductId((String)m.get("productId"));
        if (m.containsKey("description")) l.setDescription((String)m.get("description"));
        if (m.containsKey("qty")) l.setQty(new BigDecimal(m.get("qty").toString()));
        if (m.containsKey("unitPrice")) l.setUnitPrice(new BigDecimal(m.get("unitPrice").toString()));
        if (m.containsKey("taxId")) l.setTaxId((String)m.get("taxId"));
        return l;
    }
}

// ============================
// Reporting Controller (basic summaries)
// ============================
@RestController
@RequestMapping("/api/reports")
class ReportingController {
    @Autowired InvoiceRepository invoiceRepo;

    @GetMapping("/summary")
    public Map<String,Object> summary(@RequestParam(value="from", required=false) String fromStr,
                                      @RequestParam(value="to", required=false) String toStr) {
        OffsetDateTime from = fromStr==null ? OffsetDateTime.now().minusDays(30) : OffsetDateTime.parse(fromStr);
        OffsetDateTime to = toStr==null ? OffsetDateTime.now() : OffsetDateTime.parse(toStr);
        String companyId = TenantContext.getTenant()==null?"tenant_default":TenantContext.getTenant();
        List<Invoice> invoices = invoiceRepo.findByCompanyIdAndDateBetween(companyId, from, to);
        BigDecimal total = invoices.stream().map(Invoice::getTotal).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String,Object> out = new HashMap<>();
        out.put("count", invoices.size());
        out.put("total", total);
        return out;
    }
}

// ============================
// Simple Sync Controller (pull endpoint)
// ============================
@RestController
@RequestMapping("/api/sync")
class SyncController {
    @Autowired InvoiceRepository invoiceRepo;
    @Autowired InvoiceLineRepository lineRepo;
    @GetMapping("/pull")
    public Map<String,Object> pull(@RequestParam(value="since", required=false) String since) {
        // demo: return last 100 invoices for tenant
        String companyId = TenantContext.getTenant()==null?"tenant_default":TenantContext.getTenant();
        List<Invoice> invoices = invoiceRepo.findAll().stream()
                .filter(i -> companyId.equals(i.getCompanyId()))
                .limit(100)
                .collect(Collectors.toList());
        Map<String,Object> out = new HashMap<>();
        out.put("invoices", invoices);
        return out;
    }
}


