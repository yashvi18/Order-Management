package com.ecommerce.oms.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address {

    @Column(name = "ship_line1", length = 200)
    private String line1;

    @Column(name = "ship_line2", length = 200)
    private String line2;

    @Column(name = "ship_city", length = 100)
    private String city;

    @Column(name = "ship_state", length = 100)
    private String state;

    @Column(name = "ship_postal_code", length = 12)
    private String postalCode;

    @Column(name = "ship_country", length = 2)
    private String country;
}
