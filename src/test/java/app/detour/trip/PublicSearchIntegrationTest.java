package app.detour.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PublicSearchIntegrationTest {
    private static final String DATABASE_URL = "jdbc:h2:mem:public_search_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> DATABASE_URL);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void anonymousSearchReturnsCatalogResultsWithoutPersistingTrip() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip", Integer.class);
        mvc.perform(get("/api/public/airfare")
                .param("destinationKey", "destination-sfo").param("startDate", "2027-03-01")
                .param("endDate", "2027-03-05").param("travelerCount", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").value(16));
        mvc.perform(get("/api/public/stays")
                .param("destinationKey", "destination-sfo").param("startDate", "2027-03-01")
                .param("endDate", "2027-03-05").param("travelerCount", "2").param("type", "HOTEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options.length()").isNumber());
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM detour_trip", Integer.class));
        mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousSearchValidatesTripInputs() throws Exception {
        mvc.perform(get("/api/public/airfare")
                .param("destinationKey", "destination-sfo").param("startDate", "2027-03-01")
                .param("endDate", "2027-03-05").param("travelerCount", "9"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.travelerCount").exists());
    }
}
