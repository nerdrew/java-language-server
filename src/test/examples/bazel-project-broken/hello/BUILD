load("@rules_java//java:defs.bzl", "java_binary")

java_binary(
    name = "main",
    srcs = glob(["src/**/*.java"]),
    main_class = "Hello",
    visibility = ["//visibility:public"],
    deps = [
        "@maven//:com_google_guava_guava",
    ],
)