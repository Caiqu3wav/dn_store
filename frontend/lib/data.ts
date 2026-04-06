export const CATEGORIES = [
  { id: '1', name: 'T-Shirts', image: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&q=80&w=500', slug: 't-shirts' },
  { id: '2', name: 'Camisas de Ciclismo', image: 'https://images.unsplash.com/photo-1534723452862-4c874018d66d?auto=format&fit=crop&q=80&w=500', slug: 'camisas-ciclismo' },
  { id: '3', name: 'Bonés & Meias', image: 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?auto=format&fit=crop&q=80&w=500', slug: 'acessorios' },
];

export const FEATURED_PRODUCTS = [
  { id: '1', name: 'Camiseta DN Classic', price: 89.90, image: '/assets/products/CaDN.jpg', category: 'T-Shirts' },
  { id: '2', name: 'Jersey Aero Pro', price: 249.90, image: '/assets/products/CDNBlue.jpg', category: 'Camisas de Ciclismo', tag: 'Novo' },
  { id: '3', name: 'Camisa Manga Longa MTB', price: 199.90, image: '/assets/products/CDNRed.jpg', category: 'Camisas de Ciclismo' },
  { id: '4', name: 'Boné Trail Explorer', price: 69.90, image: '/assets/products/BoneTeste.webp', category: 'Acessórios' },
  { id: '5', name: 'Meia Performance Verde', price: 45.00, image: '/assets/products/TDNBlack.jpg', category: 'Acessórios' },
  { id: '6', name: 'Tech Tee Respirável', price: 119.90, image: '/assets/products/CamisaTeste.webp', category: 'T-Shirts' },
  { id: '7', name: 'Tênis Performance', price: 299.90, image: '/assets/products/TenisTeste.webp', category: 'Calçados' },
];

export const EVENTS = [
  {
    id: '1',
    name: 'Pedal na Serra da Mantiqueira',
    date: '12 de Abril',
    location: 'Campos do Jordão',
    description: 'Uma jornada desafiadora com subidas técnicas e descidas rápidas nas montanhas.',
    mapsLink: 'https://maps.google.com?q=Campos+do+Jordao',
    image: 'https://images.unsplash.com/photo-1604677657548-4ced0c4f40c6?auto=format&fit=crop&w=800&q=80'
  },
  {
    id: '2',
    name: 'Trilha MTB Parque da Cidade',
    date: '20 de Abril',
    location: 'São José dos Campos',
    description: 'Percurso urbano e trilhas leves, ideal para ciclistas de todos os níveis.',
    mapsLink: 'https://maps.google.com?q=São+José+dos+Campos',
    image: 'https://images.unsplash.com/photo-1524661135-423995f22d0b?auto=format&fit=crop&q=80&w=800'
  },
  {
    id: '3',
    name: 'Desafio MTB Pico do Itapeva',
    date: '04 de Maio',
    location: 'Pindamonhangaba',
    description: 'Trecho desafiante com subida íngreme e paisagem panorâmica do Pico do Itapeva.',
    mapsLink: 'https://maps.google.com?q=Pindamonhangaba',
    image: 'https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&q=80&w=800'
  },
  {
    id: '4',
    name: 'Pedal Estrada Velha',
    date: '18 de Maio',
    location: 'Taubaté',
    description: 'Passeio clássico pela Estrada Velha com paradas para fotos e troca de experiência.',
    mapsLink: 'https://maps.google.com?q=Taubate',
    image: 'https://images.unsplash.com/photo-1508609349937-5ec4ae374ebf?auto=format&fit=crop&q=80&w=800'
  }
];

export const NEXT_EVENT = EVENTS[0];
