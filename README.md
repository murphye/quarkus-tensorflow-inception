# Quarkus TensorFlow Inception Project

This project shows how you can combine TensorFlow and Quarkus together into one executable using GraalVM native image, 
JNI, and Protobuf to execute an object detection API in a Quarkus microservice. With this microservice, we detect 
objects in photos by returning labels, bounding boxes, and confidence scores.

## (Not Currently Working) Running the application in dev mode

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

You can then execute your binary: `./target/quarkus-tensorflow-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult https://quarkus.io/guides/building-native-image-guide .