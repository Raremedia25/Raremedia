package com.theotech.catalog.web;

import com.theotech.catalog.domain.ProductImage;
import com.theotech.catalog.dto.ProductRequest;
import com.theotech.catalog.dto.ProductResponse;
import com.theotech.catalog.dto.StockAddRequest;
import com.theotech.catalog.service.ProductService;
import com.theotech.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/**
 * <pre>
 * GET    /api/products?q=&categoryId=   list / search
 * GET    /api/products/{id}
 * POST   /api/products
 * PUT    /api/products/{id}
 * DELETE /api/products/{id}
 * POST   /api/products/{id}/stock       { "quantity": 20 }
 * GET    /api/products/{id}/image       the picture (bytes)
 * POST   /api/products/{id}/image       multipart "file" (jpeg/png/webp/gif, max 2 MB)
 * DELETE /api/products/{id}/image
 * </pre>
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ProductResponse>> list(@RequestParam(required = false) String q,
                                                   @RequestParam(required = false) Long categoryId) {
        return ApiResponse.ok(service.list(q, categoryId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/stock")
    public ApiResponse<ProductResponse> addStock(@PathVariable Long id, @Valid @RequestBody StockAddRequest request) {
        return ApiResponse.ok(service.addStock(id, request));
    }

    // ---- picture ----------------------------------------------------------------------------

    /** The URL in {@code ProductResponse.imageUrl} carries a version, so the picture may be cached for long. */
    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        ProductImage img = service.image(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(img.getContent());
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ProductResponse> uploadImage(@PathVariable Long id,
                                                    @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.ok(service.uploadImage(id, file));
    }

    @DeleteMapping("/{id}/image")
    public ApiResponse<ProductResponse> deleteImage(@PathVariable Long id) {
        return ApiResponse.ok(service.deleteImage(id));
    }
}
