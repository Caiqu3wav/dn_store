# Plano de Conexão e Integração Frontend <-> Backend

Quando removermos os mocks do frontend (Next.js/React), precisaremos de uma estrutura sólida, previsível e segura para consumir os dados do backend Java (Spring Boot). Este é o roteiro de como faremos essa integração.

## 1. Variáveis de Ambiente e Configuração
Criar um arquivo `.env.local` na raiz do frontend com a base URL da API para que o ambiente de dev e prod fiquem separados sem hardcoding:
```env
NEXT_PUBLIC_API_URL=http://localhost:8080/api
```

## 2. Cliente HTTP Centralizado (Axios + Interceptors)
Não faremos requisições diretamente com `fetch` avulso para rotas protegidas. Iremos criar uma instância central do **Axios** (`frontend/lib/axios.ts` ou `api/client.ts`).
- **Interceptors de Request**: Injetarão automaticamente o Token JWT (armazenado em HttpOnly Cookies ou LocalStorage) no header `Authorization: Bearer <token>` de todas as requisições, evitando repetição de código.
- **Interceptors de Response**: Vão capturar globalmente erros como `401 Unauthorized`. Se o token expirar, ele desloga o usuário e o redireciona para a tela de login.

## 3. Gerenciamento de Estado e Cache (React Query / SWR)
Para o catálogo, carrinho e favoritos, não usaremos apenas `useState` e `useEffect`. Iremos implementar uma biblioteca de Data Fetching (ex: **React Query** ou **SWR**).
- **Vantagens**: Cache automático (não recarrega a foto do produto se ele já foi visitado segundos atrás), loading states robustos (`isLoading`, `isError`), e refetch em background quando a aba do navegador volta ao foco.
- Os *mocks* estáticos atuais em `lib/data.ts` serão substituídos por Hooks. Exemplo: `useProducts({ category, search, sort })` que chama a API.

## 4. Separação de Serviços (Service Layer)
O frontend terá uma pasta `/services` para isolar a comunicação com a API dos componentes visuais:
- `services/productService.ts` (Listar, Buscar por ID)
- `services/cartService.ts` (Adicionar, Atualizar qtd, Remover)
- `services/authService.ts` (Login, Register, Logout)
- `services/favoriteService.ts`

## 5. Autenticação e Contexto do Usuário
- Substituir o estado local por uma integração real onde a validação de login chama `POST /api/auth/login`.
- Ao obter sucesso, o JWT será salvo e a aplicação atualizará o Header global.
- Atualizaremos o `Navbar` para esconder "Login" e mostrar os dados do Usuário (ex: miniatura, carrinho real).

## 6. Sincronização de Carrinho (Cart) e Favoritos
- **Usuários não logados**: O carrinho pode funcionar localmente (LocalStorage).
- **Usuários logados**: No momento que o usuário loga, o carrinho local é sincronizado e enviado para o backend (`POST /api/cart/items`), e a fonte de verdade passa a ser o banco de dados via API.

## 7. Renderização no Servidor (RSC - Next.js App Router)
Para otimização de SEO e performance da vitrine:
- Páginas como `/produtos` e a **Página Inicial (Home)** devem fazer o `fetch` dos produtos mais populares *no lado do servidor* (Server Components), entregando o HTML pronto para o Google e garantindo carregamento instantâneo.
- O cliente hidratará apenas as partes dinâmicas (botão de favoritar e adicionar ao carrinho).
