package com.dnstore.backend.service;

import com.dnstore.backend.service.impl.ViaCepResponse;

public interface ZipCodeService {
    /**
     * Valida e recupera informações de endereço para um CEP fornecido.
     * @param zipCode O CEP como string (formatado ou não).
     * @return Os detalhes do endereço.
     */
    ViaCepResponse getAddress(String zipCode);
}
