package com.theotech.security;

import com.theotech.iam.domain.Permission;
import com.theotech.iam.domain.Role;
import com.theotech.iam.domain.User;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * The authenticated user as stored in the HTTP session. A detached snapshot of {@link User} —
 * no entity, no lazy collections — so it is cheap and safe to serialise.
 * Authorities are {@code ROLE_<name>} for each role plus every permission name.
 */
public final class AppUserPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private String password;
    private final String fullName;
    private final String email;
    private final boolean enabled;
    private final boolean accountNonLocked;
    private volatile boolean mustChangePassword;
    private final String preferredLanguage;
    private final Long branchId;
    private final Set<String> roles;
    private final Set<String> permissions;
    private final Set<GrantedAuthority> authorities;

    private AppUserPrincipal(User u) {
        this.id = u.getId();
        this.username = u.getUsername();
        this.password = u.getPasswordHash();
        this.fullName = u.getFullName();
        this.email = u.getEmail();
        this.enabled = u.isEnabled() && !u.isDeleted();
        this.accountNonLocked = u.isAccountNonLocked();
        this.mustChangePassword = u.isMustChangePassword();
        this.preferredLanguage = u.getPreferredLanguage();
        this.branchId = u.getDefaultBranchId();

        Set<String> roleNames = new TreeSet<>();
        Set<String> permNames = new TreeSet<>();
        for (Role r : u.getRoles()) {
            roleNames.add(r.getName());
            for (Permission p : r.getPermissions()) {
                permNames.add(p.getName());
            }
        }
        this.roles = Collections.unmodifiableSet(roleNames);
        this.permissions = Collections.unmodifiableSet(permNames);

        Set<GrantedAuthority> auths = new LinkedHashSet<>();
        roleNames.forEach(r -> auths.add(new SimpleGrantedAuthority("ROLE_" + r)));
        permNames.forEach(p -> auths.add(new SimpleGrantedAuthority(p)));
        this.authorities = Collections.unmodifiableSet(auths);
    }

    public static AppUserPrincipal from(User user) {
        return new AppUserPrincipal(user);
    }

    // ---- UserDetails ----------------------------------------------------------------------

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.password = null;
    }

    // ---- application data -----------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    /** Called after a successful password change so the live session no longer forces a change. */
    public void passwordChanged() {
        this.mustChangePassword = false;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public Long getBranchId() {
        return branchId;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    @Override
    public String toString() {
        return "AppUserPrincipal{id=" + id + ", username='" + username + "', roles=" + roles + "}";
    }
}
