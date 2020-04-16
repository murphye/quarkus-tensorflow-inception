# Quarkus TensorFlow Inception Project

This project shows how you can combine TensorFlow and Quarkus together into one executable using GraalVM native image, 
JNI, and Protobuf to execute an object detection API in a Quarkus microservice. With this microservice, we detect 
objects in photos by returning labels, bounding boxes, and confidence scores.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:
```
mvn quarkus:dev
```

## Packaging and running the application

The application is packageable using `mvn package`.
It produces the executable `quarkus-tensorflow-inception-1.0.0-SNAPSHOT-runner.jar` file in `/target` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/lib` directory.

The application is now runnable using `java -jar target/quarkus-tensorflow-inception-1.0.0-SNAPSHOT-runner.jar`.

## Creating a native executable

You can create a native executable using: `mvn package -Pnative`.

Or you can use Docker to build the native executable using: `mvn package -Pnative -Dquarkus.native.container-build=true`.

You can then execute your binary: `./target/quarkus-tensorflow-inception-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image-guide .

## Deploy native executable to OpenShift with binary build and Dockerfile

You can combine a custom Dockerfile with the OpenShift binary build process to create your own custom deployment of
a Quarkus application. This avoids the full S2I build process, but you have to build the Linux native image executable yourself!

This may also be useful to leverage in a build pipeline that checks out the code and builds it internally before deployment to OpenShift.

```
cat src/main/docker/Dockerfile.native.binary-build | oc new-build --name tensorquark --dockerfile='-'
oc start-build bc/tensorquark --from-file target/quarkus-tensorflow-inception-1.0.0-SNAPSHOT-runner --follow
oc expose svc/tensorquark
oc get route tensorquark
```
## Deploy JVM Uber JAR to OpenShift with binary build and Dockerfile

```
 oc new-build --name=tensorquark registry.redhat.io/openjdk/openjdk-11-rhel7 --binary=true

oc new-build --name=tensorquark openjdk --binary=true
oc start-build bc/tensorquark --from-file target/quarkus-tensorflow-inception-1.0.0-SNAPSHOT-runner.jar --follow
```