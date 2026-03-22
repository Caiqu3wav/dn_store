import fs from 'fs';
import path from 'path';
import { Hero } from "./components/home/Hero";
import { BrandSection } from "./components/home/BrandSection";
import { Categories } from "./components/home/Categories";
import { FeaturedProducts } from "./components/home/FeaturedProducts";
import { EventsTeaser } from "./components/home/EventsTeaser";
import { PaymentMethods } from "./components/home/PaymentMethods";

export default function Home() {
  // Dynamically checking for desafionatureza images in the public/assets directory
  let brandImages: string[] = [];
  try {
    const assetsDir = path.join(process.cwd(), 'public', 'assets');
    const files = fs.readdirSync(assetsDir);
    const matched = files
      .filter(f => f.startsWith('desafionatureza') && (f.endsWith('.jpg') || f.endsWith('.png') || f.endsWith('.jpeg') || f.endsWith('.webp')))
      .sort();
      
    if (matched.length > 0) {
      brandImages = matched.map(f => `/assets/${f}`);
    } else {
      brandImages = ['/assets/desafionatureza1.jpg']; // Fallback explicit path
    }
  } catch (e) {
    brandImages = ['/assets/desafionatureza1.jpg']; // Fallback
  }

  return (
    <div className="flex flex-col min-h-screen bg-background">
      <Hero />
      <FeaturedProducts />
      <Categories />
      <BrandSection images={brandImages} />
      <EventsTeaser />
      <PaymentMethods />
    </div>
  );
}
