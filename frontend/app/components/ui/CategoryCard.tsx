import Image from 'next/image';
import Link from 'next/link';

interface CategoryCardProps {
  id: string;
  name: string;
  image: string;
  slug: string;
}

export function CategoryCard({ id, name, image, slug }: CategoryCardProps) {
  return (
    <Link href={`/categoria/${slug}`} className="group block relative w-full h-[250px] sm:h-[300px] lg:h-[350px] rounded-2xl overflow-hidden">
      {/* Background Image */}
      <Image 
        src={image} 
        alt={name} 
        fill 
        className="object-cover group-hover:scale-105 transition-transform duration-700 ease-in-out"
      />
      
      {/* Overlay */}
      <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent flex flex-col justify-end p-6 sm:p-8">
        <h3 className="text-white text-2xl sm:text-3xl font-bold mb-2 translate-y-2 group-hover:translate-y-0 transition-transform duration-300">
          {name}
        </h3>
        <span className="text-white/80 text-sm font-medium uppercase tracking-wider opacity-0 group-hover:opacity-100 transition-opacity duration-300 flex items-center gap-2">
          Ver Produtos
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M14 5l7 7m0 0l-7 7m7-7H3" />
          </svg>
        </span>
      </div>
    </Link>
  );
}
