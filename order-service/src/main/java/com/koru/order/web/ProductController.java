package com.koru.order.web;

import com.koru.order.domain.Product;
import com.koru.order.repository.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody CreateProductRequest request) {
        Product product = new Product(request.name(), request.sku(), request.priceCents(), request.stockQuantity());
        Product saved = productRepository.save(product);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProductResponse.from(saved));
    }
}
