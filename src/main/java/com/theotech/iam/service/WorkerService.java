package com.theotech.iam.service;

import com.theotech.common.exception.ConflictException;
import com.theotech.common.exception.NotFoundException;
import com.theotech.iam.domain.Role;
import com.theotech.iam.domain.User;
import com.theotech.iam.dto.ResetPasswordRequest;
import com.theotech.iam.dto.WorkerRequest;
import com.theotech.iam.dto.WorkerResponse;
import com.theotech.iam.dto.WorkerUpdateRequest;
import com.theotech.iam.repository.RoleRepository;
import com.theotech.iam.repository.UserRepository;
import com.theotech.security.AuthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Worker accounts, managed by the administrator only. A worker can sign in, sell and look at stock and
 * sales; everything that changes products, settings or accounts stays with the admin
 * ({@code hasRole('ADMIN')} on those service methods).
 */
@Service
@PreAuthorize("hasRole('ADMIN')")
@Transactional
public class WorkerService {

    /** The seeded role that marks a worker (its permissions are not used: access is by role only). */
    public static final String WORKER_ROLE = "SALES_STAFF";

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;

    public WorkerService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<WorkerResponse> list() {
        return users.findActiveByRole(WORKER_ROLE).stream().map(WorkerResponse::from).toList();
    }

    public WorkerResponse create(WorkerRequest req) {
        String username = req.username().trim().toLowerCase();
        if (users.existsActiveByUsername(username)) {
            throw new ConflictException("DUPLICATE_USERNAME", "Username already taken: " + username);
        }
        AuthService.validateStrength(req.password());
        User u = new User(username, req.fullName().trim(), passwordEncoder.encode(req.password()));
        u.setMustChangePassword(true);   // the worker chooses their own password at first sign-in
        Role worker = roles.findByName(WORKER_ROLE).orElseThrow(() -> new IllegalStateException("Role " + WORKER_ROLE + " missing"));
        u.getRoles().add(worker);
        return WorkerResponse.from(users.save(u));
    }

    public WorkerResponse update(Long id, WorkerUpdateRequest req) {
        User u = load(id);
        u.setFullName(req.fullName().trim());
        u.setEnabled(req.enabled());
        return WorkerResponse.from(u);
    }

    public WorkerResponse resetPassword(Long id, ResetPasswordRequest req) {
        User u = load(id);
        AuthService.validateStrength(req.newPassword());
        u.changePassword(passwordEncoder.encode(req.newPassword()), Instant.now());
        u.setMustChangePassword(true);
        u.unlock();
        return WorkerResponse.from(u);
    }

    public WorkerResponse unlock(Long id) {
        User u = load(id);
        u.unlock();
        return WorkerResponse.from(u);
    }

    /** Soft delete: the account can no longer sign in; the sales it recorded stay. */
    public void delete(Long id) {
        User u = load(id);
        u.setEnabled(false);
        u.markDeleted();
    }

    private User load(Long id) {
        User u = users.findActiveById(id).orElseThrow(() -> new NotFoundException("Worker", id));
        boolean isWorker = u.getRoles().stream().anyMatch(r -> WORKER_ROLE.equals(r.getName()));
        if (!isWorker) throw new NotFoundException("Worker", id);   // the admin account is not managed here
        return u;
    }
}
