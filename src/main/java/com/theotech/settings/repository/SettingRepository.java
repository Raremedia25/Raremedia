package com.theotech.settings.repository;

import com.theotech.settings.domain.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SettingRepository extends JpaRepository<Setting, String> {

    List<Setting> findByCategoryOrderByKey(String category);
}
