package com.bankms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "branches")
@Getter
@Setter
@NoArgsConstructor
public class Branch extends BaseEntity {

    @Column(nullable = false, length = 6, updatable = false)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 11, updatable = false)
    private String ifsc;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(length = 15)
    private String phone;

    @Column(nullable = false)
    private boolean active = true;
}
