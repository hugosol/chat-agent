package com.hugosol.chatagent.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "batch_operation_logs")
public class BatchOperationLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchOperationType operationType;

    @Column(nullable = false)
    private String tagId;

    @Column(nullable = false)
    private String tagName;

    private String fileName;

    private Integer totalRows;

    private Integer successCount;

    private Integer skipCount;

    @Column(length = 4000)
    private String errorDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BatchOperationStatus status;

    public BatchOperationLog() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public BatchOperationType getOperationType() { return operationType; }
    public void setOperationType(BatchOperationType operationType) { this.operationType = operationType; }
    public String getTagId() { return tagId; }
    public void setTagId(String tagId) { this.tagId = tagId; }
    public String getTagName() { return tagName; }
    public void setTagName(String tagName) { this.tagName = tagName; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Integer getTotalRows() { return totalRows; }
    public void setTotalRows(Integer totalRows) { this.totalRows = totalRows; }
    public Integer getSuccessCount() { return successCount; }
    public void setSuccessCount(Integer successCount) { this.successCount = successCount; }
    public Integer getSkipCount() { return skipCount; }
    public void setSkipCount(Integer skipCount) { this.skipCount = skipCount; }
    public String getErrorDetails() { return errorDetails; }
    public void setErrorDetails(String errorDetails) { this.errorDetails = errorDetails; }
    public BatchOperationStatus getStatus() { return status; }
    public void setStatus(BatchOperationStatus status) { this.status = status; }
}
