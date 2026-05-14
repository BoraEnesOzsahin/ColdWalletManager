package com.ayrotek.coldwalletmanagerservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "address", nullable = false, unique = true)
    private String address;

    @Column(name = "public_key", nullable = false, unique = true, length = 200)
    private String publicKey;

    @Column(name = "generation_time", nullable = false)
    private LocalDateTime generationTime;

    @Column(name = "hsm_alias", unique = true)
    private String hsmAlias;

    // Constructors
    public Wallet() {
    }

    public Wallet(String name, String address, String publicKey, LocalDateTime generationTime) {
        this.name = name;
        this.address = address;
        this.publicKey = publicKey;
        this.generationTime = generationTime;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public LocalDateTime getGenerationTime() {
        return generationTime;
    }

    public void setGenerationTime(LocalDateTime generationTime) {
        this.generationTime = generationTime;
    }

    public String getHsmAlias() {
        return hsmAlias;
    }

    public void setHsmAlias(String hsmAlias) {
        this.hsmAlias = hsmAlias;
    }
}
