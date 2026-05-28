export interface Product {
  id: string;
  name: string;
  price: number;
  image: string;
  category: string;
  color: string;
  size: string[];
  tag?: string;
}