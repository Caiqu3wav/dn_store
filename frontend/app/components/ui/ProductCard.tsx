import Image from 'next/image';
import Link from 'next/link';
import { ShoppingCart, Heart } from 'lucide-react';

interface ProductCardProps {
  id: string;
  name: string;
  price: number;
  image: string;
  category: string;
  tag?: string;
}

export function ProductCard({ id, name, price, image, category, tag }: ProductCardProps) {
  return (
    <div className="group relative bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300 border border-gray-100 flex flex-col h-full">
      
      {/* Image Container */}
      <div className="relative aspect-square w-full overflow-hidden bg-gray-50">
        <Image 
          src={image} 
          alt={name} 
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
        {tag && (
          <div className="absolute top-3 left-3 bg-brand-highlight text-white text-[10px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-full shadow-sm">
            {tag}
          </div>
        )}
      </div>

      {/* Content */}
      <div className="p-4 sm:p-5 flex flex-col flex-1">
        <span className="text-xs text-brand-secondary font-semibold uppercase tracking-wider mb-1">
          {category}
        </span>
        <h3 className="text-brand-primary font-bold text-sm sm:text-base mb-2 line-clamp-2 leading-tight">
          <Link href={`/produto/${id}`} className="hover:underline">
            {name}
          </Link>
        </h3>
        
        <div className="mt-auto flex items-center justify-between gap-4 pt-3 border-t border-gray-50">
          <span className="text-lg font-black text-brand-primary">
            R$ {price.toFixed(2).replace('.', ',')}
          </span>
          <button className="bg-brand-secondary w-fit py-3 px-5 text-white rounded-xl flex items-center justify-center hover:bg-brand-primary hover:scale-105 transition-all outline-none focus:ring-2 focus:ring-brand-primary focus:ring-offset-2 shrink-0">
            COMPRAR
          </button>
          <button className="w-10 h-10 bg-brand-primary text-white rounded-full flex items-center justify-center hover:bg-brand-secondary hover:scale-105 transition-all outline-none focus:ring-2 focus:ring-brand-secondary focus:ring-offset-2 shrink-0 group-hover:bg-brand-secondary">
            <ShoppingCart className="w-4 h-4" />
          </button>
        </div>
      </div>
    </div>
  );
}
