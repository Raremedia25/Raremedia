package com.theotech.settings.repository;

import com.theotech.settings.domain.ShopLogo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShopLogoRepository extends JpaRepository<ShopLogo, Short> {

    default Optional<ShopLogo> current() {
        return findById(ShopLogo.SINGLETON_ID);
    }
}
