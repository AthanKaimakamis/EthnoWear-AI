package fmi.ethnowear.application.service.user;

import fmi.ethnowear.application.exception.UserDeletionConflictException;
import fmi.ethnowear.domain.model.user.RoleName;
import fmi.ethnowear.persistence.jpa.entity.user.User;
import fmi.ethnowear.persistence.jpa.repository.user.RoleRepository;
import fmi.ethnowear.persistence.jpa.repository.user.UserInfoRepository;
import fmi.ethnowear.persistence.jpa.repository.user.UserRepository;
import fmi.ethnowear.persistence.jpa.repository.user.UserRoleRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-02T12:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserInfoRepository userInfoRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private TemporaryPasswordGenerator passwordGenerator;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private UserMapper mapper;
    @Mock
    private UserQueryService queryService;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        service = new UserManagementService(
                userRepository,
                userInfoRepository,
                roleRepository,
                userRoleRepository,
                passwordGenerator,
                passwordEncoder,
                mapper,
                queryService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void softDeletesUserAndRevokesCredentials() {
        User administrator = user(1L, "administrator");
        User editor = user(2L, "editor");
        int previousTokenVersion = editor.getTokenVersion();

        when(userRepository.findByIdAndDeletedAtIsNull(2L)).thenReturn(Optional.of(editor));
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(administrator));
        when(userRoleRepository.existsByUser_IdAndRole_Name(2L, RoleName.ADMINISTRATOR))
                .thenReturn(false);

        service.delete(2L, 1L);

        assertFalse(editor.isEnabled());
        assertNull(editor.getPasswordHash());
        assertTrue(editor.isMustChangePassword());
        assertNull(editor.getTemporaryPasswordExpiresAt());
        assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), editor.getDeletedAt());
        assertSame(administrator, editor.getDeletedByUser());
        assertEquals(previousTokenVersion + 1, editor.getTokenVersion());
        verify(userRepository).saveAndFlush(editor);
    }

    @Test
    void rejectsSelfDeletion() {
        User administrator = user(1L, "administrator");
        when(userRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(administrator));

        UserDeletionConflictException exception = assertThrows(
                UserDeletionConflictException.class,
                () -> service.delete(1L, 1L)
        );

        assertEquals("USER_SELF_DELETE_FORBIDDEN", exception.getCode());
        verify(userRepository, never()).saveAndFlush(any());
    }

    private User user(Long id, String username) {
        User user = new User(username, null);
        EntityTestUtils.setId(user, id);
        user.assignPassword("encoded-password", false, null);
        user.enable();
        return user;
    }
}
