package com.theotech.config;

import com.theotech.catalog.domain.Category;
import com.theotech.catalog.domain.Product;
import com.theotech.catalog.repository.CategoryRepository;
import com.theotech.catalog.repository.ProductRepository;
import com.theotech.catalog.service.ProductService;
import com.theotech.settings.domain.Setting;
import com.theotech.settings.repository.SettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Puts a dozen typical items on the shelf, each with a picture, the first time the app starts in the
 * {@code dev} profile. Items whose name already exists are left alone. Runs once (remembered in
 * {@code settings} under {@value #FLAG}); delete or edit the items freely afterwards.
 */
@Component
@Profile("dev")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    static final String FLAG = "demo.seeded";

    record Item(String name, String category, String price, int stock, String image) {
    }

    static final List<Item> ITEMS = List.of(
            new Item("Samsung Charger", "Chargers", "10000", 20, "charger.svg"),
            new Item("Type-C Cable", "Cables", "5000", 50, "cable.svg"),
            new Item("Bluetooth Earphone", "Earphones", "15000", 30, "earphone.svg"),
            new Item("Headphones", "Headphones", "20000", 15, "headphones.svg"),
            new Item("Power Bank 10000mAh", "Power Banks", "25000", 10, "powerbank.svg"),
            new Item("Radio", "Radios", "18000", 12, "radio.svg"),
            new Item("Phone Battery", "Batteries", "8000", 25, "battery.svg"),
            new Item("Bluetooth Speaker", "Speakers", "30000", 8, "speaker.svg"),
            new Item("Memory Card 32GB", "Memory Cards", "12000", 40, "memorycard.svg"),
            new Item("Tecno Spark 20", "Phones", "150000", 6, "phone.svg"),
            new Item("USB Mouse", "Computer Accessories", "7000", 14, "mouse.svg"),
            new Item("Phone Cover", "Other", "3000", 60, "cover.svg"));

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductService productService;
    private final SettingRepository settings;
    private final TransactionTemplate tx;

    public DemoDataSeeder(ProductRepository products, CategoryRepository categories, ProductService productService,
                          SettingRepository settings, TransactionTemplate tx) {
        this.products = products;
        this.categories = categories;
        this.productService = productService;
        this.settings = settings;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        tx.executeWithoutResult(status -> {
            if (settings.existsById(FLAG)) return;
            Map<String, Category> byName = categories.findAllActive().stream()
                    .collect(Collectors.toMap(c -> c.getName().toLowerCase(), Function.identity(), (a, b) -> a));
            Category fallback = byName.getOrDefault("other", byName.values().iterator().next());
            int added = 0;
            for (Item item : ITEMS) {
                if (products.existsActiveByName(item.name(), null)) continue;
                Category category = byName.getOrDefault(item.category().toLowerCase(), fallback);
                Product p = products.save(new Product(item.name(), category, new BigDecimal(item.price()), item.stock()));
                productService.storeImage(p, read(item.image()), "image/svg+xml");
                added++;
            }
            settings.save(new Setting(FLAG, "true", "BOOLEAN", "system",
                    "Sample products were inserted on first start (dev profile); set once, never repeated"));
            log.info("Sample data: inserted {} products with pictures ({} already existed)", added, ITEMS.size() - added);
        });
    }

    private static byte[] read(String file) {
        try {
            return new ClassPathResource("demo/" + file).getContentAsByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Missing sample image " + file, e);
        }
    }
}
