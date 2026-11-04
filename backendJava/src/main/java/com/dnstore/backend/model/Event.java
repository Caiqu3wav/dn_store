package com.dnstore.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    @Column(length = 150)
    private String location;

    @Column(name = "map_url", length = 500)
    private String mapUrl;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "registration_url", length = 500)
    private String registrationUrl;

    @Column(nullable = false)
    private boolean active = true;
}
