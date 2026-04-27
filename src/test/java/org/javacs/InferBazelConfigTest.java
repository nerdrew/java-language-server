package org.javacs;

import java.util.Set;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Paths;
import org.junit.Ignore;
import org.junit.Test;

@Ignore // TODO Bazel updates have broken this, will revive it...
public class InferBazelConfigTest {
    @Test
    public void bazelClassPath() {
        var bazel = new InferConfig(Paths.get("src/test/examples/bazel-project"), Set.of(), false);
        assertThat(bazel.classPath(), hasItem(hasToString(endsWith("guava-33.4.8-jre.jar"))));
    }

    @Test
    public void bazelClassPathInSubdir() {
        var bazel = new InferConfig(Paths.get("src/test/examples/bazel-project/hello"), Set.of(), false);
        assertThat(bazel.classPath(), hasItem(hasToString(endsWith("guava-33.4.8-jre.jar"))));
    }

    @Test
    public void bazelClassPathWithProtos() {
        var bazel = new InferConfig(Paths.get("src/test/examples/bazel-protos-project"), Set.of(), false);
        var classPath = bazel.classPath();
        assertThat(classPath, hasItem(hasToString(endsWith("libperson_proto-speed.jar"))));
    }

    @Test
    public void bazelClassPathBrokenProject() {
        var bazel = new InferConfig(Paths.get("src/test/examples/bazel-project-broken"));
        assertThat(bazel.classPath(), contains(hasToString(endsWith("guava-18.0.jar"))));
    }

    @Test
    public void bazelDocPath() {
        var bazel = new InferConfig(Paths.get("src/test/examples/bazel-project"), Set.of(), false);
        var docPath = bazel.buildDocPath();
        assertThat(docPath, hasItem(hasToString(containsString("processed_guava-33.4.8-jre.jar"))));
    }

    @Test
    public void bazelDocPathInSubdir() {
        var bazel = new InferConfig(Paths.get("src/test/examples/bazel-project/hello"), Set.of(), false);
        assertThat(bazel.buildDocPath(), hasItem(hasToString(endsWith("processed_guava-33.4.8-jre.jar"))));
    }

    @Test
    public void bazelDocPathWithProtos() {
        var bazel = new InferConfig(Paths.get("src/test/examples/bazel-protos-project"), Set.of(), false);
        assertThat(bazel.buildDocPath(), hasItem(hasToString(endsWith("person_proto-speed-src.jar"))));
    }
}
