package com.ottnavi.product.domain.repository;

import com.ottnavi.product.domain.entity.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long>  {
}
