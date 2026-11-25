import { orderStatusLabel } from '@/utils/orderStatus';
import { Badge, type BadgeTone } from './ui';

// Mesmo agrupamento de cores de orderStatusStyle (utils/orderStatus.ts), mas com os tokens do admin.
const statusTone = (status: string): BadgeTone => {
    switch (status) {
        case 'PENDING_PAYMENT':
        case 'PAYMENT_REVIEW':
            return 'warning';
        case 'PAID':
        case 'PROCESSING':
            return 'info';
        case 'SHIPPED':
            return 'violet';
        case 'DELIVERED':
            return 'success';
        default:
            return 'danger';
    }
};

export function OrderStatusBadge({ status, className }: { status: string; className?: string }) {
    return <Badge tone={statusTone(status)} className={className}>{orderStatusLabel(status)}</Badge>;
}
