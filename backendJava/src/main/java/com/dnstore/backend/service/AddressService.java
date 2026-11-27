package com.dnstore.backend.service;

import com.dnstore.backend.model.Address;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.AddressRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional
    public Address saveOrReuse(User user, Address submitted) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException("Usuário autenticado é obrigatório.");
        }
        if (submitted == null) {
            throw new IllegalArgumentException("Endereço é obrigatório.");
        }

        Address normalized = normalize(user, submitted);
        if (normalized.getZipCode() != null && !normalized.getZipCode().isBlank()) {
            for (Address saved : addressRepository.findByUser_Id(user.getId())) {
                if (sameAddress(saved, normalized)) {
                    return saved;
                }
            }
        }

        return addressRepository.save(normalized);
    }

    private Address normalize(User user, Address submitted) {
        Address address = new Address();
        address.setUser(user);
        address.setStreet(clean(submitted.getStreet()));
        address.setNumber(clean(submitted.getNumber()));
        address.setComplement(clean(submitted.getComplement()));
        address.setNeighborhood(clean(submitted.getNeighborhood()));
        address.setCity(clean(submitted.getCity()));
        String state = clean(submitted.getState());
        address.setState(state == null ? null : state.toUpperCase(Locale.ROOT));
        address.setZipCode(submitted.getZipCode() == null
                ? null
                : submitted.getZipCode().replaceAll("\\D", ""));
        return address;
    }

    private boolean sameAddress(Address saved, Address submitted) {
        return sameText(saved.getStreet(), submitted.getStreet())
                && sameText(saved.getNumber(), submitted.getNumber())
                && sameText(saved.getComplement(), submitted.getComplement())
                && sameText(saved.getNeighborhood(), submitted.getNeighborhood())
                && sameText(saved.getCity(), submitted.getCity())
                && sameText(saved.getState(), submitted.getState())
                && sameText(normalizeZip(saved.getZipCode()), submitted.getZipCode());
    }

    private String normalizeZip(String zipCode) {
        return zipCode == null ? null : zipCode.replaceAll("\\D", "");
    }

    private boolean sameText(String left, String right) {
        return normalizeText(left).equals(normalizeText(right));
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim().replaceAll("\\s+", " ");
        return cleaned.isEmpty() ? null : cleaned;
    }
}
