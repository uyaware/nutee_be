package com.nutee.backend.product.controller;

import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.product.dto.*;
import com.nutee.backend.product.service.ManagementProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/products")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF') and @managementAccess.isStaffOrAdmin(authentication)")
@RequiredArgsConstructor
public class ManagementProductController {
    private final ManagementProductService service;

    @GetMapping
    public PageResponse<ManagementProductSummary> list(@RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") @Min(0) int page, @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(q, page, size);
    }
    @GetMapping("/{id}") public ManagementProductResponse get(@PathVariable int id) { return service.get(id); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ManagementProductResponse create(@Valid @RequestBody ManagementProductRequest request) { return service.create(request); }
    @PutMapping("/{id}") public ManagementProductResponse update(@PathVariable int id, @Valid @RequestBody ManagementProductRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable int id) { service.delete(id); }

    @PostMapping("/{id}/variants") @ResponseStatus(HttpStatus.CREATED)
    public ManagementProductResponse createVariant(@PathVariable int id, @Valid @RequestBody ManagementVariantRequest request) { return service.saveVariant(id, null, request); }
    @PutMapping("/{id}/variants/{variantId}")
    public ManagementProductResponse updateVariant(@PathVariable int id, @PathVariable int variantId, @Valid @RequestBody ManagementVariantRequest request) { return service.saveVariant(id, variantId, request); }
    @DeleteMapping("/{id}/variants/{variantId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteVariant(@PathVariable int id, @PathVariable int variantId) { service.deleteVariant(id, variantId); }

    @PostMapping("/{id}/images") @ResponseStatus(HttpStatus.CREATED)
    public ManagementProductResponse createImage(@PathVariable int id, @Valid @RequestBody ManagementImageRequest request) { return service.saveImage(id, null, request); }
    @PutMapping("/{id}/images/{imageId}")
    public ManagementProductResponse updateImage(@PathVariable int id, @PathVariable int imageId, @Valid @RequestBody ManagementImageRequest request) { return service.saveImage(id, imageId, request); }
    @DeleteMapping("/{id}/images/{imageId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable int id, @PathVariable int imageId) { service.deleteImage(id, imageId); }
}
