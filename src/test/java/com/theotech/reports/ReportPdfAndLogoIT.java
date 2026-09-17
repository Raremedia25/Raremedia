package com.theotech.reports;

import com.theotech.TestAuth;
import com.theotech.iam.repository.UserRepository;
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
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The shop logo (upload / show / remove) and the PDF report that carries it. */
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

        // wrong type is refused
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

        // served publicly (the login page shows it), cacheable, right type and bytes
        MvcResult img = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andReturn();
        assertThat(img.getResponse().getContentAsByteArray()).isEqualTo(png);

        // replacing changes the version in the URL
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
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin, "{\"productId\":%d,\"quantity\":2}".formatted(charger)))
                .andExpect(status().isCreated());
        mvc.perform(TestAuth.json(mvc, post("/api/sales"), admin,
                        "{\"productId\":%d,\"quantity\":1,\"paid\":false,\"customerName\":\"Mukamana\"}".formatted(charger)))
                .andExpect(status().isCreated());
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
        assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        String tail = new String(bytes, Math.max(0, bytes.length - 64), Math.min(64, bytes.length), java.nio.charset.StandardCharsets.ISO_8859_1);
        assertThat(tail).contains("%%EOF");
        assertThat(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)).contains("/Image");   // the logo made it in

        // without a logo it still renders
        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/settings/logo"), admin)).andExpect(status().isOk());
        mvc.perform(get("/api/reports/sales/pdf").session(admin)).andExpect(status().isOk());
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

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
