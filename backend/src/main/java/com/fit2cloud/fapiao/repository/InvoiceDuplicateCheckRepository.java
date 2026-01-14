package com.fit2cloud.fapiao.repository;

import com.fit2cloud.fapiao.entity.InvoiceDuplicateCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceDuplicateCheckRepository extends JpaRepository<InvoiceDuplicateCheck, Long> {

    /**
     * 严格查重：检查是否存在相同发票号码和开票日期的记录
     */
    @Query("SELECT COUNT(i) > 0 FROM InvoiceDuplicateCheck i WHERE i.invoiceNumber = :invoiceNumber AND i.invoiceDate = :invoiceDate AND i.status != 'REJECTED'")
    boolean existsByInvoiceNumberAndInvoiceDate(@Param("invoiceNumber") String invoiceNumber,
                                                @Param("invoiceDate") LocalDate invoiceDate);

    /**
     * 宽松查重：检查是否存在相同发票号码和近似金额的记录
     */
    @Query("SELECT COUNT(i) > 0 FROM InvoiceDuplicateCheck i WHERE i.invoiceNumber = :invoiceNumber AND ABS(i.totalAmount - :totalAmount) < 0.01 AND i.status != 'REJECTED'")
    boolean existsByInvoiceNumberAndSimilarAmount(@Param("invoiceNumber") String invoiceNumber,
                                                  @Param("totalAmount") BigDecimal totalAmount);

    /**
     * 用户维度查重：检查同一用户是否重复提交相同发票
     */
    @Query("SELECT COUNT(i) > 0 FROM InvoiceDuplicateCheck i WHERE i.invoiceNumber = :invoiceNumber AND i.invoiceDate = :invoiceDate AND i.userId = :userId AND i.status != 'REJECTED'")
    boolean existsByInvoiceNumberAndInvoiceDateAndUserId(@Param("invoiceNumber") String invoiceNumber,
                                                         @Param("invoiceDate") LocalDate invoiceDate,
                                                         @Param("userId") String userId);

    /**
     * 根据发票号码和日期查找记录
     */
    Optional<InvoiceDuplicateCheck> findByInvoiceNumberAndInvoiceDate(String invoiceNumber, LocalDate invoiceDate);

    /**
     * 根据用户ID查找记录
     */
    List<InvoiceDuplicateCheck> findByUserIdOrderBySubmitTimeDesc(String userId);
}