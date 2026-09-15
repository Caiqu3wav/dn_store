package com.dnstore.backend.config;

import com.dnstore.backend.model.Category;
import com.dnstore.backend.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseSeeder implements CommandLineRunner {

    private final CategoryRepository categoryRepository;

    @Override
    public void run(String... args) throws Exception {
        if (categoryRepository.count() == 0) {
            log.info("Sem categorias encontradas no banco de dados. Semeando dados padrão...");

            List<Category> defaultCategories = Arrays.asList(
                Category.builder().name("Camisa Poliamida").slug("camisa-poliamida").description("Camisetas casuais e esportivas 100% poliamida.").build(),
                Category.builder().name("Camisas de Ciclismo").slug("camisas-de-ciclismo").description("Camisas profissionais e semi-profissionais para ciclismo.").build(),
                Category.builder().name("Bonés & Meias").slug("bones-e-meias").description("Bonés de aba curva, aba reta e meias de ciclismo/corrida.").build(),
                Category.builder().name("Vestuário").slug("vestuario").description("Vestuário esportivo e casual em geral.").build(),
                Category.builder().name("Acessórios").slug("acessorios").description("Mochilas, garrafas e outros acessórios esportivos.").build(),
                Category.builder().name("Equipamentos").slug("equipamentos").description("Equipamentos esportivos de alto desempenho.").build()
            );

            categoryRepository.saveAll(defaultCategories);
            log.info("Categorias padrão semeadas com sucesso!");
        }
    }
}
