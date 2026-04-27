package org.javacs;

import com.google.devtools.build.lib.analysis.AnalysisProtos;
import com.google.devtools.build.lib.analysis.AnalysisProtosV2;
import com.google.devtools.build.lib.analysis.AnalysisProtosV2.PathFragment;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

class InferConfig {
    private static final Logger LOG = Logger.getLogger("main");

    /** Root of the workspace that is currently open in VSCode */
    private final Path workspaceRoot;

    /** External dependencies specified manually by the user */
    private final Collection<String> externalDependencies;

    /** Location of the maven repository, usually ~/.m2 */
    private final Path mavenHome;

    /** Location of the gradle cache, usually ~/.gradle */
    private final Path gradleHome;

    /** Environment variables, primarily for testing */
    private final Map<String, String> envVars;

    private final String[] protoLib = {"proto_library"};
    private final String[] javaLib = {
        "java_library", "java_test", "java_binary",
        "kt_jvm_library", "kt_jvm_test", "kt_jvm_binary",
    };
    private final Path cwd = Paths.get(System.getProperty("user.dir"));
    private Path bzlOutputBase;

    private boolean protosBuilt = false;
    private boolean skipProtos = false;

    InferConfig(
            Path workspaceRoot,
            Collection<String> externalDependencies,
            Path mavenHome,
            Path gradleHome,
            boolean skipProtos,
            Map<String, String> envVars) {
        this.workspaceRoot = workspaceRoot;
        this.externalDependencies = externalDependencies;
        this.mavenHome = mavenHome;
        this.gradleHome = gradleHome;
        this.skipProtos = skipProtos;
        this.envVars = Objects.requireNonNullElseGet(envVars, System::getenv);
    }

    InferConfig(
            Path workspaceRoot,
            Collection<String> externalDependencies,
            Path mavenHome,
            Path gradleHome,
            boolean skipProtos) {
        this(workspaceRoot, externalDependencies, mavenHome, gradleHome, skipProtos, null);
    }

    InferConfig(Path workspaceRoot, Collection<String> externalDependencies, Path mavenHome, Path gradleHome) {
        this(workspaceRoot, externalDependencies, mavenHome, gradleHome, false, null);
    }

    InferConfig(Path workspaceRoot, Collection<String> externalDependencies, boolean skipProtos) {
        this(workspaceRoot, externalDependencies, defaultMavenHome(), defaultGradleHome(), skipProtos, null);
    }

    InferConfig(Path workspaceRoot, Collection<String> externalDependencies) {
        this(workspaceRoot, externalDependencies, defaultMavenHome(), defaultGradleHome(), false, null);
    }

    InferConfig(Path workspaceRoot) {
        this(workspaceRoot, Collections.emptySet(), defaultMavenHome(), defaultGradleHome(), false, null);
    }

    InferConfig(Path workspaceRoot, Map<String, String> envVars) {
        this(workspaceRoot, Collections.emptySet(), defaultMavenHome(), defaultGradleHome(), false, envVars);
    }

    private static Path defaultMavenHome() {
        return Paths.get(System.getProperty("user.home")).resolve(".m2");
    }

    private static Path defaultGradleHome() {
        return Paths.get(System.getProperty("user.home")).resolve(".gradle");
    }

    /**
     * Find .jar files for external dependencies, for examples maven dependencies in ~/.m2 or jars
     * in bazel-genfiles
     */
    Set<Path> classPath() {
        // Check for CLASSPATH environment variable first
        String classPathEnv = this.envVars.get("CLASSPATH");
        if (classPathEnv != null && !classPathEnv.isEmpty()) {
            // TODO Add source/doc discovery for arbitrary jars provided via CLASSPATH.
            LOG.info("Using CLASSPATH environment variable: " + classPathEnv);
            return Arrays.stream(classPathEnv.split(Pattern.quote(File.pathSeparator)))
                         .map(Paths::get)
                         .collect(Collectors.toSet());
        }

        // externalDependencies
        if (!externalDependencies.isEmpty()) {
            var result = new HashSet<Path>();
            for (var id : externalDependencies) {
                var a = Artifact.parse(id);
                var found = findAnyJar(a, false);
                if (found == NOT_FOUND) {
                    LOG.warning(
                            String.format(
                                    "Couldn't find jar for %s in %s or %s",
                                    a, mavenHome, gradleHome));
                    continue;
                }
                result.add(found);
            }
            return result;
        }

        // Maven
        var pomXml = workspaceRoot.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            return mvnDependencies(pomXml, "dependency:list", this.envVars);
        }

