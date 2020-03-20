package org.tensorflow;

/**
 * This is a GraalVM workaround for static blocks not being executed at runtime in SubstrateVM. Since TensorFlow uses
 * static blocks to initialize, and the init API is not public, this class offers a new way to access TensorFlow.init.
 * The alternative is to use --initialize-at-run-time=org.tensorflow.Graph but this takes away compile time benefits
 * and also prevents initialization of TensorFlow objects inside a constructor.
 * @link https://medium.com/graalvm/understanding-class-initialization-in-graalvm-native-image-generation-d765b7e4d6ed
 * @see org.tensorflow.TensorFlow
 * @see org.tensorflow.Graph
 */
public class TensorFlowInitializer {
    public static void init() {
        TensorFlow.init();
    }
}
