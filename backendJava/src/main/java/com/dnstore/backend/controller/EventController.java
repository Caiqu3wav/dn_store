package com.dnstore.backend.controller;

import com.dnstore.backend.model.Event;
import com.dnstore.backend.repository.EventRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventRepository eventRepository;

    /** GET /api/events — lista eventos ativos ordenados por data (público) */
    @GetMapping
    public ResponseEntity<List<Event>> listActive() {
        return ResponseEntity.ok(eventRepository.findByActiveTrueOrderByDateAsc());
    }

    /** GET /api/events/all — lista todos incluindo inativos (ADMIN) */
    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Event>> listAll() {
        return ResponseEntity.ok(eventRepository.findAll());
    }

    /** GET /api/events/{id} */
    @GetMapping("/{id}")
    public ResponseEntity<Event> getById(@PathVariable UUID id) {
        return eventRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /api/events (ADMIN) */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Event> create(@Valid @RequestBody EventRequest request) {
        Event event = toEntity(new Event(), request);
        Event saved = eventRepository.save(event);
        log.info("event.created id={} name={}", saved.getId(), saved.getName());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    /** PUT /api/events/{id} (ADMIN) */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Event> update(@PathVariable UUID id, @Valid @RequestBody EventRequest request) {
        return eventRepository.findById(id).map(event -> {
            toEntity(event, request);
            Event saved = eventRepository.save(event);
            log.info("event.updated id={}", id);
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    /** DELETE /api/events/{id} (ADMIN) — soft delete */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        return eventRepository.findById(id).map(event -> {
            event.setActive(false);
            eventRepository.save(event);
            log.info("event.deleted id={}", id);
            return ResponseEntity.noContent().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private Event toEntity(Event event, EventRequest r) {
        event.setName(r.name());
        event.setDescription(r.description());
        event.setDate(r.date());
        event.setLocation(r.location());
        event.setMapUrl(r.mapUrl());
        event.setImageUrl(r.imageUrl());
        event.setRegistrationUrl(r.registrationUrl());
        event.setActive(r.active() != null ? r.active() : true);
        return event;
    }

    public record EventRequest(
            @NotBlank String name,
            String description,
            @NotNull LocalDate date,
            String location,
            String mapUrl,
            String imageUrl,
            String registrationUrl,
            Boolean active
    ) {}
}
