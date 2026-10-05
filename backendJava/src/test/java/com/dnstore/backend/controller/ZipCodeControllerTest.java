package com.dnstore.backend.controller;

import com.dnstore.backend.exception.DeliveryException;
import com.dnstore.backend.exception.GlobalExceptionHandler;
import com.dnstore.backend.service.ZipCodeService;
import com.dnstore.backend.service.impl.ViaCepResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ZipCodeControllerTest {

    private ZipCodeService zipCodeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        zipCodeService = mock(ZipCodeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ZipCodeController(zipCodeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAddress_shouldReturnAddressFieldsFromViaCep() throws Exception {
        ViaCepResponse address = new ViaCepResponse();
        address.setCep("01310-100");
        address.setLogradouro("Avenida Paulista");
        address.setComplemento("lado ímpar");
        address.setBairro("Bela Vista");
        address.setLocalidade("São Paulo");
        address.setUf("SP");
        address.setErro(false);
        when(zipCodeService.getAddress("01310-100")).thenReturn(address);

        mockMvc.perform(get("/api/zip-code/01310-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zipCode").value("01310-100"))
                .andExpect(jsonPath("$.street").value("Avenida Paulista"))
                .andExpect(jsonPath("$.complement").value("lado ímpar"))
                .andExpect(jsonPath("$.neighborhood").value("Bela Vista"))
                .andExpect(jsonPath("$.city").value("São Paulo"))
                .andExpect(jsonPath("$.state").value("SP"))
                .andExpect(jsonPath("$.erro").doesNotExist());
    }

    @Test
    void getAddress_shouldPropagateInvalidZipCodeError() throws Exception {
        when(zipCodeService.getAddress("123"))
                .thenThrow(new DeliveryException("Formato de CEP inválido: 123"));

        mockMvc.perform(get("/api/zip-code/123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(HttpStatus.BAD_REQUEST.value()))
                .andExpect(jsonPath("$.message").value("Formato de CEP inválido: 123"));
    }

    @Test
    void getAddress_shouldDelegateZipCodeToService() throws Exception {
        when(zipCodeService.getAddress("20040-020")).thenReturn(new ViaCepResponse());

        mockMvc.perform(get("/api/zip-code/20040-020"))
                .andExpect(status().isOk());

        verify(zipCodeService).getAddress("20040-020");
    }
}
