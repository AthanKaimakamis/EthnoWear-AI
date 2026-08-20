package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.application.dto.auth.CurrentUserDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.domain.model.user.RoleName;
import fmi.ethnowear.persistence.jpa.entity.user.*;
import fmi.ethnowear.persistence.jpa.repository.user.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CurrentUserService {

    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;
    private final UserRoleRepository userRoleRepository;

    public CurrentUserDetails getCurrentUser(Long userId) {
        requireId(userId, "User");

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserInfo info = userInfoRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User information", userId));

        Set<RoleName> roles = userRoleRepository
                .findWithRolesByUserId(userId)
                .stream()
                .map(UserRole::getRole)
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());

        return new CurrentUserDetails(
                user.getId(),
                user.getUsername(),
                info.getFirstName(),
                info.getLastName(),
                info.getEmail(),
                roles,
                user.isMustChangePassword()
        );
    }
}
