# ANÁLISE DE INFRAESTRUTURA E PREPARAÇÃO PARA PRODUÇÃO — DN STORE

Quero preparar o DN Store para funcionar integralmente em produção, incluindo frontend, backend, banco de dados, Asaas e Melhor Envio.

Neste momento, NÃO quero implementar funcionalidades novas nem alterar arquivos.

Quero que você faça uma auditoria da infraestrutura atual e me apresente um plano de execução.

## Contexto

- Frontend: Next.js, React e TypeScript.
- Hospedagem do frontend: Vercel.
- Backend: Java 21 e Spring Boot 3.4.
- Banco de dados: MySQL 8.4.
- ORM: Spring Data JPA / Hibernate.
- Migrations: Flyway.
- Pagamentos: Asaas.
- Logística: Melhor Envio.

## O que preciso descobrir

1. Como o frontend está publicado na Vercel?
2. Como o frontend se comunica atualmente com o backend?
3. O backend está preparado para deploy?
4. Quais configurações ainda dependem de localhost?
5. Como o banco de dados está configurado?
6. Quais variáveis de ambiente são necessárias?
7. Como devemos hospedar o Spring Boot?
8. Como configurar HTTPS, CORS e comunicação entre frontend e backend?
9. Como configurar os webhooks do Asaas?
10. Como configurar OAuth e callback do Melhor Envio?
11. Como manter URLs permanentes sem depender do meu computador ligado?
12. Como futuramente conectar um domínio próprio sem reconstruir a aplicação?

## Requisitos

- Leia o CLAUDE.md.
- Utilize as Skills relevantes já instaladas.
- Analise apenas os arquivos necessários.
- Não leia o projeto inteiro sem necessidade.
- Não invente informações sobre a infraestrutura.
- Não exponha credenciais.
- Não altere arquivos.
- Não faça deploy.
- Não execute operações em produção.
- Não compre etiquetas nem gere cobranças.

## Resultado esperado

Quero um relatório objetivo com:

A. Arquitetura atual identificada.

B. O que já está funcionando.

C. O que está faltando.

D. Qual hospedagem você recomenda para o backend Java e por quê.

E. Qual hospedagem ou serviço de banco de dados devemos utilizar.

F. Todas as configurações e variáveis de ambiente necessárias.

G. Como ficará a arquitetura final com Vercel + backend + banco de dados + Asaas + Melhor Envio.

H. Plano de implementação dividido em etapas, na ordem correta.

I. Estimativa dos custos recorrentes de infraestrutura, separando o que é gratuito e o que é pago, sem inventar preços.

J. Checklist para deixar o sistema pronto para produção.

IMPORTANTE: quero uma solução definitiva de infraestrutura, com URLs permanentes. Não quero depender de Cloudflare Tunnel temporário ou de deixar meu Mac ligado.

Comece pela análise e apresente o relatório. Não faça alterações.
