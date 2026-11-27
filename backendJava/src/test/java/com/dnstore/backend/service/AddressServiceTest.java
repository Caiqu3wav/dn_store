package com.dnstore.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dnstore.backend.model.Address;
import com.dnstore.backend.model.User;
import com.dnstore.backend.repository.AddressRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @InjectMocks
    private AddressService addressService;

    @Test
    void saveOrReuse_sameAddressWithDifferentFormatting_shouldReuseExistingAddress() {
        User user = user();
        Address saved = address(user, "Rua  A", "123", null, "Sao Paulo", "SP", "01000-000");
        Address submitted = address(user, " rua A ", " 123 ", "", " SAO   PAULO ", "sp", "01000 000");
        when(addressRepository.findByUser_Id(user.getId())).thenReturn(List.of(saved));

        Address result = addressService.saveOrReuse(user, submitted);

        assertThat(result).isSameAs(saved);
        verify(addressRepository, never()).save(any(Address.class));
    }

    @Test
    void saveOrReuse_newAddress_shouldSaveNormalizedPostalCodeAndState() {
        User user = user();
        Address submitted = address(user, " Rua A ", " 123 ", "  ", "Centro", "sp", "01000-000");
        when(addressRepository.findByUser_Id(user.getId())).thenReturn(List.of());
        when(addressRepository.save(any(Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Address result = addressService.saveOrReuse(user, submitted);

        assertThat(result.getUser()).isSameAs(user);
        assertThat(result.getStreet()).isEqualTo("Rua A");
        assertThat(result.getNumber()).isEqualTo("123");
        assertThat(result.getComplement()).isNull();
        assertThat(result.getState()).isEqualTo("SP");
        assertThat(result.getZipCode()).isEqualTo("01000000");
    }

    private User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private Address address(
            User user,
            String street,
            String number,
            String complement,
            String city,
            String state,
            String zipCode) {
        Address address = new Address();
        address.setUser(user);
        address.setStreet(street);
        address.setNumber(number);
        address.setComplement(complement);
        address.setCity(city);
        address.setState(state);
        address.setZipCode(zipCode);
        return address;
    }
}
