load("@rules_java//java:defs.bzl", "java_library")

# Intentionally broken: references a nonexistent dep so analysis fails
# (but --allow_analysis_failures should let inference continue for siblings).
java_library(
    name = "broken",
    srcs = ["Broken.java"],
    deps = ["@does_not_exist//:missing"],
)
