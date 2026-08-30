package fmi.ethnowear.application.service.user;

import fmi.ethnowear.application.dto.user.*;
import fmi.ethnowear.domain.model.user.RoleName;
import fmi.ethnowear.persistence.jpa.entity.user.*;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

@Component
public class UserMapper {

    public UserSummaryDetails toSummary(User user, UserInfo info, Set<RoleName> roles) {
        return new UserSummaryDetails(
                user.getId(),
                user.getUsername(),
                info.getFirstName(),
                info.getLastName(),
                info.getEmail(),
                user.isEnabled(),
                user.isMustChangePassword(),
                roles
        );
    }

    public UserDetails toDetails(@NonNull User user, UserInfo info, Set<RoleName> roles) {
        return new UserDetails(
                user.getId(),
                user.getUsername(),
                toProfileDetails(info),
                roles,
                user.isEnabled(),
                user.isMustChangePassword(),
                user.getTemporaryPasswordExpiresAt(),
                user.getLockedUntil(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    public void apply(@NonNull UserInfo info, @NonNull UserProfileCommand command) {
        info.update(
                command.firstName().trim(),
                command.lastName().trim(),
                nullable(command.email()),
                nullable(command.phone()),
                nullable(command.addressLine1()),
                nullable(command.addressLine2()),
                nullable(command.city()),
                nullable(command.postalCode()),
                countryCode(command.countryCode())
        );
    }

    @Contract("_ -> new")
    private @NonNull UserProfileDetails toProfileDetails(@NonNull UserInfo info) {
        return new UserProfileDetails(
                info.getFirstName(),
                info.getLastName(),
                info.getEmail(),
                info.getPhone(),
                info.getAddressLine1(),
                info.getAddressLine2(),
                info.getCity(),
                info.getPostalCode(),
                info.getCountryCode()
        );
    }

    private String nullable(String value) {
        if (value == null || value.isBlank())
            return null;

        return value.trim();
    }

    private @Nullable String countryCode(String value) {
        String countryCode = nullable(value);

        return countryCode == null
                ? null
                : countryCode.toUpperCase(Locale.ROOT);
    }
}