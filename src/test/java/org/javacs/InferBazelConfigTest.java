package org.javacs;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import org.junit.Test;

public class InferBazelConfigTest {

    private Path bazelWorkspace = Paths.get("src/test/examples/bazel-project");
    private InferConfig bazel =
            new InferConfig(bazelWorkspace, Collections.emptySet(), Paths.get("nowhere"), Paths.get("nowhere"));

    @Test
    public void bazelClassPath() {
        assertThat(bazel.classPath(), contains(hasToString(endsWith("guava-18.0-ijar.jar"))));
    }

    @Test
    public void bazelClassPathSubdir() {
        InferConfig bazelSubdir =
                new InferConfig(
                        Paths.get("src/test/examples/bazel-project/hello"),
                        Collections.emptySet(),
                        Paths.get("nowhere"),
                        Paths.get("nowhere"));
        assertThat(bazelSubdir.classPath(), contains(hasToString(endsWith("guava-18.0-ijar.jar"))));
    }

    @Test
    public void bazelDocPath() {
        assertThat(bazel.buildDocPath(), contains(hasToString(endsWith("guava-18.0-sources.jar"))));
    }
}
