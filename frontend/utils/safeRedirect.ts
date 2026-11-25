// Aceita apenas caminhos internos ("/conta"). Valores como "//site.com", "https://site.com" ou
// "/\site.com" são descartados para evitar redirecionamento para sites externos após o login.
export function safeNextPath(value: string | null | undefined, fallback = '/'): string {
    if (!value || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
        return fallback;
    }
    return value;
}
