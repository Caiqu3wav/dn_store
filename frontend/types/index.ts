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

export interface Product {
  id: string;
  name: string;
  description: string;
  price: number;
  promotionalPrice?: number;
  active: boolean;
  weight?: number;
  width?: number;
  height?: number;
  depth?: number;
  images: ProductImage[];
  category?: Category | string;
  color?: string;
  size?: string[];
  tag?: string;
}

export interface User {
  id: string;
  name: string;
  email: string;
  role: 'USER' | 'ADMIN';
  roles?: string[];
}

export interface CartItem {
  id: string;
  productVariantId?: string; // Dependendo de como está o backend
  productId?: string;
  quantity: number;
  price: number;
  product: Product;
}

export interface Cart {
  id: number;
  items: CartItem[];
  totalPrice: number;
}