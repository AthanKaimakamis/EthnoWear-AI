package fmi.ethnowear.api.controller.user;

import fmi.ethnowear.api.controller.user.admin.AdminUserController;
import fmi.ethnowear.application.service.user.UserManagementService;
import fmi.ethnowear.application.service.user.UserQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AdminUserControllerTest {

    @Test
    void deletesUserAsAuthenticatedAdministrator() {
        UserManagementService managementService = mock(UserManagementService.class);
        AdminUserController controller = new AdminUserController(
                mock(UserQueryService.class),
                managementService
        );
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("userId", 7L)
                .build();

        var response = controller.delete(jwt, 19L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(managementService).delete(19L, 7L);
    }
}
