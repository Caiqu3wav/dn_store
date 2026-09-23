import Link from 'next/link';
import { ChevronRight } from 'lucide-react';
import { productService } from '../../../services/productService';
import ProductInteractive from '../ProductInteractive';

export default async function ProductPage({
    params
}: {
    params: Promise<{ id: string }>;
}) {
    const { id } = await params;

    let product;

    try {
        product = await productService.getProductById(id);
    } catch (error) {
        console.error('Failed to fetch product:', error);

        return (
            <div className="min-h-screen bg-white text-black pt-24 pb-20 flex items-center justify-center">
                <p className="text-lg text-red-600">
                    Não foi possível carregar o produto.
                </p>
            </div>
        );
    }

    if (!product) {
        return (
            <div className="min-h-screen bg-white text-black pt-24 pb-20 flex items-center justify-center">
                <p className="text-lg text-red-600">
                    Produto não encontrado.
                </p>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-white text-black pt-24 pb-20">
            <div className="container mx-auto px-4">

                <div className="flex items-center text-sm text-gray-500 mb-8">
                    <Link
                        href="/"
                        className="hover:text-brand-red"
                    >
                        Home
                    </Link>

                    <ChevronRight className="w-4 h-4 mx-2" />

                    <Link
                        href="/produtos"
                        className="hover:text-brand-red"
                    >
                        Loja
                    </Link>

                    <ChevronRight className="w-4 h-4 mx-2" />

                    <span className="text-black font-medium">
                        {product.name}
                    </span>
                </div>

                <ProductInteractive product={product} />

            </div>
        </div>
    );
}