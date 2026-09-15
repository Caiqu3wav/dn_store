// Mapeamento temporário entre nomes no frontend e IDs/nomes no backend
// Ajuste os IDs conforme existirem no seu banco de dados
export const CATEGORY_MAP: Record<string, number> = {
  'Camisa Poliamida': 1,
  'Camisas de Ciclismo': 2,
  'Bonés & Meias': 3,
  'Vestuário': 4,
  'Acessórios': 5,
  'Equipamentos': 6,
};

export const getCategoryId = (categoryName: string): number | undefined => {
  return CATEGORY_MAP[categoryName];
};
