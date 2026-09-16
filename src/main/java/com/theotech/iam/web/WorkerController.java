package com.theotech.iam.web;

import com.theotech.common.ApiResponse;
import com.theotech.iam.dto.ResetPasswordRequest;
import com.theotech.iam.dto.WorkerRequest;
import com.theotech.iam.dto.WorkerResponse;
import com.theotech.iam.dto.WorkerUpdateRequest;
import com.theotech.iam.service.WorkerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin only (enforced in {@link WorkerService}). */
@RestController
@RequestMapping("/api/workers")
public class WorkerController {

    private final WorkerService service;

    public WorkerController(WorkerService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<WorkerResponse>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<WorkerResponse> create(@Valid @RequestBody WorkerRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<WorkerResponse> update(@PathVariable Long id, @Valid @RequestBody WorkerUpdateRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<WorkerResponse> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(service.resetPassword(id, request));
    }

    @PostMapping("/{id}/unlock")
    public ApiResponse<WorkerResponse> unlock(@PathVariable Long id) {
        return ApiResponse.ok(service.unlock(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }
}
