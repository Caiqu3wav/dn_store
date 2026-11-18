import assert from 'node:assert/strict';
import { test } from 'node:test';
import { AxiosError, type AxiosResponse } from 'axios';
import {
    getApiErrorMessage,
    NETWORK_ERROR_MESSAGE,
    TIMEOUT_ERROR_MESSAGE,
} from './apiError.ts';

const httpError = (status: number, data: unknown) =>
    new AxiosError('request failed', undefined, undefined, undefined, {
        status,
        data,
    } as AxiosResponse);

test('erro sem resposta é tratado como falha de rede', () => {
    assert.equal(getApiErrorMessage(new AxiosError('Network Error')), NETWORK_ERROR_MESSAGE);
});

test('timeout tem mensagem própria', () => {
    assert.equal(getApiErrorMessage(new AxiosError('timeout', 'ECONNABORTED')), TIMEOUT_ERROR_MESSAGE);
});

test('mensagem genérica em inglês do backend é traduzida por status', () => {
    const message = getApiErrorMessage(httpError(401, { message: 'Authentication required or credentials are invalid.' }));
    assert.match(message, /sessão expirou|credenciais/i);
    assert.doesNotMatch(message, /Authentication/);
});

test('mensagem de regra de negócio em português é preservada', () => {
    assert.equal(
        getApiErrorMessage(httpError(409, { message: 'Estoque insuficiente para o tamanho selecionado.' })),
        'Estoque insuficiente para o tamanho selecionado.',
    );
    assert.equal(getApiErrorMessage(httpError(400, { message: 'Cupom inválido ou expirado.' })), 'Cupom inválido ou expirado.');
});

test('override por status tem prioridade', () => {
    assert.equal(
        getApiErrorMessage(httpError(400, { message: 'Request could not be processed.' }), undefined, { 400: 'Código inválido.' }),
        'Código inválido.',
    );
});

test('429 nunca expõe a mensagem do backend', () => {
    const message = getApiErrorMessage(httpError(429, { message: 'A new code can be requested in 60 seconds.' }));
    assert.match(message, /Muitas tentativas/);
});

test('erros 5xx nunca expõem mensagem técnica', () => {
    const message = getApiErrorMessage(httpError(500, { message: 'NullPointerException at Foo.bar' }));
    assert.doesNotMatch(message, /NullPointer/);
    assert.match(message, /erro inesperado/i);
});

test('erro que não é de requisição usa o fallback', () => {
    assert.equal(getApiErrorMessage(new Error('boom'), 'Falha ao salvar.'), 'Falha ao salvar.');
});
