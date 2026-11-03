'use client';

import Image from 'next/image';
import Link from 'next/link';
import { ShoppingCart, Heart } from 'lucide-react';
import { Product } from '@/types';
import { useFavorites } from '@/app/context/FavoritesContext';
import { useCart } from '@/app/context/CartContext';
import { useRouter } from 'next/navigation';
import { getProductImage } from '@/utils/productImage';
import { useAuth } from '@/app/context/AuthContext';
import { toast } from 'react-hot-toast';

interface ProductCardProps {
  product: Product;
}

export function ProductCard({ product }: ProductCardProps) {
  const { addFavorite, removeFavorite, isFavorite, loading: favoritesLoading } = useFavorites();
  const { user, loading: authLoading } = useAuth();
  const { addItem } = useCart();
  const router = useRouter();

  const isFav = isFavorite(product.id);
  const image = getProductImage(product);

  const handleToggleFavorite = async (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    e.stopPropagation();
    if (authLoading || favoritesLoading) return;
    if (!user) {
      router.push(`/auth?next=${encodeURIComponent(window.location.pathname)}`);
      return;
    }

    try {
      if (isFav) {
        await removeFavorite(product.id);
        toast.success('Produto removido dos favoritos.');
      } else {
        await addFavorite(product);
        toast.success('Produto adicionado aos favoritos.');
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Não foi possível atualizar os favoritos.');
    }
  };

  const catName = typeof product.category === 'object' && product.category !== null 
    ? product.category.name 
    : (product.category || 'Geral');

  const handleAddToCart = (e: React.MouseEvent<HTMLButtonElement>) => {
    e.preventDefault();
    const activeVariants = product.variants?.filter(variant => variant.active) ?? [];
    const variant = activeVariants.length === 1 && activeVariants[0].stock > 0
      ? activeVariants[0]
      : undefined;
    if (!variant) {
      router.push(`/produtos/${product.id}`);
      return;
    }
    const added = addItem({
      id: variant.id,
      productId: product.id,
      productVariantId: variant.id,
      availableStock: variant.stock,
      name: product.name,
      price: product.price,
      image: image ?? '',
      quantity: 1,
      size: variant.size,
      category: catName,
    });
    if (!added) router.push(`/produtos/${product.id}`);
  };


  return (
    <div className="group relative bg-white rounded-2xl overflow-hidden shadow-sm hover:shadow-xl transition-all duration-300 border border-gray-100 flex flex-col h-full">
      {/* Image Container */}
      <div className="relative aspect-square w-full overflow-hidden bg-gray-50">
      <Link
  href={`/produtos/${product.id}`}
  className="relative aspect-square w-full overflow-hidden bg-gray-50 block"
>
      {image ? (
    <Image
      src={image}
      alt={product.name}
      fill
      sizes="(max-width: 768px) 100vw, 320px"
      className="object-cover group-hover:scale-105 transition-transform duration-500 ease-out"
    />
      ) : (
        <span className="absolute inset-0 flex items-center justify-center text-sm text-gray-500">
          Imagem indisponível
        </span>
      )}
</Link>

        {/* Floating Actions */}
        <div className="absolute top-3 right-3 flex flex-col gap-2 opacity-0 group-hover:opacity-100 transition-opacity duration-300">
          <button
            onClick={handleToggleFavorite}
            type="button"
            disabled={authLoading || favoritesLoading}
            aria-pressed={isFav}
            aria-label={isFav ? 'Remover dos favoritos' : 'Adicionar aos favoritos'}
            className={`w-9 h-9 rounded-full flex items-center justify-center hover:scale-110 transition-all shadow-sm ${
              isFav
                ? "bg-red-500 text-white"
                : "bg-white/90 backdrop-blur-sm text-gray-600 hover:text-brand-secondary"
            }`}
          >
            <Heart className={`w-4 h-4 ${isFav ? "fill-current" : ""}`} />
          </button>
        </div>

        {/* Tag */}
        {product.tag && (
          <div className="absolute top-3 left-3 bg-brand-red-primary text-white text-[10px] font-bold uppercase tracking-wider px-2.5 py-1 rounded-full shadow-sm">
            {product.tag}
          </div>
        )}
      </div>

      {/* Content */}
      <div className="p-4 sm:p-5 flex flex-col flex-1">
        <span className="text-xs text-brand-red-primary font-semibold uppercase tracking-wider mb-1">
          {catName}
        </span>
        <h3 className="text-[#1A1B1D] font-bold text-sm sm:text-base mb-2 line-clamp-2 leading-tight">
          <Link href={`/produtos/${product.id}`} className="hover:underline">
            {product.name}
          </Link>
        </h3>

        <div className="mt-auto flex items-center justify-between gap-3 pt-3 border-t border-gray-50">
          <div className="flex flex-col">
            {product.promotionalPrice ? (
              <>
                <span className="text-xs line-through text-gray-400">R$ {product.price.toFixed(2).replace(".", ",")}</span>
                <span className="text-lg font-black text-green-600">
                  R$ {product.promotionalPrice.toFixed(2).replace(".", ",")}
                </span>
              </>
            ) : (
              <span className="text-lg font-black text-[#1A1B1D]">
                R$ {product.price.toFixed(2).replace(".", ",")}
              </span>
            )}
          </div>
         <Link
  href={`/produtos/${product.id}`}
  className="w-fit py-3 px-5 rounded-xl font-bold flex items-center justify-center transition-all outline-none shrink-0 bg-brand-red-primary text-white hover:bg-[#1A1B1D] hover:scale-105"
>
  VER MAIS
</Link>

         <button
  onClick={handleAddToCart}
  aria-label="Adicionar ao carrinho ou escolher tamanho"
  className="w-10 h-10 rounded-full flex items-center justify-center transition-all outline-none shrink-0 bg-gray-100 text-[#1A1B1D] hover:bg-[#1A1B1D] hover:text-white hover:scale-105 focus:ring-2 focus:ring-brand-red-primary focus:ring-offset-2"
>
  <ShoppingCart className="w-4 h-4" />
</button>
        </div>
      </div>
    </div>
  );
}
