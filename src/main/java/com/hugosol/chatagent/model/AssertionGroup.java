package com.hugosol.chatagent.model;

import jakarta.persistence.*;

@Entity
@Table(name = "assertion_groups")
public class AssertionGroup extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "CLOB")
    private String description;

    @Column
    private String mode;

    public AssertionGroup() {}

    public AssertionGroup(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public AssertionGroup(String name, String description, String mode) {
        this.name = name;
        this.description = description;
        this.mode = mode;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
