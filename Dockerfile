FROM birdy/graalvm:latest
WORKDIR /tmp/build
ENV GRADLE_USER_HOME /tmp/build/.gradle

ADD . /tmp/build
RUN ./gradlew build fatJar
RUN native-image -jar /tmp/build/build/libs/aemterliste2-all-1.0-SNAPSHOT.jar -H:ReflectionConfigurationFiles=reflection.json -H:Name=aemterliste2 -H:+JNI --static && ls -la /tmp/build

FROM scratch
COPY --from=0 /tmp/build/aemterliste2 /aemterliste2
COPY ./testdata /testdata
ENV AEMTERLISTE_TXT_FILE_BASE_DIR /testdata
WORKDIR /
CMD ["/aemterliste2"]
