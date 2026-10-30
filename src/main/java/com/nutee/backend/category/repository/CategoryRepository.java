package com.nutee.backend.category.repository;

import com.nutee.backend.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

/** Dùng method có sẵn của Spring Data JPA để đọc danh mục. */
public interface CategoryRepository extends JpaRepository<Category, Integer> {}
