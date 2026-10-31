import { Product } from '@/types';

const OFFICIAL_PRODUCT_IMAGES: Record<string, string> = {
    'camisa dn ciclismo vermelho/amarelo': '/assets/Images/products/camisa-dn-ciclismo-vermelho-amarelo.jpeg',
    'camiseta dn poliamida azul/branco': '/assets/Images/products/camiseta-dn-poliamida-azul-branco.jpeg',
    'camisa dn ciclismo azul/branco': '/assets/Images/products/camisa-dn-ciclismo-azul-branco.jpeg',
    'camisa dn ciclismo bordô/amarelo': '/assets/Images/products/Camisa-DN-Ciclismo-Bordo-Amarelo.jpeg',
    'camiseta dn poliamida preto/cinza-claro': '/assets/Images/products/Camiseta-DN-Poliamida-Preto-Cinza-claro.jpeg',
    'camiseta dn poliamida branco/preto': '/assets/Images/products/Camiseta-DN-Poliamida-Branco-Preto.jpeg',
    'camiseta dn poliamida preto/verde-limão': '/assets/Images/products/Camiseta-DN-Poliamida-Preto-Verde-limão.jpeg',
    'camiseta dn poliamida azul/verde-limão': '/assets/Images/products/Camiseta-DN-Poliamida-Azul-Verde-limão.jpeg',
    'camiseta dn poliamida vermelho/cinza-prata': '/assets/Images/products/Camiseta-DN-Poliamida-Vermelho-Cinza-prata.jpeg',
    'camiseta dn poliamida cinza/verde-limão': '/assets/Images/products/camiseta-dn-poliamida-cinza-verde-limao.jpeg',
    'camiseta dn poliamida preto/laranja': '/assets/Images/products/Camiseta-DN-Poliamida-Preto-Laranja.jpeg',
    'camiseta dn poliamida preto/branco/roxo': '/assets/Images/products/camiseta-dn-poliamida-preto-branco-roxo.jpeg',
    'camiseta dn poliamida branco/vermelho': '/assets/Images/products/camiseta-dn-poliamida-branco-vermelho.jpeg',
    'camiseta dn poliamida cinza/branco': '/assets/Images/products/Camiseta-DN-Poliamida-Cinza-Branco.jpeg',
    'camisa dn ciclismo marrom/rosa-claro': '/assets/Images/products/camisa-dn-ciclismo-marrom-rosa-claro.jpeg',
    'camiseta dn poliamida vinho/branco': '/assets/Images/products/camiseta-dn-poliamida-vinho-branco.jpeg',
    'camisa dn ciclismo grafite/branco': '/assets/Images/products/Camisa-DN-Ciclismo-Grafite-Branco.jpeg',
    'camiseta dn poliamida laranja/azul': '/assets/Images/products/Camiseta-DN-Poliamida-Laranja-Azul.jpeg',
    'boné dn verde/preto': '/assets/Images/products/bone-dn-verde-preto.jpeg',
    'boné dn preto/vermelho': '/assets/Images/products/Bone-DN-Preto-Vermelho.jpeg',
};

function normalizeProductName(name: string) {
    return name.trim().toLocaleLowerCase('pt-BR').normalize('NFC');
}

export function getProductImageUrl(name: string) {
    return OFFICIAL_PRODUCT_IMAGES[normalizeProductName(name)];
}

export function getProductImage(product: Pick<Product, 'name'>) {
    return getProductImageUrl(product.name);
}

export function getProductGalleryImages(product: Pick<Product, 'name'>) {
    const mainImage = getProductImage(product);
    return mainImage ? [mainImage] : [];
}
