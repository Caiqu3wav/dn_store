import fs from 'fs';
import path from 'path';
import { Hero } from "./components/home/Hero";
import { Sobre } from "./components/home/Sobre";
import { Categories } from "./components/home/Categories";
import { FeaturedProducts } from "./components/home/FeaturedProducts";
import { EventsTeaser } from "./components/home/EventsTeaser";

export default function Home() {
  let brandImages: string[] = [];
  let heroImages: string[] = [];
  
  try {
    const assetsDir = path.join(process.cwd(), 'public', 'assets');
    const files = fs.readdirSync(assetsDir);
    
    // Check for Brand images
    const matchedBrand = files
      .filter(f => f.startsWith('desafionatureza') && (f.endsWith('.jpg') || f.endsWith('.png') || f.endsWith('.jpeg') || f.endsWith('.webp')))
      .sort();
    brandImages = matchedBrand.length > 0 
      ? matchedBrand.map(f => `/assets/${f}`) 
      : ['/assets/desafionatureza1.jpg'];

    // Check for Hero images in the nested "dn" folder
    try {
      const dnAssetsDir = path.join(process.cwd(), 'public', 'assets', 'dn');
      if (fs.existsSync(dnAssetsDir)) {
        const dnFiles = fs.readdirSync(dnAssetsDir);
        const matchedHero = dnFiles
          .filter(f => (f.endsWith('.jpg') || f.endsWith('.png') || f.endsWith('.jpeg') || f.endsWith('.webp') || f.endsWith('.jfif')))
          .sort();
        if (matchedHero.length > 0) {
          heroImages = matchedHero.map(f => `/assets/dn/${f}`);
        }
      }
    } catch (e) {
      console.error("Error reading dn folder", e);
    }

    if (heroImages.length === 0) {
      heroImages = ['/assets/bg_hero.png']; // Default fallback
    }

  } catch (e) {
    brandImages = ['/assets/desafionatureza1.jpg'];
    heroImages = ['/assets/bg_hero.png'];
  }

  return (
    <div className="flex flex-col min-h-screen bg-background">
      <Hero images={heroImages} />
      <FeaturedProducts />
      <Categories />
      <Sobre images={brandImages} />
      <EventsTeaser />
    </div>
  );
}
