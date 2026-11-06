node -e "
const fs = require('fs');
fs.writeFileSync('temporal.md', \`# DOKUMENTASI DAN KODE LENGKAP TEMPORAL BACKEND APPROVAL MVP

File ini memuat seluruh source code backend (Spring Boot, H2 Database, dan Temporal.io) beserta penjelasan teknis mendalam mengenai setiap bagian yang berkaitan dengan Temporal.

---

## DAFTAR ISI
1. Arsitektur & Konsep Alur Temporal
2. File Konfigurasi Build (build.gradle & application.properties)
3. Domain Entities, Repositories & Dummy Data Seeder
4. Data Transfer Object (DTO) & Rule Evaluator
5. Temporal Workflow (Interface & Implementasi)
6. Temporal Activity (Interface & Implementasi Database H2)
7. Konfigurasi Temporal Worker & Client
8. REST API Controller (Integrasi Signal & Query)
9. Panduan Menjalankan, Observabilitas & Pengujian cURL

---

## 1. ARSITEKTUR & KONSEP ALUR TEMPORAL

### Mengapa Menggunakan Temporal?
Dalam sistem approval berjenjang konvensional, penanganan jeda waktu antar persetujuan (*approval delays*) biasanya membutuhkan polling database berulang atau cron job yang rawan race-condition dan memory leak.

Temporal menyelesaikan masalah ini dengan konsep **Durable Execution**:
- **Workflow State Tersimpan Otomatis:** Saat menunggu keputusan manusia, workflow berada dalam kondisi \`Workflow.await(...)\` yang memarkir eksekusi tanpa memakan thread CPU atau koneksi database aktif.
- **Tahan Gangguan (Fault Tolerant):** Jika server Spring Boot mati atau listrik padam saat menunggu persetujuan Direktur, saat server kembali hidup Temporal akan melanjutkan workflow tepat pada titik terakhir secara deterministik.
- **Mekanisme Pick Me (Claim):** Menggunakan fitur **Temporal Signal** untuk mengunci order ke satu approver tertentu sebelum keputusan disetujui/ditolak.

---

## 2. FILE KONFIGURASI BUILD

### build.gradle
\`\`\`groovy
plugins {
id 'java'
id 'org.springframework.boot' version '3.2.4'
id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.example'
version = '0.0.1-SNAPSHOT'
java {
sourceCompatibility = '17'
}

repositories {
mavenCentral()
}

dependencies {
implementation 'org.springframework.boot:spring-boot-starter-web'
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
runtimeOnly 'com.h2database:h2'

    // TEMPORAL SDK RESMI
    implementation 'io.temporal:temporal-sdk:1.24.1'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
}
\`\`\`
**Penjelasan:**
- Dependensi \`io.temporal:temporal-sdk\` menyediakan modul inti: Workflow runtime, Activity runner, gRPC Client stub untuk koneksi ke Temporal Server (port 7233).

### src/main/resources/application.properties
\`\`\`properties
spring.application.name=approval-backend
server.port=8080

# Database H2 In-Memory
spring.datasource.url=jdbc:h2:mem:approvaldb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

# H2 Web Console
spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

# JPA Hibernate
spring.jpa.database-platform=org.hibernate.dialect.H2Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=false
\`\`\`

---

## 3. DOMAIN ENTITIES, REPOSITORIES & DUMMY DATA SEEDER

### Role.java
\`\`\`java
package com.example.approval.model;

public enum Role {
EMPLOYEE,
MANAGER,
FINANCE,
DIRECTOR,
CEO
}
\`\`\`

### UserEntity.java
\`\`\`java
package com.example.approval.entity;

import com.example.approval.model.Role;
import jakarta.persistence.*;

@Entity
@Table(name = \"users\")
public class UserEntity {
@Id
private String id;
private String name;
private String email;

    @Enumerated(EnumType.STRING)
    private Role role;

    public UserEntity() {}
    public UserEntity(String id, String name, String email, Role role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
}
\`\`\`

### OrderEntity.java
\`\`\`java
package com.example.approval.entity;

import com.example.approval.model.OrderDTO.*;
import com.example.approval.model.Role;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = \"orders\")
public class OrderEntity {
@Id
private String orderId;

    private String requesterId;

    @Enumerated(EnumType.STRING)
    private OrderType type;

    private Integer days;
    private BigDecimal amount;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    private Status status;

    private int currentStepIndex = 0;

    @Enumerated(EnumType.STRING)
    private Role waitingForRole;

    private String claimedById;
    private String claimedByName;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = \"order_approval_chains\", joinColumns = @JoinColumn(name = \"order_id\"))
    @Enumerated(EnumType.STRING)
    @Column(name = \"role\")
    private List<Role> approvalChain = new ArrayList<>();

    @OneToMany(mappedBy = \"order\", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    @JsonManagedReference
    private List<ApprovalHistoryEntity> history = new ArrayList<>();

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    public OrderEntity() {}

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getRequesterId() { return requesterId; }
    public void setRequesterId(String requesterId) { this.requesterId = requesterId; }
    public OrderType getType() { return type; }
    public void setType(OrderType type) { this.type = type; }
    public Integer getDays() { return days; }
    public void setDays(Integer days) { this.days = days; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public int getCurrentStepIndex() { return currentStepIndex; }
    public void setCurrentStepIndex(int currentStepIndex) { this.currentStepIndex = currentStepIndex; }
    public Role getWaitingForRole() { return waitingForRole; }
    public void setWaitingForRole(Role waitingForRole) { this.waitingForRole = waitingForRole; }
    public String getClaimedById() { return claimedById; }
    public void setClaimedById(String claimedById) { this.claimedById = claimedById; }
    public String getClaimedByName() { return claimedByName; }
    public void setClaimedByName(String claimedByName) { this.claimedByName = claimedByName; }
    public List<Role> getApprovalChain() { return approvalChain; }
    public void setApprovalChain(List<Role> approvalChain) { this.approvalChain = approvalChain; }
    public List<ApprovalHistoryEntity> getHistory() { return history; }
    public void setHistory(List<ApprovalHistoryEntity> history) { this.history = history; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
\`\`\`

### ApprovalHistoryEntity.java
\`\`\`java
package com.example.approval.entity;

import com.example.approval.model.Role;
import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = \"approval_history\")
public class ApprovalHistoryEntity {
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = \"order_id\")
    @JsonBackReference
    private OrderEntity order;

    private String approverId;

    @Enumerated(EnumType.STRING)
    private Role role;

    private boolean approved;

    @Column(length = 1000)
    private String notes;

    private LocalDateTime timestamp = LocalDateTime.now();

    public ApprovalHistoryEntity() {}
    public ApprovalHistoryEntity(OrderEntity order, String approverId, Role role, boolean approved, String notes) {
        this.order = order;
        this.approverId = approverId;
        this.role = role;
        this.approved = approved;
        this.notes = notes;
    }

    public Long getId() { return id; }
    public OrderEntity getOrder() { return order; }
    public void setOrder(OrderEntity order) { this.order = order; }
    public String getApproverId() { return approverId; }
    public void setApproverId(String approverId) { this.approverId = approverId; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
\`\`\`

### Repositories (Spring Data JPA)
\`\`\`java
package com.example.approval.repository;

import com.example.approval.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {}

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {}

@Repository
public interface ApprovalHistoryRepository extends JpaRepository<ApprovalHistoryEntity, Long> {}
\`\`\`

### DataInitializer.java (2 User per Role untuk Uji Coba Pick Me)
\`\`\`java
package com.example.approval.config;

import com.example.approval.entity.UserEntity;
import com.example.approval.model.Role;
import com.example.approval.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
public class DataInitializer {

    @Bean
    public CommandLineRunner initDatabase(UserRepository userRepository) {
        return args -> {
            userRepository.saveAll(List.of(
                new UserEntity(\"USR-EMP1\", \"Budi Santoso\", \"budi@office.com\", Role.EMPLOYEE),
                new UserEntity(\"USR-EMP2\", \"Siti Rahma\", \"siti@office.com\", Role.EMPLOYEE),
                new UserEntity(\"USR-MGR1\", \"Pak Toni (Manager 1)\", \"toni.mgr1@office.com\", Role.MANAGER),
                new UserEntity(\"USR-MGR2\", \"Pak Agus (Manager 2)\", \"agus.mgr2@office.com\", Role.MANAGER),
                new UserEntity(\"USR-FIN1\", \"Bu Lisa (Finance 1)\", \"lisa.fin1@office.com\", Role.FINANCE),
                new UserEntity(\"USR-FIN2\", \"Bu Maya (Finance 2)\", \"maya.fin2@office.com\", Role.FINANCE),
                new UserEntity(\"USR-DIR1\", \"Pak Hartono (Director 1)\", \"hartono.dir1@office.com\", Role.DIRECTOR),
                new UserEntity(\"USR-DIR2\", \"Pak Bambang (Director 2)\", \"bambang.dir2@office.com\", Role.DIRECTOR),
                new UserEntity(\"USR-CEO1\", \"Pak Hendra (CEO 1)\", \"hendra.ceo1@office.com\", Role.CEO),
                new UserEntity(\"USR-CEO2\", \"Pak Rudi (CEO 2)\", \"rudi.ceo2@office.com\", Role.CEO)
            ));
        };
    }
}
\`\`\`

---

## 4. DATA TRANSFER OBJECT (DTO) & RULE EVALUATOR

### OrderDTO.java
\`\`\`java
package com.example.approval.model;

import java.math.BigDecimal;
import java.util.List;

public class OrderDTO {
public enum OrderType { IJIN_CUTI, BUY }
public enum Status { PENDING, IN_PROGRESS, APPROVED, REJECTED }

    public record CreateRequest(
        String orderId,
        String requesterId,
        OrderType type,
        Integer days,
        BigDecimal amount,
        String notes
    ) {}

    public record ClaimRequest(
        String approverId,
        String approverName,
        Role role,
        boolean release
    ) {}

    public record ApprovalDecision(
        String approverId,
        Role role,
        boolean approved,
        String notes
    ) {}

    public record OrderState(
        String orderId,
        OrderType type,
        Status status,
        List<Role> approvalChain,
        int currentStepIndex,
        Role waitingForRole,
        String claimedById,
        String claimedByName,
        List<ApprovalDecision> history
    ) {}
}
\`\`\`

### ApprovalRuleEvaluator.java
\`\`\`java
package com.example.approval.service;

import com.example.approval.model.OrderDTO.OrderType;
import com.example.approval.model.Role;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ApprovalRuleEvaluator {

    public static List<Role> determineRequiredApprovals(OrderType type, Integer days, BigDecimal amount) {
        List<Role> chain = new ArrayList<>();

        if (type == OrderType.IJIN_CUTI) {
            // Aturan Bisnis Cuti:
            // Cuti <= 1 Hari -> Auto Approve (Chain Kosong)
            // Cuti > 2 Hari  -> Perlu MANAGER
            if (days != null && days > 2) {
                chain.add(Role.MANAGER);
            }
        } else if (type == OrderType.BUY) {
            // Aturan Bisnis BUY:
            // <= 1.000.000             -> FINANCE
            // > 1.000.000 s/d 10.000.000 -> FINANCE -> DIRECTOR
            // > 10.000.000 s/d 100.000.000 -> FINANCE -> DIRECTOR -> CEO
            BigDecimal val = amount != null ? amount : BigDecimal.ZERO;
            BigDecimal oneMillion = new BigDecimal(\"1000000\");
            BigDecimal tenMillion = new BigDecimal(\"10000000\");
            BigDecimal hundredMillion = new BigDecimal(\"100000000\");

            if (val.compareTo(oneMillion) <= 0) {
                chain.add(Role.FINANCE);
            } else if (val.compareTo(tenMillion) <= 0) {
                chain.add(Role.FINANCE);
                chain.add(Role.DIRECTOR);
            } else if (val.compareTo(hundredMillion) <= 0) {
                chain.add(Role.FINANCE);
                chain.add(Role.DIRECTOR);
                chain.add(Role.CEO);
            }
        }
        return chain;
    }
}
\`\`\`

---

## 5. TEMPORAL WORKFLOW (INTERFACE & IMPLEMENTASI)

### OrderApprovalWorkflow.java (Interface)
\`\`\`java
package com.example.approval.temporal;

import com.example.approval.model.OrderDTO.*;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderApprovalWorkflow {

    @WorkflowMethod
    void processOrder(CreateRequest request);

    @SignalMethod
    void claimOrder(ClaimRequest claim);

    @SignalMethod
    void submitApproval(ApprovalDecision decision);

    @QueryMethod
    OrderState getOrderState();
}
\`\`\`
**Penjelasan Anotasi Temporal:**
- **\`@WorkflowInterface\`**: Menandai interface Java ini sebagai kontrak alur Temporal.
- **\`@WorkflowMethod\`**: Entry point alur utama yang berjalan asynchronous.
- **\`@SignalMethod\`**: Jalur komunikasi satu arah untuk menerima pesan luar (sinyal tombol Pick Me dan Approve/Reject) ke workflow yang sedang diparkir.
- **\`@QueryMethod\`**: Jalur inspeksi langsung (read-only) untuk membaca variabel state alur tanpa memicu side-effect.

### OrderApprovalWorkflowImpl.java (Implementasi Alur Deterministik)
\`\`\`java
package com.example.approval.temporal;

import com.example.approval.model.OrderDTO.*;
import com.example.approval.model.Role;
import com.example.approval.service.ApprovalRuleEvaluator;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class OrderApprovalWorkflowImpl implements OrderApprovalWorkflow {

    // Jembatan Activity ke Database dengan Timeout 10 Detik
    private final OrderActivities activities = Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(10))
                    .build()
    );

    private String orderId;
    private OrderType orderType;
    private Status status = Status.PENDING;
    private List<Role> approvalChain = new ArrayList<>();
    private int currentStepIndex = 0;
    private final List<ApprovalDecision> history = new ArrayList<>();

    // Variabel Sinyal
    private ClaimRequest currentClaim = null;
    private ClaimRequest pendingClaimSignal = null;
    private ApprovalDecision pendingDecision = null;

    @Override
    public void processOrder(CreateRequest request) {
        this.orderId = request.orderId();
        this.orderType = request.type();

        this.approvalChain = ApprovalRuleEvaluator.determineRequiredApprovals(
                request.type(), request.days(), request.amount()
        );

        // Kasus Cuti <= 1 Hari (Auto Approve)
        if (approvalChain.isEmpty()) {
            this.status = Status.APPROVED;
            activities.saveInitialOrder(request, approvalChain, Status.APPROVED);
            return;
        }

        this.status = Status.IN_PROGRESS;
        activities.saveInitialOrder(request, approvalChain, Status.IN_PROGRESS);

        // Loop Berjenjang: MANAGER -> FINANCE -> DIRECTOR -> CEO
        while (currentStepIndex < approvalChain.size()) {
            Role targetRole = approvalChain.get(currentStepIndex);

            this.currentClaim = null;
            this.pendingClaimSignal = null;
            this.pendingDecision = null;

            activities.updateWaitingState(orderId, currentStepIndex, targetRole);

            // Sub-loop: Menunggu Pick Me -> Kemudian menunggu keputusan dari claimant tersebut
            while (true) {
                // JEDA DETERMINISTIK: Menunggu sinyal masuk
                Workflow.await(() -> pendingClaimSignal != null || pendingDecision != null);

                // 1. Tangani Sinyal Pick Me / Release
                if (pendingClaimSignal != null) {
                    if (pendingClaimSignal.role() == targetRole) {
                        if (pendingClaimSignal.release()) {
                            this.currentClaim = null;
                            activities.updateClaimState(orderId, null, null);
                        } else {
                            this.currentClaim = pendingClaimSignal;
                            activities.updateClaimState(orderId, pendingClaimSignal.approverId(), pendingClaimSignal.approverName());
                        }
                    }
                    pendingClaimSignal = null;
                }

                // 2. Tangani Sinyal Approve / Reject
                if (pendingDecision != null) {
                    // Hanya sah jika sudah di-claim dan diputuskan oleh claimant tersebut
                    if (currentClaim != null && pendingDecision.approverId().equals(currentClaim.approverId())) {
                        break; // Keputusan sah, keluar dari sub-loop
                    }
                    pendingDecision = null; // Buang keputusan tidak sah
                }
            }

            history.add(pendingDecision);
            activities.recordDecision(orderId, pendingDecision);

            if (!pendingDecision.approved()) {
                this.status = Status.REJECTED;
                activities.finalizeStatus(orderId, Status.REJECTED);
                return;
            }

            pendingDecision = null;
            currentClaim = null;
            currentStepIndex++;
        }

        this.status = Status.APPROVED;
        activities.finalizeStatus(orderId, Status.APPROVED);
    }

    @Override
    public void claimOrder(ClaimRequest claim) {
        this.pendingClaimSignal = claim;
    }

    @Override
    public void submitApproval(ApprovalDecision decision) {
        this.pendingDecision = decision;
    }

    @Override
    public OrderState getOrderState() {
        Role waitingFor = (status == Status.IN_PROGRESS && currentStepIndex < approvalChain.size())
                ? approvalChain.get(currentStepIndex)
                : null;
        String claimedId = currentClaim != null ? currentClaim.approverId() : null;
        String claimedName = currentClaim != null ? currentClaim.approverName() : null;

        return new OrderState(orderId, orderType, status, approvalChain, currentStepIndex, waitingFor, claimedId, claimedName, history);
    }
}
\`\`\`
**Penjelasan Teknis:**
1. **Determinisme:** Kelas ini tidak memanggil repository database secara langsung demi memastikan *replay history* Temporal selalu konsisten.
2. **\`Workflow.await(...)\`**: Memarkir eksekusi workflow secara efisien tanpa mengunci thread memori JVM.
3. **Mekanisme Pick Me:** Variabel \`currentClaim\` memastikan tidak ada orang lain di role yang sama yang dapat menyetujui order sebelum order diklaim secara sah.

---

## 6. TEMPORAL ACTIVITY (INTERFACE & IMPLEMENTASI DATABASE H2)

Activity adalah satu-satunya komponen yang diperbolehkan berinteraksi dengan database JPA/H2.

### OrderActivities.java (Interface)
\`\`\`java
package com.example.approval.temporal;

import com.example.approval.model.OrderDTO.*;
import com.example.approval.model.Role;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;
import java.util.List;

@ActivityInterface
public interface OrderActivities {

    @ActivityMethod
    void saveInitialOrder(CreateRequest request, List<Role> approvalChain, Status status);

    @ActivityMethod
    void updateWaitingState(String orderId, int currentStepIndex, Role waitingForRole);

    @ActivityMethod
    void updateClaimState(String orderId, String claimedById, String claimedByName);

    @ActivityMethod
    void recordDecision(String orderId, ApprovalDecision decision);

    @ActivityMethod
    void finalizeStatus(String orderId, Status status);
}
\`\`\`

### OrderActivitiesImpl.java (Implementasi Database JPA)
\`\`\`java
package com.example.approval.temporal;

import com.example.approval.entity.ApprovalHistoryEntity;
import com.example.approval.entity.OrderEntity;
import com.example.approval.model.OrderDTO.*;
import com.example.approval.model.Role;
import com.example.approval.repository.ApprovalHistoryRepository;
import com.example.approval.repository.OrderRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OrderActivitiesImpl implements OrderActivities {

    private final OrderRepository orderRepository;
    private final ApprovalHistoryRepository historyRepository;

    public OrderActivitiesImpl(OrderRepository orderRepository, ApprovalHistoryRepository historyRepository) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    @Transactional
    public void saveInitialOrder(CreateRequest request, List<Role> approvalChain, Status status) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(request.orderId());
        order.setRequesterId(request.requesterId());
        order.setType(request.type());
        order.setDays(request.days());
        order.setAmount(request.amount());
        order.setNotes(request.notes());
        order.setStatus(status);
        order.setApprovalChain(approvalChain);
        order.setWaitingForRole(approvalChain.isEmpty() ? null : approvalChain.get(0));
        order.setClaimedById(null);
        order.setClaimedByName(null);
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void updateWaitingState(String orderId, int currentStepIndex, Role waitingForRole) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setCurrentStepIndex(currentStepIndex);
            order.setWaitingForRole(waitingForRole);
            order.setClaimedById(null); // Reset klaim saat naik jenjang
            order.setClaimedByName(null);
            order.setStatus(Status.IN_PROGRESS);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }

    @Override
    @Transactional
    public void updateClaimState(String orderId, String claimedById, String claimedByName) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setClaimedById(claimedById);
            order.setClaimedByName(claimedByName);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }

    @Override
    @Transactional
    public void recordDecision(String orderId, ApprovalDecision decision) {
        orderRepository.findById(orderId).ifPresent(order -> {
            ApprovalHistoryEntity history = new ApprovalHistoryEntity(
                order,
                decision.approverId(),
                decision.role(),
                decision.approved(),
                decision.notes()
            );
            historyRepository.save(history);
            order.getHistory().add(history);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }

    @Override
    @Transactional
    public void finalizeStatus(String orderId, Status status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            order.setWaitingForRole(null);
            order.setClaimedById(null);
            order.setClaimedByName(null);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }
}
\`\`\`

---

## 7. KONFIGURASI TEMPORAL WORKER & CLIENT

### TemporalConfig.java
\`\`\`java
package com.example.approval.config;

import com.example.approval.temporal.OrderActivitiesImpl;
import com.example.approval.temporal.OrderApprovalWorkflowImpl;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = \"ORDER_APPROVAL_TASK_QUEUE\";

    @Bean
    public WorkflowServiceStubs workflowServiceStubs() {
        return WorkflowServiceStubs.newLocalServiceStubs();
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs serviceStubs) {
        return WorkflowClient.newInstance(serviceStubs, WorkflowClientOptions.newBuilder().build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client, OrderActivitiesImpl orderActivities) {
        WorkerFactory factory = WorkerFactory.newInstance(client);
        Worker worker = factory.newWorker(TASK_QUEUE);

        // Daftarkan Definisi Workflow & Activity
        worker.registerWorkflowImplementationTypes(OrderApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(orderActivities);

        return factory;
    }

    @Configuration
    public static class WorkerLifecycle {
        private final WorkerFactory workerFactory;

        public WorkerLifecycle(WorkerFactory workerFactory) {
            this.workerFactory = workerFactory;
        }

        @PostConstruct
        public void start() {
            workerFactory.start();
        }

        @PreDestroy
        public void stop() {
            workerFactory.shutdown();
        }
    }
}
\`\`\`

---

## 8. REST API CONTROLLER (INTEGRASI SIGNAL & QUERY)

### OrderController.java
\`\`\`java
package com.example.approval.controller;

import com.example.approval.config.TemporalConfig;
import com.example.approval.entity.OrderEntity;
import com.example.approval.entity.UserEntity;
import com.example.approval.model.OrderDTO.*;
import com.example.approval.repository.OrderRepository;
import com.example.approval.repository.UserRepository;
import com.example.approval.temporal.OrderApprovalWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(\"/api\")
@CrossOrigin(\"*\")
public class OrderController {

    private final WorkflowClient workflowClient;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    public OrderController(WorkflowClient workflowClient, OrderRepository orderRepository, UserRepository userRepository) {
        this.workflowClient = workflowClient;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
    }

    @GetMapping(\"/users\")
    public List<UserEntity> getUsers() {
        return userRepository.findAll();
    }

    @GetMapping(\"/orders\")
    public List<OrderEntity> getAllOrders() {
        return orderRepository.findAll(Sort.by(Sort.Direction.DESC, \"createdAt\"));
    }

    @GetMapping(\"/orders/{orderId}\")
    public ResponseEntity<OrderEntity> getOrderDetail(@PathVariable String orderId) {
        return orderRepository.findById(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // 1. START WORKFLOW
    @PostMapping(\"/orders\")
    public ResponseEntity<?> createOrder(@RequestBody CreateRequest request) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(request.orderId())
                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                .build();

        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, options);
        WorkflowClient.start(workflow::processOrder, request);

        return ResponseEntity.ok(Map.of(
            \"message\", \"Order berhasil dibuat dan workflow aktif\",
            \"orderId\", request.orderId()
        ));
    }

    // 2. SIGNAL PICK ME (CLAIM)
    @PostMapping(\"/orders/{orderId}/claim\")
    public ResponseEntity<?> claimOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {

        String approverId = body.get(\"approverId\");
        UserEntity user = userRepository.findById(approverId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of(\"error\", \"User tidak ditemukan\"));
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();

        if (order.getWaitingForRole() != user.getRole()) {
            return ResponseEntity.badRequest().body(Map.of(
                \"error\", \"Role Anda (\" + user.getRole() + \") tidak sesuai dengan tahapan (\" + order.getWaitingForRole() + \")\"
            ));
        }

        if (order.getClaimedById() != null && !order.getClaimedById().equals(user.getId())) {
            return ResponseEntity.badRequest().body(Map.of(
                \"error\", \"Order ini sudah di-claim oleh \" + order.getClaimedByName()
            ));
        }

        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, orderId);
        ClaimRequest claim = new ClaimRequest(user.getId(), user.getName(), user.getRole(), false);
        workflow.claimOrder(claim);

        return ResponseEntity.ok(Map.of(
            \"message\", \"Order berhasil di-claim (Pick Me) oleh \" + user.getName(),
            \"claimedById\", user.getId(),
            \"claimedByName\", user.getName()
        ));
    }

    // 3. SIGNAL RELEASE CLAIM
    @PostMapping(\"/orders/{orderId}/release\")
    public ResponseEntity<?> releaseClaim(
            @PathVariable String orderId,
            @RequestBody Map<String, String> body) {

        String approverId = body.get(\"approverId\");
        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();

        if (order.getClaimedById() == null || !order.getClaimedById().equals(approverId)) {
            return ResponseEntity.badRequest().body(Map.of(\"error\", \"Anda bukan pemegang klaim order ini\"));
        }

        UserEntity user = userRepository.findById(approverId).orElseThrow();
        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, orderId);
        ClaimRequest release = new ClaimRequest(user.getId(), user.getName(), user.getRole(), true);
        workflow.claimOrder(release);

        return ResponseEntity.ok(Map.of(\"message\", \"Klaim order berhasil dilepaskan\"));
    }

    // 4. SIGNAL APPROVE / REJECT
    @PostMapping(\"/orders/{orderId}/decision\")
    public ResponseEntity<?> submitDecision(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body) {

        String approverId = (String) body.get(\"approverId\");
        boolean approved = (Boolean) body.get(\"approved\");
        String notes = (String) body.getOrDefault(\"notes\", \"\");

        UserEntity approver = userRepository.findById(approverId).orElse(null);
        if (approver == null) {
            return ResponseEntity.badRequest().body(Map.of(\"error\", \"Approver tidak ditemukan\"));
        }

        OrderEntity order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return ResponseEntity.notFound().build();

        if (order.getClaimedById() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                \"error\", \"Order belum di-Pick Me! Anda harus klik tombol 'Pick Me' terlebih dahulu.\"
            ));
        }

        if (!order.getClaimedById().equals(approver.getId())) {
            return ResponseEntity.badRequest().body(Map.of(
                \"error\", \"Order ini sedang dikunci oleh \" + order.getClaimedByName() + \". Anda tidak dapat memprosesnya.\"
            ));
        }

        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(OrderApprovalWorkflow.class, orderId);
        ApprovalDecision decision = new ApprovalDecision(approver.getId(), approver.getRole(), approved, notes);
        workflow.submitApproval(decision);

        return ResponseEntity.ok(Map.of(
            \"message\", \"Keputusan persetujuan berhasil diproses\",
            \"approver\", approver.getName()
        ));
    }
}
\`\`\`

---

## 9. PANDUAN MENJALANKAN, OBSERVABILITAS & PENGUJIAN CURL

### 1. Jalankan Temporal Server Lokal
\`\`\`bash
temporal server start-dev
\`\`\`
- Akses Temporal Web Dashboard: **http://localhost:8233**

### 2. Jalankan Spring Boot Backend
\`\`\`bash
./gradlew bootRun
\`\`\`
- Akses Database H2 Console: **http://localhost:8080/h2-console** (JDBC URL: \`jdbc:h2:mem:approvaldb\`, User: \`sa\`)

### 3. Pengujian via cURL

**A. Buat Order BUY Rp 5.000.000 (Menunggu FINANCE -> DIRECTOR)**
\`\`\`bash
curl -X POST http://localhost:8080/api/orders -H \"Content-Type: application/json\" -d '{
\"orderId\": \"ORD-501\",
\"requesterId\": \"Budi Santoso\",
\"type\": \"BUY\",
\"amount\": 5000000,
\"notes\": \"Pengadaan Switch Network\"
}'
\`\`\`

**B. Bu Lisa (FINANCE 1) Melakukan Pick Me**
\`\`\`bash
curl -X POST http://localhost:8080/api/orders/ORD-501/claim -H \"Content-Type: application/json\" -d '{
\"approverId\": \"USR-FIN1\"
}'
\`\`\`

**C. Bu Lisa Memberikan Keputusan Approve**
\`\`\`bash
curl -X POST http://localhost:8080/api/orders/ORD-501/decision -H \"Content-Type: application/json\" -d '{
\"approverId\": \"USR-FIN1\",
\"approved\": true,
\"notes\": \"Anggaran tersedia\"
}'
\`\`\`
*Status klaim otomatis di-reset oleh Temporal Activity, dan order berpindah menunggu persetujuan tingkat DIRECTOR.*
\`);
console.log('✅ Berhasil membuat file temporal.md secara lengkap!');
"