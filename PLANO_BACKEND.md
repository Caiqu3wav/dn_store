# Plano de Implementação: Backend (Spring Boot)

Este documento descreve as etapas necessárias para preparar e solidificar o backend da DN Store para que o frontend possa consumir uma API real sem mocks.

## 1. Múltiplas Imagens de Produtos
Atualmente o `Product` tem apenas um atributo `imageUrl`. Precisamos suportar a galeria do frontend.
- **Entidade**: Criar `ProductImage.java` com `id`, `product_id`, `imageUrl`, `isMain`.
- **Relacionamento**: Modificar `Product.java` adicionando uma lista `@OneToMany(mappedBy = "product") List<ProductImage> images`.
- **Upload/Armazenamento**: O backend receberá via API as URLs das imagens geradas por um bucket de armazenamento ou CDN, ou faremos o upload multipart no futuro.

## 2. Busca e Filtros Dinâmicos
Para substituir o filtro no lado do cliente (frontend) por uma consulta real no banco de dados.
- **Service**: Adicionar suporte a parâmetros como `search`, `category`, `minPrice`, `maxPrice` e `sortBy` no `ProductService`.
- **Controller**: Atualizar `ProductController.java` (`GET /api/products`) para receber os `@RequestParam` e retornar a lista filtrada de forma eficiente.

## 3. Sistema de Favoritos (Wishlist)
Como definido, os favoritos serão vinculados ao **Produto Geral**.
- **Entidade**: Criar `Favorite.java` ligando `User` e `Product`.
- **Repository**: Criar `FavoriteRepository.java`.
- **Controller**: Criar `FavoriteController.java` com rotas para `GET /api/favorites`, `POST /api/favorites/{productId}` e `DELETE /api/favorites/{productId}`.

## 4. Segurança Administrativa
- **Configuração**: Ativar o `@EnableMethodSecurity` na classe de configuração do Spring Security.
- **Proteção**: Garantir que métodos de criação, atualização e exclusão no `ProductController` estejam protegidos com `@PreAuthorize("hasRole('ADMIN')")`.

## 5. Dinâmicas do Dashboard Admin
O painel administrativo no frontend precisa de dados reais de vendas e acessos.
- **DTO**: Criar `AdminDashboardDTO.java` para estruturar a resposta.
- **Controller**: O `AdminController` calculará e retornará métricas reais injetando dados do `OrderRepository`, `UserRepository` e `ProductRepository` (ex: total de vendas, total arrecadado, ticket médio, etc.).

## 6. Simulação de Checkout e Pedidos
Como gateways reais (Mercado Pago, Asaas, Correios) não estão definidos:
- **OrderService e DeliveryService**: Vamos garantir que a simulação funcione aceitando qualquer CEP e retornando um frete fixo fictício para aprovar a criação do `Order` no banco de dados.
- O Status do pedido será definido diretamente como "CONFIRMADO" temporariamente para liberar o fluxo visual do usuário no frontend.