const aliases: Record<string, string> = {
    preto: 'Preto', preta: 'Preto', black: 'Preto',
    branco: 'Branco', branca: 'Branco', white: 'Branco',
    amarelo: 'Amarelo', amarela: 'Amarelo', yellow: 'Amarelo',
    vermelho: 'Vermelho', vermelha: 'Vermelho', red: 'Vermelho',
    azul: 'Azul', blue: 'Azul',
    verde: 'Verde', green: 'Verde',
    cinza: 'Cinza', cinzento: 'Cinza', cinzenta: 'Cinza', gray: 'Cinza', grey: 'Cinza',
    laranja: 'Laranja', orange: 'Laranja',
    rosa: 'Rosa', pink: 'Rosa',
    marrom: 'Marrom', brown: 'Marrom',
    roxo: 'Roxo', roxa: 'Roxo', purple: 'Roxo', violeta: 'Roxo', violet: 'Roxo',
    bege: 'Bege', beige: 'Bege',
    dourado: 'Dourado', dourada: 'Dourado', gold: 'Dourado',
    prata: 'Prata', prateado: 'Prata', prateada: 'Prata', silver: 'Prata',
    'azul marinho': 'Azul-marinho', marinho: 'Azul-marinho', navy: 'Azul-marinho', 'navy blue': 'Azul-marinho',
    vinho: 'Vinho', burgundy: 'Vinho',
    transparente: 'Transparente', transparent: 'Transparente',
    multicolorido: 'Multicolorido', multicolorida: 'Multicolorido', multicolor: 'Multicolorido',
    turquesa: 'Turquesa', turquoise: 'Turquesa',
    ciano: 'Ciano', cyan: 'Ciano',
    magenta: 'Magenta',
    lilas: 'Lilás', lilases: 'Lilás', lavender: 'Lilás', lilac: 'Lilás',
    salmao: 'Salmão', salmon: 'Salmão',
    coral: 'Coral',
    caramelo: 'Caramelo', caramel: 'Caramelo',
    creme: 'Creme', cream: 'Creme',
    caqui: 'Cáqui', khaki: 'Cáqui',
    cobre: 'Cobre', copper: 'Cobre',
    bronze: 'Bronze',
    grafite: 'Grafite', graphite: 'Grafite',
    chumbo: 'Chumbo',
    'verde oliva': 'Verde-oliva', oliva: 'Verde-oliva', olive: 'Verde-oliva',
    'verde petroleo': 'Verde-petróleo', petroleo: 'Verde-petróleo', teal: 'Verde-petróleo',
    indigo: 'Índigo',
    mostarda: 'Mostarda', mustard: 'Mostarda',
    'azul claro': 'Azul-claro', 'light blue': 'Azul-claro',
    'azul escuro': 'Azul-escuro', 'dark blue': 'Azul-escuro',
    'verde claro': 'Verde-claro', 'light green': 'Verde-claro',
    'verde escuro': 'Verde-escuro', 'dark green': 'Verde-escuro'
};

export const productColorOptions = [
    'Preto', 'Branco', 'Amarelo', 'Vermelho', 'Azul', 'Azul-marinho', 'Verde',
    'Laranja', 'Rosa', 'Marrom', 'Cinza', 'Roxo', 'Bege', 'Dourado', 'Prata',
    'Vinho', 'Transparente', 'Multicolorido', 'Turquesa', 'Ciano', 'Magenta', 'Lilás',
    'Salmão', 'Coral', 'Caramelo', 'Creme', 'Cáqui', 'Cobre', 'Bronze', 'Grafite',
    'Chumbo', 'Verde-oliva', 'Verde-petróleo', 'Índigo', 'Mostarda', 'Azul-claro',
    'Azul-escuro', 'Verde-claro', 'Verde-escuro', 'Preto e Vermelho', 'Preto e Verde'
];

const colorOrder = productColorOptions.filter(option => !option.includes(' e '));

const normalizeToken = (value: string) => value
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('pt-BR')
    .replace(/[-_]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();

export function normalizeProductColor(value: string | null | undefined): string | null {
    const input = value?.trim();
    if (!input) return '';

    const parts = input.split(/\s+(?:e|and)\s+|\s*[&+/;,]\s*/i);
    const normalizedParts: string[] = [];
    for (const part of parts) {
        const color = aliases[normalizeToken(part)];
        if (!color) return null;
        if (!normalizedParts.includes(color)) normalizedParts.push(color);
    }
    return normalizedParts.sort((left, right) => colorOrder.indexOf(left) - colorOrder.indexOf(right)).join(' e ');
}