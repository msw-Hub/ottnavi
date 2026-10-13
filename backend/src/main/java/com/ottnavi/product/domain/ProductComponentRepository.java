package com.ottnavi.product.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductComponentRepository extends JpaRepository<ProductComponent, ProductComponentId>  {
}
