FROM birdy/graalvm:latest
WORKDIR /tmp/build
ENV GRADLE_USER_HOME /tmp/build/.gradle

ADD . /tmp/build
RUN ./gradlew build fatJar
RUN native-image -jar /tmp/build/build/libs/aemterliste2-all-1.0-SNAPSHOT.jar -H:ReflectionConfigurationFiles=reflection.json -H:+JNI \
  -H:Name=aemterliste2 --static && ls -la /tmp/build

FROM scratch
COPY --from=0 /tmp/build/aemterliste2 /app/aemterliste2
COPY ./testdata /app/testdata
ENV AEMTERLISTE_TXT_FILE_BASE_DIR /app/testdata
WORKDIR /app
CMD ["/app/aemterliste2"]
