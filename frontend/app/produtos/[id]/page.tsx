'use client';

import { useState, useEffect } from 'react';
import { Button } from '../../components/ui/Button';
import { useCart } from '../../context/CartContext';
import { ShoppingCart, Heart, Share2, ChevronRight } from 'lucide-react';
import Link from 'next/link';
import { Product } from '../../../types';
import { productService } from '../../../services/productService';

export default function ProductPage({ params }: { params: { id: string } }) {
    const { id } = params;
    const [product, setProduct] = useState<Product | null>(null);
    const [selectedSize, setSelectedSize] = useState<string>('');
    const [currentImage, setCurrentImage] = useState(0);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const { addItem } = useCart();

    useEffect(() => {
        if (!id) return;

        setIsLoading(true);
        setError(null);

        productService.getProductById(id)
            .then((data) => {
                setProduct(data);
                const sizes = data.size && data.size.length ? data.size : ['Único'];
                setSelectedSize(sizes[0]);
                setCurrentImage(0);
            })
            .catch((err) => {
                console.error('Failed to fetch product', err);
                setError('Não foi possível carregar o produto.');
            })
            .finally(() => setIsLoading(false));
    }, [id]);

    const productImages = product?.images?.length
        ? product.images.map((img) => (typeof img === 'string' ? img : img.imageUrl))
        : ['/assets/products/placeholder.png'];

    const sizes = product?.size?.length ? product.size : ['Único'];

    const handleAddToCart = () => {
        if (!product) return;

        addItem({
            id: product.id,
            name: product.name,
            price: product.price,
            image: productImages[0] || '/assets/products/placeholder.png',
            quantity: 1,
            size: selectedSize
        });
        alert('Produto adicionado ao carrinho!');
    };

    if (isLoading) {
        return (
            <div className="min-h-screen bg-white text-black pt-24 pb-20 flex items-center justify-center">
                <p className="text-lg text-gray-600">Carregando produto...</p>
            </div>
        );
    }

    if (error || !product) {
        return (
            <div className="min-h-screen bg-white text-black pt-24 pb-20 flex items-center justify-center">
                <p className="text-lg text-red-600">{error || 'Produto não encontrado.'}</p>
            </div>
        );
    }

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
                                style={{ backgroundImage: `url(${productImages[currentImage]})` }}
                            />
                        </div>
                        <div className="flex gap-4 overflow-x-auto pb-2">
                            {productImages.map((img, index) => (
                                <button
                                    key={index}
                                    onClick={() => setCurrentImage(index)}
                                    className={`w-20 h-20 shrink-0 rounded-md overflow-hidden border-2 ${currentImage === index ? 'border-brand-red' : 'border-transparent'
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
                            {typeof product.category === 'object' && product.category !== null 
                                ? product.category.name 
                                : (product.category || 'Geral')}
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
                                {sizes.map((size) => (
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
