package fmi.ethnowear.persistence.jpa.repository.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.user.admin.AdminUserController;
import fmi.ethnowear.application.dto.user.UserProfileCommand;
import fmi.ethnowear.application.service.user.UserManagementService;
import fmi.ethnowear.application.service.user.UserMapper;
import fmi.ethnowear.application.service.user.UserQueryService;
import fmi.ethnowear.persistence.jpa.entity.user.User;
import fmi.ethnowear.persistence.jpa.entity.user.UserInfo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.use_nationalized_character_data=true")
@AutoConfigureTestDatabase(replace = NONE)
@Import({UserQueryService.class, UserMapper.class})
@EnabledIfEnvironmentVariable(named = "ETHNOWEAR_LIVE_DB_TESTS", matches = "true")
class UserInfoPersistenceTest {
    @Autowired private UserRepository users;
    @Autowired private UserInfoRepository profiles;
    @Autowired private UserQueryService queries;
    @Autowired private UserMapper mapper;
    @Autowired private EntityManager entityManager;

    private Long userId;
    private String username;
    private UserManagementService management;

    @BeforeEach
    void setUp() {
        // Synthetic fixtures and profile writes roll back with each test.
        username = "country-code-test-" + UUID.randomUUID();
        User user = users.saveAndFlush(new User(username, null));
        userId = user.getId();
        UserInfo info = new UserInfo(user);
        mapper.apply(info, profile("bg"));
        profiles.saveAndFlush(info);
        entityManager.clear();
        management = new UserManagementService(users, profiles, null, null, null, null,
                mapper, queries, Clock.systemUTC());
    }

    @Test
    void listsAndReadsProfileWithNonNullCharCountryCode() {
        var result = queries.findAll(username, PageRequest.of(0, 10));
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().getFirst().id()).isEqualTo(userId);
        entityManager.clear();
        assertThat(queries.findById(userId).profile().countryCode()).isEqualTo("BG");
        assertThat(queries.findById(userId).profile().firstName()).isEqualTo("Иван");
    }

    @Test
    void updatesAndReloadsCountryCodeIncludingNullWithoutLosingUnicode() {
        for(String code : new String[]{"US", null, "bg"}) {
            management.updateProfile(userId, profile(code));
            entityManager.clear();
            var details = queries.findById(userId);
            assertThat(details.profile().countryCode()).isEqualTo(code == null ? null : code.toUpperCase(java.util.Locale.ROOT));
            assertThat(details.profile().firstName()).isEqualTo("Иван");
            assertThat(details.profile().city()).isEqualTo("София");
            assertThat(queries.findAll(username, PageRequest.of(0, 10)).getTotalElements()).isEqualTo(1);
            entityManager.clear();
        }
    }

    @Test
    void userListDetailsAndProfileEndpointsRoundTripAgainstSqlServer() throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminUserController(queries, management))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver()).build();
        mvc.perform(get("/api/admin/users").param("search", username))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(userId));
        entityManager.clear();
        mvc.perform(get("/api/admin/users/{id}", userId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.countryCode").value("BG"));
        mvc.perform(put("/api/admin/users/{id}/profile", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new ObjectMapper().writeValueAsString(profile("us"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.countryCode").value("US"));
        entityManager.clear();
        mvc.perform(get("/api/admin/users/{id}", userId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.profile.countryCode").value("US"));
    }

    private UserProfileCommand profile(String code) {
        return new UserProfileCommand("Иван", "Иванов", null, null, null, null, "София", null, code);
    }
}
