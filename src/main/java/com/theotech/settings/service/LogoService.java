package com.theotech.settings.service;

import com.theotech.common.exception.NotFoundException;
import com.theotech.common.exception.ValidationException;
import com.theotech.settings.domain.ShopLogo;
import com.theotech.settings.repository.ShopLogoRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The shop logo (Settings → Shop). JPEG, PNG or GIF so it can also be drawn into the PDF report. */
@Service
@Transactional(readOnly = true)
public class LogoService {

    public static final Set<String> LOGO_TYPES = Set.of("image/jpeg", "image/png", "image/gif");
    public static final long MAX_LOGO_BYTES = 1L * 1024 * 1024;

    private final ShopLogoRepository logos;

    public LogoService(ShopLogoRepository logos) {
        this.logos = logos;
    }

    public Optional<ShopLogo> current() {
        return logos.current();
    }

    public ShopLogo require() {
        return logos.current().orElseThrow(() -> new NotFoundException("Logo"));
    }

    /** {@code /api/settings/logo?v=…} (the version lets browsers cache it), or null when there is none. */
    public String url() {
        return logos.current().map(l -> "/api/settings/logo?v=" + l.getUpdatedAt().toEpochMilli()).orElse(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public String upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ValidationException("No file uploaded", Map.of("file", "required"));
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!LOGO_TYPES.contains(type)) {
            throw new ValidationException("Unsupported logo type: " + type, Map.of("file", "logoType"));
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new ValidationException("Logo larger than 1 MB", Map.of("file", "logoSize"));
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
        ShopLogo logo = logos.current().map(l -> { l.replace(bytes, type); return l; })
                .orElseGet(() -> logos.save(new ShopLogo(bytes, type)));
        return "/api/settings/logo?v=" + logo.getUpdatedAt().toEpochMilli();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete() {
        logos.current().ifPresent(logos::delete);
    }
}
