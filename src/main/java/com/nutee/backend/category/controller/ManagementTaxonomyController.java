package com.nutee.backend.category.controller;

import com.nutee.backend.category.service.ManagementTaxonomyService;
import com.nutee.backend.category.dto.ManagementTaxonomyItem;
import com.nutee.backend.category.dto.ManagementTaxonomyRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/management/taxonomy")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF') and @managementAccess.isStaffOrAdmin(authentication)")
@RequiredArgsConstructor
public class ManagementTaxonomyController {
    private final ManagementTaxonomyService service;

    @GetMapping("/{kind}")
    public List<ManagementTaxonomyItem> list(@PathVariable String kind, @RequestParam(defaultValue = "") String q) {
        return service.list(kind, q);
    }

    @PostMapping("/{kind}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void create(@PathVariable String kind, @Valid @RequestBody ManagementTaxonomyRequest request) { service.create(kind, request); }

    @PutMapping("/{kind}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String kind, @PathVariable int id, @Valid @RequestBody ManagementTaxonomyRequest request) {
        service.update(kind, id, request);
    }

    @DeleteMapping("/{kind}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String kind, @PathVariable int id) { service.delete(kind, id); }
}
