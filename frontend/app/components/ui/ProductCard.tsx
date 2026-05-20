import Image from 'next/image';
import Link from 'next/link';
import { ShoppingCart, Heart } from 'lucide-react';
import { Product } from '@/types';
import { useCart } from '../../context/CartContext';
import { toast } from 'sonner';

interface ProductCardProps {
  product: Product;
}

export function ProductCard({ product }: ProductCardProps) {
  const { addItem } = useCart();

  const handleAddToCart = () => {
    addItem({
      id: product.id,
      name: product.name,
      price: product.price,
      image: product.image,
      quantity: 1,
      size: 'Único', // Default until variant selection is fully implemented
    });
    toast.success(`${product.name} adicionado ao carrinho!`);
  };

  return (
    <div className="group relative bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300 border border-gray-100 flex flex-col h-full">
      
      {/* Image Container */}
      <div className="relative aspect-square w-full overflow-hidden bg-gray-50">
        <Image 
          src={product.image} 
          alt={product.name} 
          fill
          sizes="(max-width: 768px) 100vw, 320px"
          className="object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
        />
        
        {/* Floating Actions */}
        <div className="absolute top-3 right-3 flex flex-col gap-2 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
          <button className="w-9 h-9 bg-white/90 backdrop-blur-sm rounded-full flex items-center justify-center text-gray-600 hover:text-brand-secondary hover:scale-110 transition-all shadow-sm">
            <Heart className="w-4 h-4" />
          </button>
        </div>

        {/* Tag */}
        {product.tag && (
          <div className="absolute top-3 left-3 bg-brand-secondary text-white text-[10px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-full shadow-sm">
            {product.tag}
          </div>
        )}
      </div>

      {/* Content */}
      <div className="p-4 sm:p-5 flex flex-col flex-1">
        <span className="text-xs text-brand-secondary font-semibold uppercase tracking-wider mb-1">
          {product.category}
        </span>
        <h3 className="text-[#1A1B1D] font-bold text-sm sm:text-base mb-2 line-clamp-2 leading-tight">
          <Link href={`/produto/${product.id}`} className="hover:underline">
            {product.name}
          </Link>
        </h3>
        
        <div className="mt-auto flex items-center justify-between gap-4 pt-3 border-t border-gray-50">
          <span className="text-lg font-black text-[#1A1B1D]">
            R$ {product.price.toFixed(2).replace('.', ',')}
          </span>
          <button 
            onClick={handleAddToCart}
            className="bg-brand-secondary w-fit py-3 px-5 text-white rounded-xl font-bold flex items-center justify-center hover:bg-[#1A1B1D] hover:scale-105 transition-all outline-none focus:ring-2 focus:ring-brand-primary focus:ring-offset-2 shrink-0"
          >
            COMPRAR
          </button>
          <button 
            onClick={handleAddToCart}
            className="w-10 h-10 bg-gray-100 text-[#1A1B1D] rounded-full flex items-center justify-center hover:bg-brand-secondary hover:text-white hover:scale-105 transition-all outline-none focus:ring-2 focus:ring-brand-secondary focus:ring-offset-2 shrink-0 group-hover:bg-brand-secondary group-hover:text-white"
          >
            <ShoppingCart className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
