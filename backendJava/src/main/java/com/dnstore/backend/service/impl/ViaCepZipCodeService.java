package com.dnstore.backend.service.impl;

import com.dnstore.backend.exception.DeliveryException;
import com.dnstore.backend.service.ZipCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class ViaCepZipCodeService implements ZipCodeService {

    private final RestTemplate restTemplate;
    private static final String BASE_URL = "https://viacep.com.br/ws/%s/json/";

    @Override
    public ViaCepResponse getAddress(String zipCode) {
        if (zipCode == null || zipCode.replaceAll("\\D", "").length() != 8) {
            throw new DeliveryException("Formato de CEP inválido: " + zipCode);
        }

        String formattedRequest = String.format(BASE_URL, zipCode.replaceAll("\\D", ""));
        log.info("Buscando endereço para o CEP: {}", zipCode);

        try {
            ViaCepResponse response = restTemplate.getForObject(formattedRequest, ViaCepResponse.class);
            
            if (response == null || Boolean.TRUE.equals(response.getErro())) {
                log.warn("CEP não encontrado: {}", zipCode);
                throw new DeliveryException("CEP não encontrado: " + zipCode);
            }
            
            return response;
        } catch (RestClientException e) {
            log.error("Erro na API externa ao buscar CEP: {}", e.getMessage());
            throw new DeliveryException("Erro ao validar CEP: serviço indisponível", e);
        } 
    }
}
