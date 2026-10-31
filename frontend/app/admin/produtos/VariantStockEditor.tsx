'use client';

import { PRODUCT_VARIANT_SIZES, ProductVariant } from '@/types';

export type VariantDraft = Pick<ProductVariant, 'size' | 'stock' | 'active'> & { id?: string };

interface Props {
    variants: VariantDraft[];
    onChange: (variants: VariantDraft[]) => void;
}

export function VariantStockEditor({ variants, onChange }: Props) {
    const availableSizes = PRODUCT_VARIANT_SIZES.filter(size => !variants.some(variant => variant.size === size));

    return (
        <section className="space-y-3 rounded-lg border border-white/10 bg-[#222325] p-4">
            <div className="flex items-center justify-between gap-3">
                <div>
                    <h2 className="text-sm font-semibold uppercase tracking-wide text-gray-300">Tamanhos e estoque</h2>
                    <p className="mt-1 text-xs text-gray-400">Baby Look possui estoque independente. Novas variações começam com estoque zero.</p>
                </div>
                <button
                    type="button"
                    disabled={availableSizes.length === 0}
                    onClick={() => onChange([...variants, { size: availableSizes[0], stock: 0, active: true }])}
                    className="rounded-lg border border-white/10 px-3 py-2 text-sm text-white disabled:cursor-not-allowed disabled:opacity-40"
                >
                    Adicionar tamanho
                </button>
            </div>
            {variants.length === 0 ? (
                <p className="text-sm text-amber-300">Adicione ao menos um tamanho antes de salvar o produto.</p>
            ) : (
                <div className="space-y-2">
                    {variants.map((variant, index) => (
                        <div key={variant.id ?? variant.size} className="grid grid-cols-[1fr_1fr_auto] items-center gap-3">
                            <span className="text-sm font-medium text-white">{variant.size}</span>
                            <label className="space-y-1 text-xs text-gray-400">
                                Estoque
                                <input
                                    type="number"
                                    min="0"
                                    step="1"
                                    required
                                    value={variant.stock}
                                    onChange={event => {
                                        const stock = Number(event.target.value);
                                        onChange(variants.map((current, currentIndex) =>
                                            currentIndex === index ? { ...current, stock } : current));
                                    }}
                                    className="min-h-10 w-full rounded-lg border border-white/10 bg-[#1A1B1D] px-3 text-sm text-white"
                                />
                            </label>
                            <label className="flex items-center gap-2 text-xs text-gray-400">
                                <input
                                    type="checkbox"
                                    checked={variant.active}
                                    onChange={event => onChange(variants.map((current, currentIndex) =>
                                        currentIndex === index ? { ...current, active: event.target.checked } : current))}
                                />
                                Ativa
                            </label>
                        </div>
                    ))}
                </div>
            )}
        </section>
    );
}
