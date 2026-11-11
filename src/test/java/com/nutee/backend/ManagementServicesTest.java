package com.nutee.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.nutee.backend.brand.repository.BrandRepository;
import com.nutee.backend.category.repository.CategoryRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.product.dto.ManagementVariantRequest;
import com.nutee.backend.product.entity.Product;
import com.nutee.backend.product.image.repository.ProductImageRepository;
import com.nutee.backend.product.repository.ProductRepository;
import com.nutee.backend.product.service.ManagementProductService;
import com.nutee.backend.product.variant.entity.ProductVariant;
import com.nutee.backend.product.variant.repository.ProductVariantRepository;
import com.nutee.backend.product.variant.specification.repository.VariantSpecificationRepository;
import com.nutee.backend.user.dto.ManagementUserAccessRequest;
import com.nutee.backend.user.repository.UserRepository;
import com.nutee.backend.user.service.ManagementUserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

class ManagementServicesTest {
    @Test void rejectsStaleStockInsteadOfOverwritingAReservation() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        VariantSpecificationRepository specs = mock(VariantSpecificationRepository.class);
        ManagementProductService service = new ManagementProductService(products, variants, mock(ProductImageRepository.class),
                specs, mock(BrandRepository.class), mock(CategoryRepository.class));
        Product product = mock(Product.class);
        ProductVariant variant = mock(ProductVariant.class);
        when(products.findLockedById(1)).thenReturn(Optional.of(product));
        when(product.getId()).thenReturn(1);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getQuantity()).thenReturn(3);
        when(variants.findLockedByIdIn(eq(List.of(2)), any())).thenReturn(List.of(variant));
        var request = new ManagementVariantRequest("SKU", 100, 8, 4, null, List.of());
        assertThatThrownBy(() -> service.saveVariant(1, 2, request)).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.CONFLICT));
        verify(variant, never()).update(anyString(), anyInt(), anyInt(), any());
        verifyNoInteractions(specs);
    }

    @Test void refusesToDeleteDefaultVariant() {
        ProductRepository products = mock(ProductRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        ManagementProductService service = new ManagementProductService(products, variants, mock(ProductImageRepository.class),
                mock(VariantSpecificationRepository.class), mock(BrandRepository.class), mock(CategoryRepository.class));
        Product product = mock(Product.class);
        when(products.findLockedById(1)).thenReturn(Optional.of(product));
        when(product.getDefaultVariantId()).thenReturn(2);
        assertThatThrownBy(() -> service.deleteVariant(1, 2)).isInstanceOf(ApiException.class);
        verifyNoInteractions(variants);
    }

    @Test void cannotChangeOwnAccess() {
        UserRepository users = mock(UserRepository.class);
        ManagementUserService service = new ManagementUserService(users, mock(PasswordEncoder.class));
        UUID actor = UUID.randomUUID();
        assertThatThrownBy(() -> service.update(actor, actor, new ManagementUserAccessRequest("customer", false)))
                .isInstanceOf(ApiException.class);
        verifyNoInteractions(users);
    }
}