        // Bazel
        var bazelWorkspaceRoot = bazelWorkspaceRoot();
        if (Files.exists(bazelWorkspaceRoot.resolve("MODULE.bazel"))) {
            return bazelClasspath(bazelWorkspaceRoot);
        }

        return Collections.emptySet();
    }

    private Path bazelWorkspaceRoot() {
        for (var current = workspaceRoot; current != null; current = current.getParent()) {
            if (Files.exists(current.resolve("MODULE.bazel"))) {
                return current;
            }
        }
        return workspaceRoot;
    }

    /** Find source .jar files in local maven repository. */
    Set<Path> buildDocPath() {
        // externalDependencies
        if (!externalDependencies.isEmpty()) {
            var result = new HashSet<Path>();
            for (var id : externalDependencies) {
                var a = Artifact.parse(id);
                var found = findAnyJar(a, true);
                if (found == NOT_FOUND) {
                    LOG.warning(
                            String.format(
                                    "Couldn't find doc jar for %s in %s or %s",
                                    a, mavenHome, gradleHome));
                    continue;
                }
                result.add(found);
            }
            return result;
        }

        // Maven
        var pomXml = workspaceRoot.resolve("pom.xml");
        if (Files.exists(pomXml)) {
            return mvnDependencies(pomXml, "dependency:sources", this.envVars);
        }

        // Bazel
        var bazelWorkspaceRoot = bazelWorkspaceRoot();
        if (Files.exists(bazelWorkspaceRoot.resolve("MODULE.bazel"))) {
            return bazelSourcepath(bazelWorkspaceRoot);
        }

        return Collections.emptySet();
    }

    private Path findAnyJar(Artifact artifact, boolean source) {
        Path maven = findMavenJar(artifact, source);

        if (maven != NOT_FOUND) {
            return maven;
        } else return findGradleJar(artifact, source);
    }

    Path findMavenJar(Artifact artifact, boolean source) {
        var jar =
                mavenHome
                        .resolve("repository")
                        .resolve(artifact.groupId.replace('.', File.separatorChar))
                        .resolve(artifact.artifactId)
                        .resolve(artifact.version)
                        .resolve(fileName(artifact, source));
        if (!Files.exists(jar)) {
            LOG.warning(jar + " does not exist");
            return NOT_FOUND;
        }
        return jar;
    }

    private Path findGradleJar(Artifact artifact, boolean source) {
        // Search for
        // caches/modules-*/files-*/groupId/artifactId/version/*/artifactId-version[-sources].jar
        var base = gradleHome.resolve("caches");
        var pattern =
                "glob:"
                        + String.join(
                                File.separator,
                                base.toString(),
                                "modules-*",
                                "files-*",
                                artifact.groupId,
                                artifact.artifactId,
                                artifact.version,
                                "*",
                                fileName(artifact, source));
        var match = FileSystems.getDefault().getPathMatcher(pattern);

        try {
            return Files.walk(base, 7).filter(match::matches).findFirst().orElse(NOT_FOUND);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String fileName(Artifact artifact, boolean source) {
        return artifact.artifactId + '-' + artifact.version + (source ? "-sources" : "") + ".jar";
    }

    static Set<Path> mvnDependencies(Path pomXml, String goal, Map<String, String> envVars) {
        Objects.requireNonNull(pomXml, "pom.xml path is null");
        try {
            // TODO consider using mvn valide dependency:copy-dependencies -DoutputDirectory=???
            // instead
            // Run maven as a subprocess
            String[] command = {
                getMvnCommand(envVars),
                "--batch-mode", // Turns off ANSI control sequences
                "validate",
                goal,
                "-DincludeScope=test",
                "-DoutputAbsoluteArtifactFilename=true",
            };
            LOG.info("Running " + String.join(" ", command) + " ...");
            var workingDirectory = pomXml.toAbsolutePath().getParent().toFile();
            var process =
                    new ProcessBuilder()
                            .command(command)
                            .directory(workingDirectory)
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .start();

            var output = process.getInputStream().readAllBytes();

            // Wait for process to exit
            var result = process.waitFor();
            if (result != 0) {
                LOG.severe("`" + String.join(" ", command) + "` returned " + result);
                return Set.of();
            }

            // Read output
            var dependencies = new HashSet<Path>();
            for (var line : new String(output, StandardCharsets.UTF_8).split("\\R")) {
                var jar = readDependency(line);
                if (jar != NOT_FOUND) {
                    dependencies.add(jar);
                }
            }
            return dependencies;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Pattern DEPENDENCY =
            Pattern.compile("^\\[INFO\\]\\s+(.*:.*:.*:.*:.*):(/.*?)( -- module .*)?$");

    static Path readDependency(String line) {
        var match = DEPENDENCY.matcher(line);
        if (!match.matches()) {
            return NOT_FOUND;
        }
        var artifact = match.group(1);
        var path = match.group(2);
        LOG.info(String.format("...%s => %s", artifact, path));
        return Paths.get(path);
    }

    static String getMvnCommand(Map<String, String> envVars) {
        envVars = Objects.requireNonNullElseGet(envVars, System::getenv);
        var mvnCommand = "mvn";
        if (File.separatorChar == '\\') {
            mvnCommand = findExecutableOnPath("mvn.cmd", envVars);
            if (mvnCommand == null) {
                mvnCommand = findExecutableOnPath("mvn.bat", envVars);
            }
        }
        // If findExecutableOnPath returns null (e.g. PATH is not set), we should still return "mvn"
        // and let the execution fail later if it's not on the (empty) path.
        return mvnCommand == null ? "mvn" : mvnCommand;
    }

    private static String findExecutableOnPath(String name, Map<String, String> envVars) {
        String pathEnv = envVars.get("PATH");
        if (pathEnv == null) {
            return null;
        }
        for (var dirname : pathEnv.split(File.pathSeparator)) {
            var file = new File(dirname, name);
            if (file.isFile() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }
        return null;
    }

    private boolean buildProtos() {
        if (skipProtos) {
            return false;
        }

        if (protosBuilt) {
            return true;
        }

        // `...` is relative to the bazel cwd, which is the LSP workspace root (e.g. `app/src/main/java`).
        // That misses java_proto_library targets defined elsewhere in the monorepo, so query the
        // transitive deps of `:lib` instead — the same convention used for the java_library sourcepath query.
        var targets = bazelQuery("kind(java_proto_library, deps(:lib))");

        if (targets.isEmpty()) {
            return false;
        }

        AtomicInteger count = new AtomicInteger();
        targets.stream()
                .collect(Collectors.groupingBy(t -> count.getAndIncrement() / 1000))
                .values()
                .forEach(this::bazelDryRunBuild);

        protosBuilt = true;

        return true;
    }

    private Set<Path> bazelClasspath(Path bazelWorkspaceRoot) {
        var absolute = new HashSet<Path>();

        // Add protos
        if (buildProtos()) {
            for (var relative : bazelAQuery("Javac", "--output", protoLib, workspaceRoot)) {
                absolute.add(bazelWorkspaceRoot.resolve(relative));
            }
        }

        // Add rest of classpath
        for (var relative : bazelAQuery("Javac", "--classpath", javaLib, workspaceRoot)) {
            absolute.add(bazelWorkspaceRoot.resolve(relative));
        }
        return absolute;
    }

    private Set<Path> bazelSourcepath(Path bazelWorkspaceRoot) {
        var absolute = new LinkedHashSet<Path>();

        for (var lib : bazelQuery("kind(java_library, deps(:lib, 1))")) {
            absolute.add(bazelWorkspaceRoot.resolve(lib.substring(2, lib.lastIndexOf(":"))));
        }

        // Add src jars
        var unresolvedSources = new HashSet<Path>();
        for (var relative : bazelAQuery("JavaSingleJar", "--sources", javaLib, cwd)) {
            var path = bazelOutputBase().resolve(relative);
            if (Files.exists(path)) {
                absolute.add(path);
            } else {
                unresolvedSources.add(path.getFileName());
            }
        }

        if (!unresolvedSources.isEmpty()) {
            try {
                Files.walk(bazelOutputBase().resolve("external"))
                        .forEach(
                                p -> {
                                    if (unresolvedSources.remove(p.getFileName())) {
                                        LOG.fine("Found src jar: " + p);
                                        absolute.add(p);
                                    }
                                });
                // Files.walkFileTree(bazelOutputBase().resolve("external"), new SimpleFileVisitor<Path>() {
                //     @Override
                //     public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes _attrs) {
                //         if (dir.getFileName().toString().startsWith("rules_jvm_external++maven+")) {
                //             return FileVisitResult.CONTINUE;
                //         } else {
                //             return FileVisitResult.SKIP_SUBTREE;
                //         }
                //     }
                //
                //     @Override
                //     public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                //         if (unresolvedSources.remove(file.getFileName())) {
                //             LOG.fine("Found src jar: " + file);
                //             absolute.add(file);
                //         }
                //         return FileVisitResult.CONTINUE;
                //     }
                // });
            } catch (IOException __) {
                LOG.warning("Error finding unresolved sources:\n" + unresolvedSources);
            }
        }

        if (!unresolvedSources.isEmpty()) {
            LOG.warning("Couldn't find source jars:\n" + unresolvedSources);
        }

        // Add proto source files
        if (buildProtos()) {
            for (var relative : bazelAQuery("Javac", "--source_jars", protoLib, cwd)) {
                absolute.add(bazelWorkspaceRoot.resolve(relative));
            }
        }

        return absolute;
    }

    private Path bazelOutputBase() {
        if (bzlOutputBase != null) {
            return bzlOutputBase;
        }

        // Run bazel as a subprocess
        String[] command = {"bazel", "info", "output_base"};
        var output = fork(command, workspaceRoot, false);
        if (output == null) {
            return NOT_FOUND;
        }

        var strOutput = new String(output, StandardCharsets.UTF_8);
        bzlOutputBase = Paths.get(strOutput.substring(0, strOutput.length() - 1));
        return bzlOutputBase;
    }

    private void bazelDryRunBuild(List<String> targets) {
        var command = new ArrayList<String>();
        command.add("bazel");
        command.add("build");
        command.add("--keep_going");
        command.add("--nobuild");
        command.addAll(targets);
        String[] c = new String[command.size()];
        c = command.toArray(c);
        fork(c, workspaceRoot, true);
    }

    private Set<String> bazelQuery(String query) {
        String[] command = {"bazel", "query", "--keep_going", query};
        var output = fork(command, workspaceRoot, true);
        if (output == null) {
            return Set.of();
        }
        var outStr = new String(output, StandardCharsets.UTF_8);
        return new HashSet<String>(Arrays.stream(outStr.split("\\R")).filter(s -> !s.isEmpty()).toList());
    }

    private Set<String> bazelAQuery(
            String filterMnemonic, String filterArgument, String[] kinds, Path cmdDir) {
        String kindUnion = "";
        for (var kind : kinds) {
            if (kindUnion.length() > 0) {
                kindUnion += " union ";
            }
            kindUnion += "kind(" + kind + ", ...)";
        }
        String[] command = {
            "bazel",
            "aquery",
            "--keep_going",
            "--output=proto",
            "--include_aspects", // required for java_proto_library, see
            // https://stackoverflow.com/questions/63430530/bazel-aquery-returns-no-action-information-for-java-proto-library
            "--allow_analysis_failures",
            "mnemonic(" + filterMnemonic + ", " + kindUnion + ")"
        };
        var output = fork(command, cmdDir, true);
        if (output == null) {
            return Set.of();
        }
        return readActionGraph(output, filterArgument);
    }

    private Set<String> readActionGraph(byte[] output, String filterArgument) {
        try {
            var containerV2 = AnalysisProtosV2.ActionGraphContainer.parseFrom(output);
            if (containerV2.getArtifactsCount() != 0
                    && containerV2.getArtifactsList().get(0).getId() != 0) {
                return readActionGraphFromV2(containerV2, filterArgument);
            }
            var containerV1 = AnalysisProtos.ActionGraphContainer.parseFrom(output);
            return readActionGraphFromV1(containerV1, filterArgument);
        } catch (InvalidProtocolBufferException e) {
            LOG.warning("Could not parse proto:\n" + output);

            throw new RuntimeException(e);
        }
    }

    private Set<String> readActionGraphFromV1(
            AnalysisProtos.ActionGraphContainer container, String filterArgument) {
        var argumentPaths = new HashSet<String>();
        var outputIds = new HashSet<String>();
        for (var action : container.getActionsList()) {
            var isFilterArgument = false;
            for (var argument : action.getArgumentsList()) {
                if (isFilterArgument && argument.startsWith("-")) {
                    isFilterArgument = false;
                    continue;
                }
                if (!isFilterArgument) {
                    isFilterArgument = argument.equals(filterArgument);
                    continue;
                }
                argumentPaths.add(argument);
            }
            outputIds.addAll(action.getOutputIdsList());
        }
        var artifactPaths = new HashSet<String>();
        for (var artifact : container.getArtifactsList()) {
            if (!argumentPaths.contains(artifact.getExecPath())) {
                // artifact was not specified by --filterArgument
                continue;
            }
            if (outputIds.contains(artifact.getId()) && !filterArgument.equals("--output")) {
                // artifact is the output of another java action
                continue;
            }
            var relative = artifact.getExecPath();
            LOG.fine("...found bazel dependency " + relative);
            artifactPaths.add(relative);
        }
        return artifactPaths;
    }

    private Set<String> readActionGraphFromV2(
            AnalysisProtosV2.ActionGraphContainer container, String filterArgument) {
        var argumentPaths = new HashSet<String>();
        var outputIds = new HashSet<Integer>();

        for (var action : container.getActionsList()) {
            var isFilterArgument = false;
            for (var argument : action.getArgumentsList()) {
                if (isFilterArgument && argument.startsWith("-")) {
                    isFilterArgument = false;
                    continue;
                }
                if (!isFilterArgument) {
                    isFilterArgument = argument.equals(filterArgument);
                    continue;
                }
                var parts = argument.split(",@@");
                if (parts.length > 1) {
                    argument = String.join("", Arrays.copyOfRange(parts, 0, parts.length - 1));
                }
                argumentPaths.add(argument);
            }
            outputIds.addAll(action.getOutputIdsList());
        }
        var artifactPaths = new HashSet<String>();
        for (var artifact : container.getArtifactsList()) {
            if (outputIds.contains(artifact.getId()) && !filterArgument.equals("--output")) {
                // artifact is the output of another java action
                continue;
            }
            var relative =
                    buildPath(container.getPathFragmentsList(), artifact.getPathFragmentId());
            if (!argumentPaths.contains(relative)) {
                // artifact was not specified by --filterArgument
                continue;
            }
            LOG.fine("...found bazel dependency " + relative);
            artifactPaths.add(relative);
        }
        return artifactPaths;
    }

    private static String buildPath(List<PathFragment> fragments, int id) {
        for (PathFragment fragment : fragments) {
            if (fragment.getId() == id) {
                if (fragment.getParentId() != 0) {
                    return buildPath(fragments, fragment.getParentId()) + "/" + fragment.getLabel();
                }
                return fragment.getLabel();
            }
        }
        throw new RuntimeException();
    }

    private byte[] fork(String[] command, Path cmdDir, boolean allowNonZeroExit) {
        try {
            LOG.info(String.format("Running in %s: %s", cmdDir, String.join(" ", command)));
            var process =
                    new ProcessBuilder()
                            .command(command)
                            .directory(cmdDir.toFile())
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .start();

            var output = process.getInputStream().readAllBytes();

            var result = process.waitFor();
            if (result != 0) {
                LOG.severe("`" + String.join(" ", command) + "` returned " + result);
                if (!allowNonZeroExit) {
                    return null;
                }
            }
            return output;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static final Path NOT_FOUND = Paths.get("");
}
