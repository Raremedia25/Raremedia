package com.theotech.catalog;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A picture per product: upload, serve, replace, remove. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductImageIT {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JsonMapper json;

    MockHttpSession admin;
    long productId;

    @BeforeEach
    void setUp() throws Exception {
        admin = TestAuth.loginAsAdmin(mvc, users);
        long categoryId = data(mvc.perform(get("/api/categories").session(admin)).andReturn()).get(0).path("id").asLong();
        productId = data(mvc.perform(TestAuth.json(mvc, post("/api/products"), admin,
                        "{\"name\":\"Pictured\",\"categoryId\":" + categoryId + ",\"price\":1000,\"initialStock\":3}"))
                .andExpect(status().isCreated()).andReturn()).path("id").asLong();
    }

    @Test
    void uploadServeReplaceAndRemove() throws Exception {
        mvc.perform(get("/api/products/" + productId).session(admin))
                .andExpect(jsonPath("$.data.imageUrl").value(Matchers.nullValue()));
        mvc.perform(get("/api/products/" + productId + "/image").session(admin)).andExpect(status().isNotFound());

        MvcResult up = mvc.perform(TestAuth.withCsrf(mvc, multipart("/api/products/" + productId + "/image")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", PNG)), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl", Matchers.startsWith("/api/products/" + productId + "/image?v=")))
                .andReturn();
        String url = data(up).path("imageUrl").asText();

        mvc.perform(get(url).session(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", Matchers.startsWith("image/png")))
                .andExpect(header().string("Cache-Control", Matchers.containsString("max-age")))
                .andExpect(content().bytes(PNG));
        // the list carries the same URL, and the picture needs a login like everything else
        mvc.perform(get("/api/products").session(admin))
                .andExpect(jsonPath("$.data[0].imageUrl").value(url));
        mvc.perform(get(url)).andExpect(status().isUnauthorized());

        // replacing changes the bytes and the version in the URL
        byte[] other = {1, 2, 3, 4, 5};
        Thread.sleep(5);
        String url2 = data(mvc.perform(TestAuth.withCsrf(mvc, multipart("/api/products/" + productId + "/image")
                        .file(new MockMultipartFile("file", "photo.jpg", "image/jpeg", other)), admin))
                .andExpect(status().isOk()).andReturn()).path("imageUrl").asText();
        assertThat(url2).isNotEqualTo(url);
        mvc.perform(get(url2).session(admin))
                .andExpect(header().string("Content-Type", Matchers.startsWith("image/jpeg")))
                .andExpect(content().bytes(other));

        // only photos are accepted, and a file is required
        mvc.perform(TestAuth.withCsrf(mvc, multipart("/api/products/" + productId + "/image")
                        .file(new MockMultipartFile("file", "x.svg", "image/svg+xml", "<svg/>".getBytes())), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.file").value("imageType"));
        mvc.perform(TestAuth.withCsrf(mvc, multipart("/api/products/" + productId + "/image"), admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.file").value("required"));

        mvc.perform(TestAuth.withCsrf(mvc, delete("/api/products/" + productId + "/image"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(Matchers.nullValue()));
        mvc.perform(get("/api/products/" + productId + "/image").session(admin)).andExpect(status().isNotFound());
        mvc.perform(TestAuth.withCsrf(mvc, multipart("/api/products/999999/image")
                        .file(new MockMultipartFile("file", "photo.png", "image/png", PNG)), admin))
                .andExpect(status().isNotFound());
    }

    private JsonNode data(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("data");
    }
}
