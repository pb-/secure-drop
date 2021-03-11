FROM clojure:tools-deps AS builder

RUN mkdir -p /build
WORKDIR /build

# cache deps
COPY deps.edn /build
RUN clojure -P
RUN clojure -P -M:uberdeps

COPY src src/

RUN clojure -M:uberdeps


FROM gcr.io/distroless/java:11

WORKDIR /app
VOLUME /data
EXPOSE 8080/tcp

COPY --from=builder /build/target/build.jar /app/main.jar

ENTRYPOINT ["java", "-cp", "main.jar", "clojure.main", "-m", "securedrop"]

