package io.quarkus.tensorflow;

import org.tensorflow.TensorFlow;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;

/**
 * This alternate way to load TensorFlow overcomes limitations and bugs related to using TensorFlow 1.15 with
 * native image compilation in Quarkus. TensorFlow's NativeLibrary class contains private methods and code that
 * doesn't correctly load the framework libraries in this context, so it's just easier to create a simpler version
 * that does not contain complexities for other contexts such as Android.
 */
public class LoadTensorFlow {
    private static final Logger LOG = Logger.getLogger(LoadTensorFlow.class.getName());

    private static final String JNI_LIBNAME = "tensorflow_jni";

    // These files can be found inside the libtensorflow_jni Maven dependency
    private static final String DARWIN_X86_64_PATH = "org/tensorflow/native/darwin-x86_64/";
    private static final String DARWIN_X86_64_FRAMEWORK = "libtensorflow_framework.1.dylib";
    private static final String DARWIN_X86_64_JNI = "libtensorflow_jni.dylib";
    private static final String LINUX_X86_64_PATH = "org/tensorflow/native/linux-x86_64/";
    private static final String LINUX_X86_64_FRAMEWORK = "libtensorflow_framework.so.1";
    private static final String LINUX_X86_64_JNI = "libtensorflow_jni.so";

    public static void load() {

        if(!isLoaded() && !tryLoadLibrary()) {
            try {
                String libPath;
                String frameworkFileName;
                String jniFileName;

                final String osName = System.getProperty("os.name").toLowerCase();

                if(osName.contains("linux")) {
                    libPath = LINUX_X86_64_PATH;
                    frameworkFileName = LINUX_X86_64_FRAMEWORK;
                    jniFileName = LINUX_X86_64_JNI;
                }
                else if (osName.contains("os x")) {
                    libPath = DARWIN_X86_64_PATH;
                    frameworkFileName = DARWIN_X86_64_FRAMEWORK;
                    jniFileName = DARWIN_X86_64_JNI;
                }
                else {
                    LOG.severe(osName + " is not supported.");
                    return;
                }

                // Libraries need to be copied from classpath to tmp directory in order to be loaded from disk
                Path tmpPath = Files.createTempDirectory("tensorflow_native_libraries-");
                LOG.info("TensorFlow loading from: " + tmpPath.toFile().getAbsolutePath());

                copy(tmpPath, libPath, frameworkFileName);
                Path jniFilePath = copy(tmpPath, libPath, jniFileName);

                // Load the library from disk
                System.load(jniFilePath.toFile().getAbsolutePath());
                tmpPath.toFile().deleteOnExit();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static Path copy(Path tmpPath, String path, String fileName) throws IOException {
        InputStream inputStream = TensorFlow.class.getClassLoader().getResourceAsStream(path + fileName);
        Path filePath = tmpPath.resolve(fileName);
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        inputStream.close();
        return filePath;
    }

    private static boolean isLoaded() {
        try {
            TensorFlow.version();
            LOG.info("TensorFlow is already loaded");
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }

    private static boolean tryLoadLibrary() {
        try {
            System.loadLibrary(JNI_LIBNAME);
            LOG.info("TensorFlow loaded from libary: " + JNI_LIBNAME);
            return true;
        } catch (UnsatisfiedLinkError e) {
            return false;
        }
    }
}
