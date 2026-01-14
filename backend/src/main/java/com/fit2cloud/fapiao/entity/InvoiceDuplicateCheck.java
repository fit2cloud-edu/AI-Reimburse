package com.fit2cloud.fapiao.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice_duplicate_check",
        uniqueConstraints = @UniqueConstraint(columnNames = {"invoice_number", "invoice_date", "user_id"}))
@Data
public class InvoiceDuplicateCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "total_amount", precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "submit_time", nullable = false)
    private LocalDateTime submitTime;

    @Column(name = "status", length = 20)
    private String status = "SUBMITTED";

    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime = LocalDateTime.now();

    @Column(name = "updated_time")
    private LocalDateTime updatedTime = LocalDateTime.now();
}