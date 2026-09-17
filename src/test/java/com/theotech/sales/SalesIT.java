package com.theotech.sales;

import com.theotech.TestAuth;
import com.theotech.iam.repository.UserRepository;
import com.theotech.sales.repository.SaleRepository;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Selling: stock goes down, sold goes up, overselling is refused, receipts group lines, history and totals are right. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SalesIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired SaleRepository sales;
    @Autowired JsonMapper json;

    MockHttpSession admin;
    long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.loginAsAdmin(mvc, users);
        categoryId = data(mvc.perform(get("/api/categories").session(admin)).andReturn()).get(0).path("id").asLong();
    }

    @Test
    void sellingReducesStockAndRecordsTheSaleAtTheProductPrice() throws Exception {
        long earphone = create("Bluetooth Earphone", "15000", 10);

        MvcResult sold = mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(earphone, 3)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.receiptNo").value(Matchers.matchesRegex("\\d{6}")))
                .andExpect(jsonPath("$.data.paid").value(true))
                .andExpect(jsonPath("$.data.itemCount").value(3))
                .andExpect(jsonPath("$.data.lines.length()").value(1))
                .andExpect(jsonPath("$.data.lines[0].productId").value(earphone))
                .andExpect(jsonPath("$.data.lines[0].productName").value("Bluetooth Earphone"))
                .andExpect(jsonPath("$.data.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.data.lines[0].remainingStock").value(7))
                .andExpect(jsonPath("$.data.soldAt").isString())
                .andReturn();
        JsonNode r = data(sold);
        assertThat(r.path("lines").get(0).path("unitPrice").decimalValue()).isEqualByComparingTo("15000");
        assertThat(r.path("total").decimalValue()).isEqualByComparingTo("45000");

        mvc.perform(get("/api/products/" + earphone).session(admin))
                .andExpect(jsonPath("$.data.initialStock").value(10))
                .andExpect(jsonPath("$.data.soldQuantity").value(3))
                .andExpect(jsonPath("$.data.availableStock").value(7));

        long lineId = r.path("lines").get(0).path("id").asLong();
        mvc.perform(get("/api/sales/" + lineId).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(3))
                .andExpect(jsonPath("$.data.receiptNo").value(r.path("receiptNo").asString()));
        mvc.perform(get("/api/sales/receipt/" + r.path("receiptNo").asString()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines.length()").value(1));
        mvc.perform(get("/api/sales/999999").session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/sales/receipt/999999").session(admin)).andExpect(status().isNotFound());
    }

    @Test
    void severalItemsMakeOneReceiptAndAllOrNothingWhenOneIsShort() throws Exception {
        long charger = create("Charger", "10000", 5);
        long cable = create("Cable", "5000", 20);
        long radio = create("Radio", "18000", 1);

        // cart with a repeated product: lines are merged, one receipt number for all
        MvcResult r = mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, """
                        {"items":[{"productId":%d,"quantity":2},{"productId":%d,"quantity":3},{"productId":%d,"quantity":1}],
                         "paid":false,"customerName":"Kamali"}
                        """.formatted(charger, cable, cable)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines.length()").value(2))
                .andExpect(jsonPath("$.data.lines[0].productName").value("Charger"))
                .andExpect(jsonPath("$.data.lines[1].productName").value("Cable"))
                .andExpect(jsonPath("$.data.lines[1].quantity").value(4))
                .andExpect(jsonPath("$.data.lines[1].remainingStock").value(16))
                .andExpect(jsonPath("$.data.itemCount").value(6))
                .andExpect(jsonPath("$.data.paid").value(false))
                .andExpect(jsonPath("$.data.customerName").value("Kamali"))
                .andReturn();
        JsonNode receipt = data(r);
        assertThat(receipt.path("total").decimalValue()).isEqualByComparingTo("40000");   // 2×10000 + 4×5000
        String no = receipt.path("receiptNo").asString();
        assertThat(receipt.path("lines").get(0).path("receiptNo").asString()).isEqualTo(no);
        assertThat(receipt.path("lines").get(1).path("receiptNo").asString()).isEqualTo(no);
        assertThat(receipt.path("lines").get(0).path("soldAt").asString()).isEqualTo(receipt.path("lines").get(1).path("soldAt").asString());

        // one item short → nothing at all is sold, stock untouched
        long before = sales.count();
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, """
                        {"items":[{"productId":%d,"quantity":1},{"productId":%d,"quantity":2}]}
                        """.formatted(charger, radio)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.errors.available").value("1"))
                .andExpect(jsonPath("$.errors.requested").value("2"));
        assertThat(sales.count()).isEqualTo(before);
        mvc.perform(get("/api/products/" + charger).session(admin)).andExpect(jsonPath("$.data.availableStock").value(3));
        mvc.perform(get("/api/products/" + radio).session(admin)).andExpect(jsonPath("$.data.availableStock").value(1));

        // paying the receipt pays every line on it
        mvc.perform(TestAuth.json(mvc, post("/api/sales/receipt/" + no + "/paid"), admin, "{\"paid\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paid").value(true))
                .andExpect(jsonPath("$.data.lines[*].paid", Matchers.everyItem(Matchers.is(true))));
        mvc.perform(get("/api/sales").session(admin).param("paid", "false"))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // an empty cart is a validation error
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, "{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.items").value("required"));
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, "{\"items\":[{\"productId\":%d,\"quantity\":0}]}".formatted(charger)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void overSellingIsRefusedAndNothingIsRecorded() throws Exception {
        long speaker = create("Bluetooth Speaker", "30000", 5);
        long before = sales.count();

        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(speaker, 8)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.errors.available").value("5"))
                .andExpect(jsonPath("$.errors.requested").value("8"));

        assertThat(sales.count()).isEqualTo(before);
        mvc.perform(get("/api/products/" + speaker).session(admin))
                .andExpect(jsonPath("$.data.soldQuantity").value(0))
                .andExpect(jsonPath("$.data.availableStock").value(5));

        // exactly the last units can be sold, then nothing more
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(speaker, 5)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].remainingStock").value(0));
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(speaker, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.available").value("0"));
        mvc.perform(get("/api/products/" + speaker).session(admin))
                .andExpect(jsonPath("$.data.status").value("OUT_OF_STOCK"));

        // bad requests
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(speaker, 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.quantity").value("positive"));
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, "{\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.productId").value("required"));
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(999999L, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void historyFiltersSearchesSortsAndTotals() throws Exception {
        long charger = create("Charger", "10000", 30);
        long radio = create("Radio", "18000", 15);
        long earphone = create("Earphone", "15000", 50);
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(charger, 2))).andExpect(status().isCreated());
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(earphone, 3))).andExpect(status().isCreated());
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(radio, 1))).andExpect(status().isCreated());

        // newest first by default; totals cover the whole filtered set
        MvcResult all = mvc.perform(get("/api/sales").session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].productName").value("Radio"))
                .andExpect(jsonPath("$.data.content[2].productName").value("Charger"))
                .andExpect(jsonPath("$.data.totalQuantity").value(6))
                .andReturn();
        assertThat(data(all).path("totalAmount").decimalValue()).isEqualByComparingTo("83000");   // 20000 + 45000 + 18000

        // paging keeps the totals of the whole set
        mvc.perform(get("/api/sales").session(admin).param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.totalQuantity").value(6));

        // search by product name
        MvcResult q = mvc.perform(get("/api/sales").session(admin).param("q", "ear"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.totalQuantity").value(3))
                .andReturn();
        assertThat(data(q).path("totalAmount").decimalValue()).isEqualByComparingTo("45000");

        // sort by total, largest first
        mvc.perform(get("/api/sales").session(admin).param("sort", "total,desc"))
                .andExpect(jsonPath("$.data.content[*].productName", Matchers.contains("Earphone", "Charger", "Radio")));
        mvc.perform(get("/api/sales").session(admin).param("sort", "soldBy"))
                .andExpect(status().isBadRequest());

        // date range: everything is "now"; a window in the future is empty, a window around now has all three
        String hourAgo = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        String inAnHour = Instant.now().plus(1, ChronoUnit.HOURS).toString();
        String tomorrow = Instant.now().plus(1, ChronoUnit.DAYS).toString();
        mvc.perform(get("/api/sales").session(admin).param("from", hourAgo).param("to", inAnHour))
                .andExpect(jsonPath("$.data.totalElements").value(3));
        mvc.perform(get("/api/sales").session(admin).param("from", inAnHour).param("to", tomorrow))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalQuantity").value(0));
    }

    @Test
    void deletingAProductKeepsItsSalesHistory() throws Exception {
        long id = create("Old Phone", "50000", 2);
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(id, 1))).andExpect(status().isCreated());
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/products/" + id), admin)).andExpect(status().isOk());

        mvc.perform(get("/api/sales").session(admin))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].productName").value("Old Phone"));
        // but the deleted product can no longer be sold
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(id, 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void unpaidSalesNeedACustomerAndCanBeMarkedPaidLater() throws Exception {
        long phone = create("Tecno Spark", "150000", 5);

        // credit without a name is refused
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, "{\"productId\":%d,\"quantity\":1,\"paid\":false}".formatted(phone)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.customerName").value("required"));

        MvcResult credit = mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin,
                        "{\"productId\":%d,\"quantity\":1,\"paid\":false,\"customerName\":\"  Uwase Diane \"}".formatted(phone)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paid").value(false))
                .andExpect(jsonPath("$.data.paidAt").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.data.customerName").value("Uwase Diane"))
                .andExpect(jsonPath("$.data.receiptNo").value(Matchers.matchesRegex("\\d{6}")))
                .andReturn();
        long creditLine = data(credit).path("lines").get(0).path("id").asLong();
        // a normal sale is paid on the spot
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, sale(phone, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.paid").value(true))
                .andExpect(jsonPath("$.data.paidAt").isString());

        // history: filter and totals of what is owed
        MvcResult unpaid = mvc.perform(get("/api/sales").session(admin).param("paid", "false"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].customerName").value("Uwase Diane"))
                .andExpect(jsonPath("$.data.unpaidCount").value(1))
                .andReturn();
        assertThat(data(unpaid).path("unpaidAmount").decimalValue()).isEqualByComparingTo("150000");
        MvcResult all = mvc.perform(get("/api/sales").session(admin).param("q", "uwase"))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andReturn();
        assertThat(data(all).path("totalAmount").decimalValue()).isEqualByComparingTo("150000");
        mvc.perform(get("/api/dashboard").session(admin))
                .andExpect(jsonPath("$.data.unpaidCount").value(1));

        // money arrives (addressed by the line, pays its receipt)
        mvc.perform(TestAuth.json(mvc, post("/api/sales/" + creditLine + "/paid"), admin, "{\"paid\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paid").value(true))
                .andExpect(jsonPath("$.data.paidAt").isString());
        mvc.perform(get("/api/sales").session(admin).param("paid", "false"))
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.unpaidCount").value(0));
        MvcResult dash = mvc.perform(get("/api/dashboard").session(admin))
                .andExpect(jsonPath("$.data.unpaidCount").value(0)).andReturn();
        assertThat(data(dash).path("unpaidAmount").decimalValue()).isEqualByComparingTo("0");

        // the report shows paid / not paid split
        mvc.perform(TestAuth.json(mvc, post("/api/sales/" + creditLine + "/paid"), admin, "{\"paid\":false}")).andExpect(status().isOk());
        MvcResult report = mvc.perform(get("/api/reports/sales").session(admin))
                .andExpect(jsonPath("$.data.unpaidSales.length()").value(1))
                .andExpect(jsonPath("$.data.unpaidSales[0].customerName").value("Uwase Diane"))
                .andReturn();
        JsonNode rep = data(report);
        assertThat(rep.path("totalSales").decimalValue()).isEqualByComparingTo("450000");
        assertThat(rep.path("totalPaid").decimalValue()).isEqualByComparingTo("300000");
        assertThat(rep.path("totalUnpaid").decimalValue()).isEqualByComparingTo("150000");
        assertThat(rep.path("rows").get(0).path("unpaidSales").decimalValue()).isEqualByComparingTo("150000");

        mvc.perform(TestAuth.json(mvc, post("/api/sales/999999/paid"), admin, "{\"paid\":true}")).andExpect(status().isNotFound());
    }

    // ---- helpers ----------------------------------------------------------------------------

    private long create(String name, String price, int initialStock) throws Exception {
        MvcResult r = mvc.perform(TestAuth.json(mvc, post("/api/products"), admin,
                        "{\"name\":\"%s\",\"categoryId\":%d,\"price\":%s,\"initialStock\":%d}".formatted(name, categoryId, price, initialStock)))
                .andExpect(status().isCreated()).andReturn();
        return data(r).path("id").asLong();
    }

    private static String sale(long productId, int quantity) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(productId, quantity);
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
