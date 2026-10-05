package com.dnstore.backend.controller;

import com.dnstore.backend.service.ZipCodeService;
import com.dnstore.backend.service.impl.ViaCepResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/zip-code")
@RequiredArgsConstructor
public class ZipCodeController {

    private final ZipCodeService zipCodeService;

    @GetMapping("/{zipCode}")
    public ResponseEntity<ZipCodeResponse> getAddress(@PathVariable String zipCode) {
        ViaCepResponse address = zipCodeService.getAddress(zipCode);
        return ResponseEntity.ok(ZipCodeResponse.from(address));
    }

    public record ZipCodeResponse(
            String zipCode,
            String street,
            String complement,
            String neighborhood,
            String city,
            String state) {
        private static ZipCodeResponse from(ViaCepResponse address) {
            return new ZipCodeResponse(
                    address.getCep(),
                    address.getLogradouro(),
                    address.getComplemento(),
                    address.getBairro(),
                    address.getLocalidade(),
                    address.getUf());
        }
    }
}
