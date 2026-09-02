package fmi.ethnowear.application.service.user;

import fmi.ethnowear.application.dto.user.*;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.domain.model.user.RoleName;
import fmi.ethnowear.persistence.jpa.entity.user.*;
import fmi.ethnowear.persistence.jpa.repository.user.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserQueryService {

    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserMapper mapper;

    public Page<UserSummaryDetails> findAll(String search, Pageable pageable) {
        Page<User> users = userRepository.search(
                normalizeSearch(search),
                pageable
        );

        List<Long> userIds = users.stream()
                .map(User::getId)
                .toList();

        Map<Long, UserInfo> information = userIds.isEmpty()
                ? Map.of()
                : userInfoRepository.findAllByUserIdIn(userIds)
                .stream()
                .collect(Collectors.toMap(
                        UserInfo::getUserId,
                        info -> info
                ));

        Map<Long, Set<RoleName>> roles = rolesByUserId(userIds);

        return users.map(user -> mapper.toSummary(
                user,
                requireInfo(information, user.getId()),
                roles.getOrDefault(user.getId(), Set.of())
        ));
    }

    public UserDetails findById(Long userId) {
        requireId(userId, "User");

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        UserInfo info = userInfoRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User information", userId));

        return mapper.toDetails(
                user,
                info,
                rolesByUserId(List.of(userId))
                        .getOrDefault(userId, Set.of())
        );
    }

    private Map<Long, Set<RoleName>> rolesByUserId(@NonNull List<Long> userIds) {
        if(userIds.isEmpty())
            return Map.of();

        return userRoleRepository
                .findWithRolesByUserIds(userIds)
                .stream()
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getUser().getId(),
                        Collectors.mapping(
                                assignment -> assignment.getRole().getName(),
                                Collectors.toUnmodifiableSet()
                        )
                ));
    }

    private @NonNull UserInfo requireInfo(@NonNull Map<Long, UserInfo> information, Long userId) {
        UserInfo info = information.get(userId);

        if (info == null)
            throw new ResourceNotFoundException("User information", userId);

        return info;
    }

    private String normalizeSearch(String search) {
        return search == null || search.isBlank()
                ? null
                : search.trim();
    }
}
