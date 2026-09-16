package com.theotech.iam.domain;

import com.theotech.common.domain.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "roles")
public class Role extends TimestampedEntity {

    public static final String ADMIN = "ADMIN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "system_role", nullable = false)
    private boolean systemRole;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "role_permissions",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id"))
    private Set<Permission> permissions = new HashSet<>();

    protected Role() {
    }

    public Role(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    public void replacePermissions(Collection<Permission> newPermissions) {
        permissions.clear();
        permissions.addAll(newPermissions);
    }

    /** Authority string as used by Spring Security, e.g. {@code ROLE_ADMIN}. */
    public String authority() {
        return "ROLE_" + name;
    }
}
