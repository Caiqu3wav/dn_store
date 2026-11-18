FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml .
# Cache das dependências: só é refeito quando o pom.xml muda.
RUN mvn -B -q dependency:go-offline

COPY src src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre

# Processo sem privilégios de root.
RUN groupadd --system app && useradd --system --gid app --no-create-home app

WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
RUN chown app:app app.jar
USER app

# Perfil de produção por padrão. Para rodar localmente com docker compose,
# o docker-compose.yml define SPRING_PROFILES_ACTIVE=local.
ENV SPRING_PROFILES_ACTIVE=prod
# O sistema grava e exibe datas como LocalDateTime (sem fuso) e os dados existentes foram gerados
# em horário de Brasília. Sem isto o container usaria UTC e os horários ficariam 3h adiantados.
ENV TZ=America/Sao_Paulo
# Limita o heap a uma fração da memória do container e derruba o processo em OOM
# para que a plataforma o reinicie em vez de mantê-lo degradado.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError"

# A plataforma de hospedagem pode injetar PORT; o padrão é 8080.
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
