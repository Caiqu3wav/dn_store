import api from '@/lib/axios';
import type { ShippingOption } from './components/StepDelivery';

export interface QuoteCartItem {
    id: string;
    productId?: string;
    productVariantId?: string;
    size?: string;
    quantity: number;
}

/**
 * Sincroniza o carrinho com o backend e pede as opções de frete para o CEP. O backend grava a
 * cotação e é ele quem valida preço, CEP e carrinho no checkout; o frontend só envia o id da opção.
 */
export async function requestShippingQuotes(items: QuoteCartItem[], cep: string): Promise<ShippingOption[]> {
    await api.put('/cart/sync', items.map(item => ({
        productId: item.productId ?? item.id,
        productVariantId: item.productVariantId,
        size: item.size,
        quantity: item.quantity,
    })));
    const response = await api.post<ShippingOption[]>('/orders/shipping-quotes', { zipCode: cep });
    return response.data;
}
