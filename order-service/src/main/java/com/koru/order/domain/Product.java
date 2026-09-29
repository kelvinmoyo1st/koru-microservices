
package com.koru.order.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "products")
public class Product {

    @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
            private Long id;

                @Column(nullable = false)
                    private String name;

                        @Column(nullable = false, unique = true)
                            private String sku;

                                @Column(nullable = false)
                                    private long priceCents;

                                        @Column(nullable = false)
                                            private int stockQuantity;

                                                @Version
                                                    private Long version;

                                                        protected Product() {
                                                                // required by JPA/Hibernate to build entities via reflection
                                                                    }

                                                                        public Product(String name, String sku, long priceCents, int stockQuantity) {
                                                                                this.name = name;
                                                                                        this.sku = sku;
                                                                                                this.priceCents = priceCents;
                                                                                                        this.stockQuantity = stockQuantity;
                                                                                                            }

                                                                                                                public Long getId() { return id; }
                                                                                                                    public String getName() { return name; }
                                                                                                                        public String getSku() { return sku; }
                                                                                                                            public long getPriceCents() { return priceCents; }
                                                                                                                                public int getStockQuantity() { return stockQuantity; }
                                                                                                                                    public Long getVersion() { return version; }

                                                                                                                                        public void decreaseStock(int quantity) {
                                                                                                                                                if (quantity > this.stockQuantity) {
                                                                                                                                                            throw new IllegalStateException("Insufficient stock for SKU " + sku);
                                                                                                                                                                    }
                                                                                                                                                                            this.stockQuantity -= quantity;
                                                                                                                                                                                }
                                                                                                                                                                                }
                                                                                                                                                                        