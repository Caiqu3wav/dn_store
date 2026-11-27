package com.dnstore.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dnstore.backend.dto.StockDtos.EntryDetail;
import com.dnstore.backend.dto.StockDtos.EntrySummary;
import com.dnstore.backend.dto.StockDtos.MovementView;
import com.dnstore.backend.dto.StockDtos.PageView;
import com.dnstore.backend.exception.GlobalExceptionHandler;
import com.dnstore.backend.exception.StockConflictException;
import com.dnstore.backend.model.StockMovement;
import com.dnstore.backend.model.User;
import com.dnstore.backend.model.enums.Role;
import com.dnstore.backend.service.StockEntryService;
import com.dnstore.backend.service.StockQueryService;
import com.dnstore.backend.service.StockReconciliationService;
import com.dnstore.backend.service.StockService;
import com.dnstore.backend.service.SupplierService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StockControllerTest {

    private final StockService stockService = mock(StockService.class);
    private final StockEntryService entryService = mock(StockEntryService.class);
    private final StockQueryService queryService = mock(StockQueryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        User admin = new User();
        admin.setId(UUID.randomUUID());
        admin.setEmail("admin@dnstore.test");
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
        mockMvc = MockMvcBuilders.standaloneSetup(new StockController(stockService, entryService, queryService,
                        mock(StockReconciliationService.class), mock(SupplierService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static String noteBody(String lines) {
        return """
                {"supplierId":"%s","invoiceNumber":"1234","invoiceDate":"%s","idempotencyKey":"key-1","lines":%s}
                """.formatted(UUID.randomUUID(), LocalDate.now(), lines);
    }

    private static EntryDetail detail(boolean replayed) {
        EntrySummary summary = new EntrySummary(UUID.randomUUID(), UUID.randomUUID(), "Fábrica", "1234", "",
                LocalDate.now(), 1, 5, new BigDecimal("50.00"), "admin@dnstore.test", LocalDateTime.now());
        return new EntryDetail(summary, null, List.of(), List.of(), replayed);
    }

    @Test
    void createNoteReturns201ThenReplayReturns200() throws Exception {
        String body = noteBody("[{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":5,\"unitCost\":10.00}]");
        when(entryService.create(any())).thenReturn(detail(false), detail(true));

        mockMvc.perform(post("/api/admin/stock/entries").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/admin/stock/entries").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.replayed").value(true));

        ArgumentCaptor<StockEntryService.CreateCommand> captor = ArgumentCaptor.forClass(StockEntryService.CreateCommand.class);
        verify(entryService, org.mockito.Mockito.times(2)).create(captor.capture());
        assertThat(captor.getValue().performedBy()).isEqualTo("admin@dnstore.test");
        assertThat(captor.getValue().lines()).singleElement().satisfies(l -> assertThat(l.quantity()).isEqualTo(5));
    }

    @Test
    void duplicateNoteIs409WithTheExistingNoteId() throws Exception {
        UUID existing = UUID.randomUUID();
        when(entryService.create(any())).thenThrow(new StockConflictException("Esta nota já foi registrada para o fornecedor.",
                Map.of("existingEntryId", existing.toString())));

        mockMvc.perform(post("/api/admin/stock/entries").contentType(MediaType.APPLICATION_JSON)
                        .content(noteBody("[{\"variantId\":\"" + UUID.randomUUID() + "\",\"quantity\":1}]")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.existingEntryId").value(existing.toString()));
    }

    @Test
    void noteWithoutLinesOrKeyIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(post("/api/admin/stock/entries").contentType(MediaType.APPLICATION_JSON).content(noteBody("[]")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/stock/entries").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"" + UUID.randomUUID() + "\",\"invoiceNumber\":\"1\","
                                + "\"invoiceDate\":\"" + LocalDate.now() + "\",\"lines\":[{\"variantId\":\""
                                + UUID.randomUUID() + "\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(entryService);
    }

    @Test
    void legacyAdjustBodyStillWorksAndNewFieldsAreOptional() throws Exception {
        UUID variantId = UUID.randomUUID();
        MovementView view = new MovementView(UUID.randomUUID(), 1L, variantId, UUID.randomUUID(), "Camiseta", "M", "Preto", -2,
                10, 8, "ADJUSTMENT", "OTHER", null, null, null, null, "Avaria", "admin@dnstore.test",
                LocalDateTime.now());
        when(stockService.adjust(any())).thenReturn(new StockService.AdjustResult(view, false));

        mockMvc.perform(put("/api/admin/stock/adjust").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"delta\":-2,\"reason\":\"Avaria\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockBefore").value(10))
                .andExpect(jsonPath("$.stockAfter").value(8))
                .andExpect(jsonPath("$.variantSize").value("M"));

        ArgumentCaptor<StockService.AdjustCommand> captor = ArgumentCaptor.forClass(StockService.AdjustCommand.class);
        verify(stockService).adjust(captor.capture());
        assertThat(captor.getValue().delta()).isEqualTo(-2);
        assertThat(captor.getValue().countedQuantity()).isNull();
        assertThat(captor.getValue().expectedBalance()).isNull();
        assertThat(captor.getValue().performedBy()).isEqualTo("admin@dnstore.test");
    }

    @Test
    void adjustWithoutReasonIs400AndStaleBalanceIs409WithTheCurrentBalance() throws Exception {
        UUID variantId = UUID.randomUUID();
        mockMvc.perform(put("/api/admin/stock/adjust").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"delta\":-2,\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());

        when(stockService.adjust(any())).thenThrow(new StockConflictException("O saldo mudou desde que você abriu a tela.",
                Map.of("currentBalance", "7", "expectedBalance", "10")));
        mockMvc.perform(put("/api/admin/stock/adjust").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"variantId\":\"" + variantId + "\",\"countedQuantity\":3,\"expectedBalance\":10,"
                                + "\"category\":\"COUNT\",\"reason\":\"Contagem\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.currentBalance").value("7"));
    }

    @Test
    void movementFiltersAreForwardedAndUnknownTypeIs400() throws Exception {
        when(queryService.movements(any(), eq(2), eq(25))).thenReturn(PageView.of(List.of(), 2, 25, 0));
        UUID variantId = UUID.randomUUID();

        mockMvc.perform(get("/api/admin/stock/movements").param("variantId", variantId.toString())
                        .param("type", "SALE", "ENTRY").param("from", "2026-11-01").param("page", "2").param("size", "25"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/admin/stock/movements").param("type", "BOGUS")).andExpect(status().isBadRequest());

        ArgumentCaptor<StockQueryService.MovementFilter> captor = ArgumentCaptor.forClass(StockQueryService.MovementFilter.class);
        verify(queryService).movements(captor.capture(), eq(2), eq(25));
        assertThat(captor.getValue().variantId()).isEqualTo(variantId);
        assertThat(captor.getValue().types()).containsExactlyInAnyOrder(StockMovement.Type.SALE, StockMovement.Type.ENTRY);
        assertThat(captor.getValue().from()).isEqualTo(LocalDate.of(2026, 11, 1));
    }
}
