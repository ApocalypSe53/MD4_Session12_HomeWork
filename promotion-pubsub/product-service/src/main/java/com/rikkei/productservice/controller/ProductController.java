package com.rikkei.productservice.controller;

import com.rikkei.productservice.dto.ProductView;
import com.rikkei.productservice.service.ProductService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    private final ProductService productService;
    private final String instanceId;

    public ProductController(ProductService productService, @Value("${app.instance-id:product}") String instanceId) {
        this.productService = productService;
        this.instanceId = instanceId;
    }

    @GetMapping("/api/products/{id}")
    public ResponseEntity<ProductView> getById(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header("X-Served-By", instanceId)
                .body(productService.getById(id));
    }
}
