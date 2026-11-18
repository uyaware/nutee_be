package com.nutee.backend.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.user.dto.ManagementUserAccessRequest;
import com.nutee.backend.user.dto.ManagementUserCreateRequest;
import com.nutee.backend.user.entity.User;
import com.nutee.backend.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class ManagementUserServiceTest {
    @Mock UserRepository users;
    @Mock PasswordEncoder passwords;
    ManagementUserService service;

    @BeforeEach void setup() { service = new ManagementUserService(users, passwords); }

    @Test void customerCannotBeReadOrChanged() {
        User customer = User.create("customer", "customer@example.com", "hash", "Customer", true, null);
        when(users.findById(customer.getId())).thenReturn(Optional.of(customer));
        when(users.findLockedById(customer.getId())).thenReturn(Optional.of(customer));
        when(users.findActiveAdminsLocked()).thenReturn(List.of());
        assertThatThrownBy(() -> service.get(customer.getId())).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), customer.getId(), new ManagementUserAccessRequest("staff", true)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(customer.getRole()).isEqualTo("CUSTOMER");
    }

    @Test void staffCanBecomeAdminButCannotBecomeCustomer() {
        User staff = User.create("staff", "staff@example.com", "hash", "Staff", true, null);
        staff.updateAccess("STAFF", true);
        when(users.findLockedById(staff.getId())).thenReturn(Optional.of(staff));
        when(users.findActiveAdminsLocked()).thenReturn(List.of());
        assertThatThrownBy(() -> service.update(UUID.randomUUID(), staff.getId(), new ManagementUserAccessRequest("customer", true)))
                .isInstanceOfSatisfying(ApiException.class, error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(service.update(UUID.randomUUID(), staff.getId(), new ManagementUserAccessRequest("admin", true)).role()).isEqualTo("admin");
    }

    @Test void customerCreationIsRejectedBeforeWriting() {
        var request = new ManagementUserCreateRequest("customer@example.com", "Password123!", "Customer", "customer");
        assertThatThrownBy(() -> service.create(request)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(users, never()).saveAndFlush(any());
    }
}
