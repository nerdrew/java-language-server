package org.javacs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasToString;

import java.nio.file.Paths;
import org.junit.Test;

public class InferBazelKeepGoingTest {
    @Test
    public void classPathFromBrokenProjectKeepsWorking() {
        var bazel = new InferConfig(Paths.get("src/test/examples/bazel-project-broken"));
        assertThat(bazel.classPath(), hasItem(hasToString(endsWith("header_guava-33.4.8-jre.jar"))));
    }
}
