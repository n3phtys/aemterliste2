FROM oracle/graalvm-ce:latest
RUN gu install native-image
WORKDIR /tmp/build
ENV GRADLE_USER_HOME /tmp/build/.gradle
RUN sh -c 'cp $JAVA_HOME/jre/lib/amd64/libsunec.so / && cp $JAVA_HOME/jre/lib/security/cacerts /cacerts && ls -la /'

ADD . /tmp/build
RUN ./gradlew build fatJar
RUN native-image -jar /tmp/build/build/libs/aemterliste2-all-1.0-SNAPSHOT.jar -H:ReflectionConfigurationFiles=reflection.json -H:Name=aemterliste2 -H:+JNI -H:IncludeResourceBundles=javax.servlet.LocalStrings -H:IncludeResourceBundles=javax.servlet.http.LocalStrings --no-fallback --static -H:EnableURLProtocols=http,https --enable-all-security-services && ls -la /tmp/build

FROM alpine
WORKDIR /
COPY --from=0 /tmp/build/aemterliste2 /aemterliste2
COPY --from=0 /libsunec.so /libsunec.so
COPY --from=0 /cacerts /cacerts
COPY ./testdata /testdata
ENV AEMTERLISTE_TXT_FILE_BASE_DIR /testdata
RUN touch /testdata/aemter.json && touch /testdata/aemter27.json && touch /testdata/aemtermails.txt && touch /testdata/mailmanmails.txt && touch /testdata/mails.txt && ls -laR ./
CMD ["./aemterliste2", "-Djavax.net.ssl.trustStore=./cacerts", "-Djavax.net.ssl.trustAnchors=./cacerts", "-Djava.library.path=./"]
