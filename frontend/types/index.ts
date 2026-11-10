export interface ProductImage {
  id?: string;
  imageUrl: string;
  main: boolean;
}

export interface Category {
  id: string;
  name: string;
  description?: string;
}

export interface InstagramMediaItem {
  id: string;
  mediaType: 'IMAGE' | 'VIDEO' | 'CAROUSEL_ALBUM';
  mediaUrl: string | null;
  thumbnailUrl: string | null;
  timestamp: string | null;
}

export interface InstagramPost extends InstagramMediaItem {
  caption: string | null;
  permalink: string | null;
  children: InstagramMediaItem[];
}

export interface ProductVariant {
  id: string;
  size: string;
  type?: string;
  color?: string;
  stock: number;
  active: boolean;
  sku?: string;
}

export const PRODUCT_VARIANT_SIZES = ['PP', 'P', 'M', 'G', 'GG', 'BL P', 'BL M', 'BL G', 'Único'] as const;

export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  promotionalPrice?: number;
  stock?: number;
  active: boolean;
  weight?: number;
  width?: number;
  height?: number;
  depth?: number;
  images: ProductImage[];
  variants?: ProductVariant[];
  category?: Category | string;
  color?: string;
  size?: string[];
  tag?: string;
}

export interface ProductDefaultRuleCriteria {
  nameContains?: string | null;
  categoryId?: string | null;
  color?: string | null;
  minPrice?: number | null;
  maxPrice?: number | null;
}

export interface ProductDefaultRuleValues {
  name?: string | null;
  description?: string | null;
  price?: number | null;
  promotionalPrice?: number | null;
  clearPromotionalPrice?: boolean;
  active?: boolean | null;
  color?: string | null;
  clearColor?: boolean;
  categoryId?: string | null;
  clearCategory?: boolean;
  weight?: number | null;
  width?: number | null;
  height?: number | null;
  depth?: number | null;
  sizes?: string[] | null;
}

export interface ProductDefaultRuleRequest {
  name: string;
  criteria: ProductDefaultRuleCriteria;
  values: ProductDefaultRuleValues;
  excludedProductIds: string[];
}

export interface ProductDefaultRule extends ProductDefaultRuleRequest {
  id: string;
  updatedProducts: number;
  createdAt: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: 'USER' | 'ADMIN';
  roles?: string[];
  emailMfaEnabled?: boolean;
}

export interface CartItem {
  id: string;
  productId?: string;
  productVariantId?: string;
  quantity: number;
  price: number;
  product: Product;
}

export interface Cart {
  id: number;
  items: CartItem[];
  totalPrice: number;
}