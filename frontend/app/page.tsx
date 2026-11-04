import fs from 'fs';
import path from 'path';

import { Hero } from "./components/home/Hero";
import { Categories } from "./components/home/Categories";
import { FeaturedProducts } from "./components/home/FeaturedProducts";
import  InstagramSection from "./components/home/InstagramSection";
import { Sobre } from './components/home/Sobre';

export default function Home() {
  const brandImages = ['/assets/Images/pelotaodn.jpeg'];
  let heroImages: string[] = [];

  try {
    // Check for Hero images in the nested "dn" folder
    try {
      const dnAssetsDir = path.join(
        process.cwd(),
        'public',
        'assets',
        'dn'
      );

      if (fs.existsSync(dnAssetsDir)) {
        const dnFiles = fs.readdirSync(dnAssetsDir);

        const matchedHero = dnFiles
          .filter(
            f =>
              f.endsWith('.jpg') ||
              f.endsWith('.png') ||
              f.endsWith('.jpeg') ||
              f.endsWith('.webp') ||
              f.endsWith('.jfif')
          )
          .sort();

        if (matchedHero.length > 0) {
          heroImages = matchedHero.map(
            f => `/assets/dn/${f}`
          );
        }
      }
    } catch (e) {
      console.error("Error reading dn folder", e);
    }

    if (heroImages.length === 0) {
      heroImages = ['/assets/bg_hero.png'];
    }
  } catch {
    heroImages = ['/assets/bg_hero.png'];
  }

  return (
    <div className="flex flex-col min-h-screen bg-background">
      <Hero images={heroImages} />

      <FeaturedProducts />

      <Categories />

      <Sobre images={brandImages} />

      <InstagramSection />
    </div>
  );
}