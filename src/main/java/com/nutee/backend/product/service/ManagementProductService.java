package com.nutee.backend.product.service;

import com.nutee.backend.brand.entity.Brand;
import com.nutee.backend.brand.repository.BrandRepository;
import com.nutee.backend.category.entity.Category;
import com.nutee.backend.category.repository.CategoryRepository;
import com.nutee.backend.common.exception.ApiException;
import com.nutee.backend.common.response.PageResponse;
import com.nutee.backend.product.dto.*;
import com.nutee.backend.product.entity.Product;
import com.nutee.backend.product.image.entity.ProductImage;
import com.nutee.backend.product.image.repository.ProductImageRepository;
import com.nutee.backend.product.repository.ProductRepository;
import com.nutee.backend.product.variant.entity.ProductVariant;
import com.nutee.backend.product.variant.repository.ProductVariantRepository;
import com.nutee.backend.product.variant.specification.entity.VariantSpecification;
import com.nutee.backend.product.variant.specification.repository.VariantSpecificationRepository;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ManagementProductService {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;
    private final VariantSpecificationRepository specifications;
    private final BrandRepository brands;
    private final CategoryRepository categories;

    @Transactional(readOnly = true)
    public PageResponse<ManagementProductSummary> list(String q, int page, int size) {
        return PageResponse.from(products.findByNameContainingIgnoreCase(q.strip(), PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"))))
                .map(p -> new ManagementProductSummary(p.getId(), p.getName(), Boolean.TRUE.equals(p.getPublished()), p.getUpdatedAt())));
    }

    @Transactional(readOnly = true)
    public ManagementProductResponse get(int id) { return response(products.findById(id).orElseThrow(ManagementProductService::missing)); }

    @Transactional
    public ManagementProductResponse create(ManagementProductRequest request) {
        if (request.defaultVariantId() != null) throw invalid("Sản phẩm mới chưa có biến thể mặc định.");
        Product product = Product.create(request.name().strip(), request.description(), request.published(), brand(request.brandId()), categorySet(request.categoryIds()));
        return response(products.saveAndFlush(product));
    }

    @Transactional
    public ManagementProductResponse update(int id, ManagementProductRequest request) {
        Product product = lockedProduct(id);
        if (request.defaultVariantId() != null) variant(id, request.defaultVariantId());
        product.update(request.name().strip(), request.description(), request.published(), brand(request.brandId()), categorySet(request.categoryIds()), request.defaultVariantId());
        products.flush();
        return response(product);
    }

    @Transactional
    public void delete(int id) {
        Product product = lockedProduct(id);
        product.update(product.getName(), product.getDescription(), false, product.getBrand(), Set.of(), null);
        products.flush();
        for (ProductVariant variant : variants.findByProduct_IdOrderById(id)) {
            specifications.deleteAll(specifications.findByVariant_IdOrderById(variant.getId()));
            specifications.flush();
            variants.delete(variant);
        }
        variants.flush();
        images.deleteAll(images.findByProduct_IdOrderById(id));
        images.flush();
        products.delete(product);
        products.flush();
    }

    @Transactional
    public ManagementProductResponse saveVariant(int productId, Integer variantId, ManagementVariantRequest request) {
        Product product = lockedProduct(productId);
        ProductImage image = request.imageId() == null ? null : image(productId, request.imageId());
        Set<String> names = new HashSet<>();
        for (var spec : request.specifications()) {
            if (!names.add(spec.name().strip())) throw invalid("Tên thông số không được trùng.");
        }
        ProductVariant variant;
        if (variantId == null) {
            variant = variants.saveAndFlush(ProductVariant.create(product, request.sku().strip(), request.price(), request.quantity(), image));
        } else {
            variant = variants.findLockedByIdIn(List.of(variantId), Sort.by("id")).stream().findFirst().orElseThrow(ManagementProductService::missing);
            if (!variant.getProduct().getId().equals(productId)) throw missing();
            if (request.expectedQuantity() == null || !request.expectedQuantity().equals(variant.getQuantity())) {
                throw new ApiException(HttpStatus.CONFLICT, "Tồn kho đã thay đổi. Vui lòng tải lại trước khi lưu.");
            }
            variant.update(request.sku().strip(), request.price(), request.quantity(), image);
            specifications.deleteAll(specifications.findByVariant_IdOrderById(variantId));
            specifications.flush();
        }
        for (var spec : request.specifications()) specifications.save(VariantSpecification.create(variant, spec.name().strip(), spec.value().strip()));
        specifications.flush();
        variants.flush();
        return response(product);
    }

    @Transactional
    public void deleteVariant(int productId, int variantId) {
        Product product = lockedProduct(productId);
        if (Integer.valueOf(variantId).equals(product.getDefaultVariantId())) throw invalid("Hãy đổi biến thể mặc định trước khi xóa.");
        ProductVariant variant = variant(productId, variantId);
        specifications.deleteAll(specifications.findByVariant_IdOrderById(variantId));
        specifications.flush();
        variants.delete(variant);
        variants.flush();
    }

    @Transactional
    public ManagementProductResponse saveImage(int productId, Integer imageId, ManagementImageRequest request) {
        Product product = lockedProduct(productId);
        if (request.primary()) for (ProductImage existing : images.findByProduct_IdOrderById(productId)) existing.update(existing.getImageUrl(), false);
        if (imageId == null) images.save(ProductImage.create(product, request.url().strip(), request.primary()));
        else image(productId, imageId).update(request.url().strip(), request.primary());
        images.flush();
        return response(product);
    }

    @Transactional
    public void deleteImage(int productId, int imageId) {
        lockedProduct(productId);
        images.delete(image(productId, imageId));
        images.flush();
    }

    private ManagementProductResponse response(Product product) {
        int id = product.getId();
        var productImages = images.findByProduct_IdOrderById(id).stream()
                .map(image -> new ManagementProductResponse.Image(image.getId(), image.getImageUrl(), image.isPrimaryImage())).toList();
        var rows = variants.findByProduct_IdOrderById(id);
        Map<Integer, List<VariantSpecification>> groupedSpecs = rows.isEmpty() ? Map.of()
                : specifications.findByVariant_IdIn(rows.stream().map(ProductVariant::getId).toList(), Sort.by("id"))
                        .stream().collect(Collectors.groupingBy(spec -> spec.getVariant().getId()));
        var productVariants = rows.stream().map(variant -> new ManagementProductResponse.Variant(
                variant.getId(), variant.getSku(), variant.getPrice(), variant.getQuantity(), variant.getImage() == null ? null : variant.getImage().getId(),
                groupedSpecs.getOrDefault(variant.getId(), List.of()).stream().map(spec -> new ManagementProductResponse.Spec(spec.getSpecificationName(), spec.getSpecificationValue())).toList())).toList();
        return new ManagementProductResponse(id, product.getName(), product.getDescription(), Boolean.TRUE.equals(product.getPublished()),
                product.getBrand() == null ? null : product.getBrand().getId(), product.getCategories().stream().map(Category::getId).toList(),
                product.getDefaultVariantId(), product.getUpdatedAt(), productImages, productVariants);
    }

    private Product lockedProduct(int id) { return products.findLockedById(id).orElseThrow(ManagementProductService::missing); }
    private ProductVariant variant(int productId, int id) {
        ProductVariant variant = variants.findById(id).orElseThrow(ManagementProductService::missing);
        if (!variant.getProduct().getId().equals(productId)) throw missing();
        return variant;
    }
    private ProductImage image(int productId, int id) {
        ProductImage image = images.findById(id).orElseThrow(ManagementProductService::missing);
        if (!image.getProduct().getId().equals(productId)) throw missing();
        return image;
    }
    private Brand brand(Integer id) { return id == null ? null : brands.findById(id).orElseThrow(ManagementProductService::missing); }
    private Set<Category> categorySet(List<Integer> ids) {
        Set<Integer> unique = new HashSet<>(ids);
        List<Category> found = categories.findAllById(unique);
        if (found.size() != unique.size()) throw invalid("Danh mục không tồn tại.");
        return new LinkedHashSet<>(found);
    }
    private static ApiException missing() { return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy dữ liệu."); }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST, message); }
}
