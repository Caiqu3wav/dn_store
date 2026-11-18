import axios from 'axios';

// Mensagens genéricas em inglês que o backend devolve por status (GlobalExceptionHandler,
// ApiSecurityExceptionHandler). Mensagens de regra de negócio (ex.: "Email já cadastrado.")
// já vêm em português e são exibidas como estão.
const GENERIC_BACKEND_MESSAGES = new Set([
    'Request could not be processed.',
    'Invalid request.',
    'Invalid request parameters or body.',
    'Validation failed.',
    'Resource not found.',
    'Too many requests. Try again later.',
    'External service communication failed.',
    'Service temporarily unavailable.',
    'An unexpected error occurred.',
    'Authentication required or credentials are invalid.',
    'Access denied.',
    'Payment provider could not process the request.',
    'Shipping quote is temporarily unavailable.',
    'The request conflicts with existing data.',
    'HTTP method not supported for this resource.',
]);

const STATUS_MESSAGES: Record<number, string> = {
    400: 'Não foi possível processar a solicitação. Revise os dados informados e tente novamente.',
    401: 'Sua sessão expirou ou as credenciais são inválidas. Entre novamente.',
    403: 'Você não tem permissão para realizar esta ação.',
    404: 'Não encontramos o que você procurou.',
    405: 'Esta ação não está disponível.',
    409: 'Esta ação conflita com os dados atuais. Atualize a página e tente novamente.',
    422: 'Os dados informados não puderam ser processados. Revise e tente novamente.',
    429: 'Muitas tentativas em pouco tempo. Aguarde um instante e tente novamente.',
    500: 'Ocorreu um erro inesperado em nosso servidor. Tente novamente em instantes.',
    502: 'Um serviço externo não respondeu corretamente. Tente novamente em instantes.',
    503: 'O serviço está temporariamente indisponível. Tente novamente em instantes.',
    504: 'O servidor demorou para responder. Tente novamente em instantes.',
};

export const NETWORK_ERROR_MESSAGE =
    'Não foi possível conectar ao servidor. Verifique sua conexão com a internet e tente novamente.';
export const TIMEOUT_ERROR_MESSAGE = 'O servidor demorou para responder. Tente novamente em instantes.';

export type ApiErrorOverrides = Partial<Record<number, string>>;

export function getApiErrorStatus(error: unknown): number | undefined {
    return axios.isAxiosError(error) ? error.response?.status : undefined;
}

export function isNetworkError(error: unknown): boolean {
    return axios.isAxiosError(error) && !error.response;
}

/** Código estável de negócio enviado pelo backend em `errors.code` (ex.: SHIPPING_QUOTE_STALE). */
export function getApiErrorCode(error: unknown): string | undefined {
    if (!axios.isAxiosError(error)) return undefined;
    const data = error.response?.data as { errors?: { code?: unknown } } | undefined;
    const code = data?.errors?.code;
    return typeof code === 'string' ? code : undefined;
}

function backendMessage(error: unknown): string | undefined {
    if (!axios.isAxiosError(error)) return undefined;
    const data = error.response?.data as { message?: unknown } | string | undefined;
    const message = typeof data === 'string' ? data : data?.message;
    return typeof message === 'string' && message.trim() ? message.trim() : undefined;
}

/**
 * Converte qualquer erro de requisição em uma mensagem em português para o usuário, sem expor
 * detalhes técnicos. `overrides` permite mensagens específicas por status HTTP em um fluxo
 * (ex.: 400 em "verificar código" = código inválido ou expirado).
 */
export function getApiErrorMessage(
    error: unknown,
    fallback?: string,
    overrides: ApiErrorOverrides = {},
): string {
    if (!axios.isAxiosError(error)) {
        return fallback ?? STATUS_MESSAGES[500];
    }

    if (!error.response) {
        return error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
            ? TIMEOUT_ERROR_MESSAGE
            : NETWORK_ERROR_MESSAGE;
    }

    const status = error.response.status;
    const override = overrides[status];
    if (override) return override;

    const message = backendMessage(error);
    const isBusinessMessage = status >= 400 && status < 500 && status !== 401 && status !== 403
        && status !== 429 && message && !GENERIC_BACKEND_MESSAGES.has(message);
    if (isBusinessMessage) return message;

    return STATUS_MESSAGES[status] ?? fallback ?? STATUS_MESSAGES[500];
}
