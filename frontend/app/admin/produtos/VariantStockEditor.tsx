'use client';

import { PRODUCT_VARIANT_SIZES, ProductVariant } from '@/types';
import { Button } from '../_components/ui';
import { Checkbox, Field, Input } from '../_components/form';

export type VariantDraft = Pick<ProductVariant, 'size' | 'stock' | 'active'> & { id?: string };

interface Props {
    variants: VariantDraft[];
    onChange: (variants: VariantDraft[]) => void;
}

export function VariantStockEditor({ variants, onChange }: Props) {
    const availableSizes = PRODUCT_VARIANT_SIZES.filter(size => !variants.some(variant => variant.size === size));

    return (
        <section className="space-y-4 rounded-adm-card border border-adm-line bg-adm-surface p-4 shadow-adm-card sm:p-5">
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-adm-line pb-3">
                <div>
                    <h2 className="text-sm font-semibold uppercase tracking-wide text-adm-ink-muted">Tamanhos e estoque</h2>
                    <p className="mt-1 text-xs text-adm-ink-subtle">Baby Look possui estoque independente. Novas variações começam com estoque zero.</p>
                </div>
                <Button
                    size="sm"
                    disabled={availableSizes.length === 0}
                    onClick={() => onChange([...variants, { size: availableSizes[0], stock: 0, active: true }])}
                >
                    Adicionar tamanho
                </Button>
            </div>
            {variants.length === 0 ? (
                <p className="rounded-adm-control border border-adm-warning/30 bg-adm-warning/10 p-3 text-sm text-adm-warning">Adicione ao menos um tamanho antes de salvar o produto.</p>
            ) : (
                <div className="space-y-2">
                    {variants.map((variant, index) => (
                        <div key={variant.id ?? variant.size} className="grid grid-cols-[4rem_1fr_auto] items-end gap-3">
                            <span className="pb-2.5 text-sm font-semibold text-adm-ink">{variant.size}</span>
                            <Field label="Estoque">
                                <Input
                                    type="number"
                                    min="0"
                                    step="1"
                                    required
                                    aria-label={`Estoque do tamanho ${variant.size}`}
                                    value={variant.stock}
                                    onChange={event => {
                                        const stock = Number(event.target.value);
                                        onChange(variants.map((current, currentIndex) =>
                                            currentIndex === index ? { ...current, stock } : current));
                                    }}
                                />
                            </Field>
                            <Checkbox
                                className="pb-1"
                                checked={variant.active}
                                onChange={event => onChange(variants.map((current, currentIndex) =>
                                    currentIndex === index ? { ...current, active: event.target.checked } : current))}
                            >
                                Ativa
                            </Checkbox>
                        </div>
                    ))}
                </div>
            )}
        </section>
    );
}
