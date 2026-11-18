import assert from 'node:assert/strict';
import { test } from 'node:test';
import { safeNextPath } from './safeRedirect.ts';

test('aceita caminhos internos', () => {
    assert.equal(safeNextPath('/conta'), '/conta');
    assert.equal(safeNextPath('/produtos/123?x=1'), '/produtos/123?x=1');
});

test('rejeita destinos externos ou ambíguos', () => {
    for (const value of ['https://evil.example', '//evil.example', '/\\evil.example', 'javascript:alert(1)', 'conta', '', null, undefined]) {
        assert.equal(safeNextPath(value), '/', String(value));
    }
});

test('usa o fallback informado', () => {
    assert.equal(safeNextPath('//x', '/admin'), '/admin');
});
