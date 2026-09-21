package com.theotech.reports;

import com.theotech.TestAuth;
import com.theotech.iam.repository.UserRepository;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The shop logo (upload / show / remove), the PDF and Excel downloads, and who may reach the reports at all. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportPdfAndLogoIT {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JsonMapper json;

    MockHttpSession admin;
    long categoryId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.loginAsAdmin(mvc, users);
        categoryId = data(mvc.perform(get("/api/categories").session(admin)).andReturn()).get(0).path("id").asLong();
    }

    @Test
    void logoIsUploadedShownEverywhereAndRemovable() throws Exception {
        mvc.perform(get("/api/settings").session(admin)).andExpect(jsonPath("$.data.logoUrl").value(Matchers.nullValue()));
        mvc.perform(get("/api/settings/logo")).andExpect(status().isNotFound());   // public URL, nothing yet

        mvc.perform(TestAuth.withCsrf(mvc, multipart("/api/settings/logo")
                        .file(new MockMultipartFile("file", "logo.svg", "image/svg+xml", "<svg/>".getBytes())), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.file").value("logoType"));

        byte[] png = png(120, 60);
        MvcResult up = mvc.perform(TestAuth.withCsrf(mvc, multipart("/api/settings/logo")
                        .file(new MockMultipartFile("file", "logo.png", "image/png", png)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl").value(Matchers.startsWith("/api/settings/logo?v=")))
                .andReturn();
        String url = data(up).path("logoUrl").asString();

        MvcResult img = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn();
        assertThat(img.getResponse().getContentAsByteArray()).isEqualTo(png);

        mvc.perform(TestAuth.withCsrf(mvc, multipart("/api/settings/logo")
                        .file(new MockMultipartFile("file", "logo2.png", "image/png", png(80, 80))), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl").value(Matchers.startsWith("/api/settings/logo?v=")));

        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/settings/logo"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoUrl").value(Matchers.nullValue()));
        mvc.perform(get("/api/settings/logo")).andExpect(status().isNotFound());
    }

    @Test
    void pdfReportDownloadsWithTheLogoAndTheFigures() throws Exception {
        long charger = create("Charger", "10000", 30);
        sell(admin, charger, 2, true, null);
        sell(admin, charger, 1, false, "Mukamana");
        mvc.perform(TestAuth.withCsrf(mvc, multipart("/api/settings/logo")
                .file(new MockMultipartFile("file", "logo.png", "image/png", png(120, 60))), admin)).andExpect(status().isOk());

        MvcResult pdf = mvc.perform(get("/api/reports/sales/pdf").session(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString("sales-report-")))
                .andExpect(header().string("Content-Disposition", Matchers.containsString(".pdf")))
                .andReturn();
        byte[] bytes = pdf.getResponse().getContentAsByteArray();
        assertThat(bytes.length).isGreaterThan(2000);
        assertThat(new String(bytes, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(new String(bytes, Math.max(0, bytes.length - 64), Math.min(64, bytes.length), StandardCharsets.ISO_8859_1)).contains("%%EOF");
        assertThat(new String(bytes, StandardCharsets.ISO_8859_1)).contains("/Image");   // the logo made it in

        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/settings/logo"), admin)).andExpect(status().isOk());
        mvc.perform(get("/api/reports/sales/pdf").session(admin)).andExpect(status().isOk());
    }

    @Test
    void excelReportHasOneSheetPerTableWithHeadersBordersAndNumbers() throws Exception {
        long charger = create("Charger", "10000", 30);
        long radio = create("Radio", "18000", 3);
        sell(admin, charger, 2, true, null);
        sell(admin, radio, 1, false, "Uwase");
        mvc.perform(TestAuth.json(mvc, post("/api/expenses"), admin,
                        "{\"spentOn\":\"" + java.time.LocalDate.now(java.time.ZoneId.of("Africa/Kigali")) + "\",\"category\":\"Rent\",\"description\":\"Shop rent\",\"amount\":5000}"))
                .andExpect(status().isCreated());

        MvcResult xlsx = mvc.perform(get("/api/reports/sales/excel").session(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", Matchers.containsString(".xlsx")))
                .andReturn();
        byte[] bytes = xlsx.getResponse().getContentAsByteArray();
        assertThat(new String(bytes, 0, 2, StandardCharsets.ISO_8859_1)).isEqualTo("PK");   // a zip = an .xlsx

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            List<String> names = new ArrayList<>();
            for (int i = 0; i < wb.getNumberOfSheets(); i++) names.add(wb.getSheetName(i));
            assertThat(names).containsExactly("Summary", "Sales per product", "Not paid", "Expenses", "Low stock");

            Sheet sales = wb.getSheet("Sales per product");
            Row header = sales.getRow(3);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Product");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Sales (RWF)");
            assertThat(((org.apache.poi.xssf.usermodel.XSSFCellStyle) header.getCell(0).getCellStyle()).getFont().getBold()).as("bold header").isTrue();
            assertThat(header.getCell(0).getCellStyle().getFillPattern().name()).isEqualTo("SOLID_FOREGROUND");

            // data rows: products sorted by sales, numbers stored as numbers, every cell bordered
            Row first = sales.getRow(4);
            assertThat(first.getCell(0).getStringCellValue()).isEqualTo("Charger");
            assertThat(first.getCell(2).getNumericCellValue()).isEqualTo(2d);
            assertThat(first.getCell(3).getNumericCellValue()).isEqualTo(20000d);
            for (Cell c : first) {
                assertThat(c.getCellStyle().getBorderTop()).as("border on " + c.getAddress()).isEqualTo(BorderStyle.THIN);
                assertThat(c.getCellStyle().getBorderBottom()).isEqualTo(BorderStyle.THIN);
                assertThat(c.getCellStyle().getBorderLeft()).isEqualTo(BorderStyle.THIN);
                assertThat(c.getCellStyle().getBorderRight()).isEqualTo(BorderStyle.THIN);
            }
            Row radioRow = sales.getRow(5);
            assertThat(radioRow.getCell(0).getStringCellValue()).isEqualTo("Radio");
            assertThat(radioRow.getCell(4).getNumericCellValue()).as("not paid").isEqualTo(18000d);
            Row total = sales.getRow(6);
            assertThat(total.getCell(0).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(total.getCell(3).getNumericCellValue()).isEqualTo(38000d);
            assertThat(sales.getPaneInformation()).as("frozen header").isNotNull();

            Sheet unpaid = wb.getSheet("Not paid");
            assertThat(unpaid.getRow(4).getCell(2).getStringCellValue()).isEqualTo("Uwase");
            Sheet expenses = wb.getSheet("Expenses");
            assertThat(expenses.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Rent");
            assertThat(expenses.getRow(4).getCell(2).getNumericCellValue()).isEqualTo(5000d);
            Sheet summary = wb.getSheet("Summary");
            assertThat(summary.getRow(0).getCell(0).getStringCellValue()).isEqualTo("THEO TECH LTD");
            Sheet low = wb.getSheet("Low stock");
            assertThat(low.getRow(4).getCell(0).getStringCellValue()).isEqualTo("Radio");   // 2 left ≤ threshold 5
        }
    }

    @Test
    void workersCannotReachAnyReportAndCannotSetPrices() throws Exception {
        mvc.perform(TestAuth.json(mvc, post("/api/workers"), admin,
                        "{\"fullName\":\"Alice Worker\",\"username\":\"alice\",\"password\":\"Worker2026\"}"))
                .andExpect(status().isCreated());
        users.findActiveByUsername("alice").ifPresent(u -> u.setMustChangePassword(false));
        MockHttpSession alice = TestAuth.login(mvc, "alice", "Worker2026");
        long charger = create("Charger", "10000", 30);

        for (String path : new String[]{"/api/reports/sales", "/api/reports/sales/pdf", "/api/reports/sales/excel"}) {
            mvc.perform(get(path).session(alice)).andExpect(status().isForbidden());
        }
        mvc.perform(TestAuth.withCsrf(mvc, post("/api/reports/sales/email"), alice)).andExpect(status().isForbidden());

        // a worker sells at the admin's price: a price sent by the client is ignored, and products cannot be edited
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), alice,
                        "{\"productId\":" + charger + ",\"quantity\":2,\"unitPrice\":1,\"price\":1,\"total\":2}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.lines[0].unitPrice").value(10000))
                .andExpect(jsonPath("$.data.total").value(20000));
        mvc.perform(TestAuth.json(mvc, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/products/" + charger), alice,
                        "{\"name\":\"Charger\",\"categoryId\":" + categoryId + ",\"price\":1,\"initialStock\":30}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/products/" + charger).session(alice)).andExpect(jsonPath("$.data.price").value(10000));
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(0x4f46e5));
        g.fillRoundRect(4, 4, w - 8, h - 8, 12, 12);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(bi, "png", out);
        return out.toByteArray();
    }

    private long create(String name, String price, int initialStock) throws Exception {
        MvcResult r = mvc.perform(TestAuth.json(mvc, post("/api/products"), admin,
                        "{\"name\":\"%s\",\"categoryId\":%d,\"price\":%s,\"initialStock\":%d}".formatted(name, categoryId, price, initialStock)))
                .andExpect(status().isCreated()).andReturn();
        return data(r).path("id").asLong();
    }

    private void sell(MockHttpSession who, long productId, int quantity, boolean paid, String customer) throws Exception {
        String body = "{\"productId\":%d,\"quantity\":%d,\"paid\":%s%s}".formatted(productId, quantity, paid,
                customer == null ? "" : ",\"customerName\":\"" + customer + "\"");
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), who, body)).andExpect(status().isCreated());
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
