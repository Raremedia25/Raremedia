package com.theotech.catalog.service;

import com.theotech.catalog.domain.Category;
import com.theotech.catalog.domain.Product;
import com.theotech.catalog.domain.ProductImage;
import com.theotech.catalog.dto.ProductRequest;
import com.theotech.catalog.dto.ProductResponse;
import com.theotech.catalog.dto.StockAddRequest;
import com.theotech.catalog.repository.CategoryRepository;
import com.theotech.catalog.repository.ProductImageRepository;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.common.exception.ConflictException;
import com.theotech.common.exception.NotFoundException;
import com.theotech.common.exception.ValidationException;
import com.theotech.settings.service.SettingsService;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Products: add, edit, delete, search, add stock, picture. Selling lives in {@code SaleService}. Every
 * rule about stock counts is on the {@link Product} entity itself so it cannot be bypassed.
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    /** Uploaded pictures: photos only (SVG is accepted from our own seed, never from an upload). */
    public static final Set<String> UPLOAD_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    public static final long MAX_IMAGE_BYTES = 2L * 1024 * 1024;

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductImageRepository images;
    private final SettingsService settings;

    public ProductService(ProductRepository products, CategoryRepository categories, ProductImageRepository images,
                          SettingsService settings) {
        this.products = products;
        this.categories = categories;
        this.images = images;
        this.settings = settings;
    }

    /** All products (not deleted), optionally narrowed by a text search on name/category and by category. */
    public List<ProductResponse> list(String q, Long categoryId) {
        int threshold = settings.lowStockThreshold();
        return products.findAll(specification(q, categoryId), Sort.by("name")).stream()
                .map(p -> ProductResponse.from(p, threshold))
                .toList();
    }

    public ProductResponse get(Long id) {
        return ProductResponse.from(load(id), settings.lowStockThreshold());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse create(ProductRequest req) {
        String name = req.name().trim();
        if (products.existsActiveByName(name, null)) {
            throw new ConflictException("DUPLICATE_PRODUCT", "Product already exists: " + name);
        }
        Product p = new Product(name, resolveCategory(req.categoryId()), req.price(),
                req.initialStock() == null ? 0 : req.initialStock());
        return ProductResponse.from(products.save(p), settings.lowStockThreshold());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse update(Long id, ProductRequest req) {
        Product p = load(id);
        String name = req.name().trim();
        if (products.existsActiveByName(name, id)) {
            throw new ConflictException("DUPLICATE_PRODUCT", "Product already exists: " + name);
        }
        p.setName(name);
        p.setCategory(resolveCategory(req.categoryId()));
        p.setPrice(req.price());
        if (req.initialStock() != null) p.setInitialStock(req.initialStock());
        return ProductResponse.from(p, settings.lowStockThreshold());
    }

    /** Soft delete: the product disappears from every list, its sales history stays intact. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        load(id).markDeleted();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse addStock(Long id, StockAddRequest req) {
        Product p = load(id);
        p.addStock(req.quantity());
        return ProductResponse.from(p, settings.lowStockThreshold());
    }

    // ---- pictures ---------------------------------------------------------------------------

    public ProductImage image(Long id) {
        load(id);
        return images.findById(id).orElseThrow(() -> new NotFoundException("Image of product", id));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse uploadImage(Long id, MultipartFile file) {
        Product p = load(id);
        if (file == null || file.isEmpty()) {
            throw new ValidationException("No file uploaded", Map.of("file", "required"));
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!UPLOAD_IMAGE_TYPES.contains(type)) {
            throw new ValidationException("Unsupported image type: " + type, Map.of("file", "imageType"));
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ValidationException("Image larger than 2 MB", Map.of("file", "imageSize"));
        }
        try {
            storeImage(p, file.getBytes(), type);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
        return ProductResponse.from(p, settings.lowStockThreshold());
    }

    /** Stores (or replaces) the picture of a product. Also used by the sample-data seeder. */
    @Transactional
    public void storeImage(Product p, byte[] content, String contentType) {
        ProductImage img = images.findById(p.getId())
                .map(existing -> { existing.replace(content, contentType); return existing; })
                .orElseGet(() -> images.save(new ProductImage(p.getId(), content, contentType)));
        p.setImageUpdatedAt(img.getUpdatedAt());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ProductResponse deleteImage(Long id) {
        Product p = load(id);
        images.findById(id).ifPresent(images::delete);
        p.setImageUpdatedAt(null);
        return ProductResponse.from(p, settings.lowStockThreshold());
    }

    // ---- helpers ----------------------------------------------------------------------------

    static Specification<Product> specification(String q, Long categoryId) {
        List<Specification<Product>> specs = new ArrayList<>();
        specs.add((root, cq, cb) -> cb.isNull(root.get("deletedAt")));
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase(Locale.ROOT) + "%";
            specs.add((root, cq, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("category").get("name")), like)));
        }
        if (categoryId != null) {
            specs.add((root, cq, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }
        return Specification.allOf(specs);
    }

    private Product load(Long id) {
        return products.findActiveById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    private Category resolveCategory(Long categoryId) {
        return categories.findActiveById(categoryId)
                .orElseThrow(() -> new ValidationException("Unknown category", Map.of("categoryId", "invalid")));
    }
}
