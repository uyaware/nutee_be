package com.nutee.backend.brand.repository;

import com.nutee.backend.brand.entity.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

/** Dùng trực tiếp các method CRUD và sắp xếp có sẵn của Spring Data JPA. */
public interface BrandRepository extends JpaRepository<Brand, Integer> {}
