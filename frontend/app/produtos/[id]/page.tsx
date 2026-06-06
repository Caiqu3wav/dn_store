'use client';

import { useState, use } from 'react';
import { Button } from '../../components/ui/Button';
import { useCart } from '../../context/CartContext';
import { ShoppingCart, Heart, Share2, ChevronRight } from 'lucide-react';
import Link from 'next/link';

// Mock data (would come from API/DB)
const products = {
    '1': {
        id: '1',
        name: 'Camiseta DN Poliamida Cinza',
        price: 59.99,
        description: 'A Camiseta DN Poliamida Cinza combina conforto, leveza e desempenho para acompanhar você em qualquer desafio. Produzida em tecido respirável de secagem rápida, oferece excelente mobilidade e conforto durante treinos, pedaladas ou no dia a dia, com um visual moderno e versátil que combina com qualquer ocasião.',
        images: [
            '/assets/products/CaPoliCinza.jpeg',
            'https://images.unsplash.com/photo-1503342217505-b0a15ec3261c?q=80&w=2070&auto=format&fit=crop'
        ],
        sizes: ['P', 'M', 'G', 'GG'],
        category: 'Camisetas Poliamida'
    },
    '2': {
        id: '2',
        name: 'Camisa DN Ciclismo Laranja/Azul',
        price: 199.99,
        description: 'A Camisa DN Ciclismo Laranja/Azul foi desenvolvida para ciclistas que buscam performance e conforto em cada percurso. Confeccionada com tecido leve e respirável, oferece excelente ventilação e secagem rápida, enquanto seu design vibrante em laranja e azul garante estilo e visibilidade nas estradas e trilhas. Ideal para encarar qualquer desafio sobre duas rodas.',
        images: [
            '/assets/products/CaCicliLaranja.jpeg'
        ],
        sizes: ['P', 'M', 'G', 'GG'],
        category: 'Camisas de Ciclismo'
    },
    '3': {
        id: '3',
        name: 'Boné DN Preto/Vermelho',
        price: 59.99,
        description: 'O Boné DN Preto/Vermelho combina estilo, conforto e praticidade para o dia a dia. Com design moderno e ajuste confortável, é ideal para proteger do sol durante treinos, passeios ou momentos de lazer, trazendo a identidade da DN Store em uma combinação de cores marcante e versátil.',
        images: [
            '/assets/products/BoneDNPreto.jpeg'
        ],
        sizes: ['Único'],
        category: 'Bonés e Meias',
    },
    // Fallback for other IDs
    'default': {
        id: '0',
        name: 'Produto Exemplo',
        price: 99.90,
        description: 'Descrição do produto exemplo.',
        images: ['https://images.unsplash.com/photo-1517649763962-0c623066013b?q=80&w=2070&auto=format&fit=crop'],
        sizes: ['Único'],
        category: 'Geral'
    }
};

export default function ProductPage({ params }: { params: Promise<{ id: string }> }) {
    const { id } = use(params);
    const product = products[id as keyof typeof products] || products['default'];

    const [selectedSize, setSelectedSize] = useState(product.sizes[0]);
    const [currentImage, setCurrentImage] = useState(0);
    const { addItem } = useCart();

    const handleAddToCart = () => {
        addItem({
            id: product.id,
            name: product.name,
            price: product.price,
            image: product.images[0],
            quantity: 1,
            size: selectedSize
        });
        alert('Produto adicionado ao carrinho!');
    };

    return (
        <div className="min-h-screen bg-white text-black pt-24 pb-20">
            <div className="container mx-auto px-4">
                {/* Breadcrumb */}
                <div className="flex items-center text-sm text-gray-500 mb-8">
                    <Link href="/" className="hover:text-brand-red">Home</Link>
                    <ChevronRight className="w-4 h-4 mx-2" />
                    <Link href="/produtos" className="hover:text-brand-red">Loja</Link>
                    <ChevronRight className="w-4 h-4 mx-2" />
                    <span className="text-black font-medium">{product.name}</span>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-12">
                    {/* Gallery */}
                    <div className="space-y-4">
                        <div className="aspect-square bg-gray-100 rounded-lg overflow-hidden relative">
                            <div
                                className="absolute inset-0 bg-cover bg-center"
                                style={{ backgroundImage: `url(${product.images[currentImage]})` }}
                            />
                        </div>
                        <div className="flex gap-4 overflow-x-auto pb-2">
                            {product.images.map((img, index) => (
                                <button
                                    key={index}
                                    onClick={() => setCurrentImage(index)}
                                    className={`w-20 h-20 flex-shrink-0 rounded-md overflow-hidden border-2 ${currentImage === index ? 'border-brand-red' : 'border-transparent'
                                        }`}
                                >
                                    <div
                                        className="w-full h-full bg-cover bg-center"
                                        style={{ backgroundImage: `url(${img})` }}
                                    />
                                </button>
                            ))}
                        </div>
                    </div>

                    {/* Info */}
                    <div>
                        <span className="text-brand-red font-bold tracking-wider uppercase text-sm">
                            {product.category}
                        </span>
                        <h1 className="text-4xl font-bold mt-2 mb-4">{product.name}</h1>
                        <p className="text-3xl font-bold mb-6">
                            {new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(product.price)}
                        </p>

                        <div className="prose prose-gray mb-8">
                            <p>{product.description}</p>
                        </div>

                        {/* Sizes */}
                        <div className="mb-8">
                            <h3 className="font-bold mb-3">Tamanho</h3>
                            <div className="flex gap-3">
                                {product.sizes.map((size) => (
                                    <button
                                        key={size}
                                        onClick={() => setSelectedSize(size)}
                                        className={`w-12 h-12 rounded-full flex items-center justify-center border font-medium transition-all ${selectedSize === size
                                                ? 'bg-black text-white border-black'
                                                : 'bg-white text-black border-gray-200 hover:border-black'
                                            }`}
                                    >
                                        {size}
                                    </button>
                                ))}
                            </div>
                        </div>

                        {/* Actions */}
                        <div className="flex gap-4 mb-8">
                            <Button
                                size="lg"
                                className="flex-1 text-lg h-14"
                                onClick={handleAddToCart}
                            >
                                <ShoppingCart className="w-5 h-5 mr-2" />
                                Adicionar ao Carrinho
                            </Button>
                            <Button variant="outline" size="lg" className="h-14 w-14 p-0">
                                <Heart className="w-6 h-6" />
                            </Button>
                        </div>

                        <div className="flex items-center gap-2 text-sm text-gray-500">
                            <Share2 className="w-4 h-4" />
                            Compartilhar este produto
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}
