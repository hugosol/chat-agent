package com.hugosol.chatagent.model;

import jakarta.persistence.*;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "assertion_lineage")
@IdClass(AssertionLineage.LineageId.class)
public class AssertionLineage {

    @Id
    @Column(name = "parent_id", nullable = false)
    private String parentId;

    @Id
    @Column(name = "child_id", nullable = false)
    private String childId;

    @Column(nullable = false)
    private String operation = "MERGE";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", insertable = false, updatable = false)
    private MemoryAssertion parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "child_id", insertable = false, updatable = false)
    private MemoryAssertion child;

    public AssertionLineage() {}

    public AssertionLineage(String parentId, String childId, String operation) {
        this.parentId = parentId;
        this.childId = childId;
        this.operation = operation;
    }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public String getChildId() { return childId; }
    public void setChildId(String childId) { this.childId = childId; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public MemoryAssertion getParent() { return parent; }
    public MemoryAssertion getChild() { return child; }

    public static class LineageId implements Serializable {
        private String parentId;
        private String childId;

        public LineageId() {}

        public LineageId(String parentId, String childId) {
            this.parentId = parentId;
            this.childId = childId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof LineageId that)) return false;
            return Objects.equals(parentId, that.parentId) && Objects.equals(childId, that.childId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentId, childId);
        }
    }
}
