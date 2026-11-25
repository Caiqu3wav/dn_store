// Os valores são os status reais do backend (OrderStatus).
export const ORDER_STATUS_LABELS: Record<string, string> = {
    PENDING_PAYMENT: 'Aguardando pagamento',
    PAID: 'Pago',
    PAYMENT_FAILED: 'Pagamento falhou',
    PAYMENT_REVIEW: 'Revisar pagamento',
    PROCESSING: 'Em separação',
    SHIPPED: 'Enviado',
    DELIVERED: 'Entregue',
    CANCELLED: 'Cancelado',
    EXPIRED: 'Expirado',
};

export const orderStatusLabel = (status: string) => ORDER_STATUS_LABELS[status] ?? status;

export const orderStatusStyle = (status: string) => {
    switch (status) {
        case 'PENDING_PAYMENT':
        case 'PAYMENT_REVIEW':
            return 'bg-yellow-500/10 text-yellow-500 border-yellow-500/20';
        case 'PAID':
        case 'PROCESSING':
            return 'bg-blue-500/10 text-blue-400 border-blue-500/20';
        case 'SHIPPED':
            return 'bg-purple-500/10 text-purple-400 border-purple-500/20';
        case 'DELIVERED':
            return 'bg-green-500/10 text-green-500 border-green-500/20';
        default:
            return 'bg-red-500/10 text-red-500 border-red-500/20';
    }
};

// Pedidos em que o pagamento nunca foi concluído (carrinho que chegou ao "Confirmar pagamento" e parou,
// PIX não pago, expirado). Ficam no banco para reserva/liberação de estoque e conciliação, mas não são
// vendas, então o painel admin os esconde por padrão.
export const UNPAID_ORDER_STATUSES = ['PENDING_PAYMENT', 'PAYMENT_FAILED', 'EXPIRED'];

export const isUnpaidOrder = (status: string) => UNPAID_ORDER_STATUSES.includes(status);
