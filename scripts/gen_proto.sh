#!/usr/bin/env bash
/opt/homebrew/opt/protobuf/bin/protoc -I=src/main/protobuf --java_out=src/main/java src/main/protobuf/build.proto
/opt/homebrew/opt/protobuf/bin/protoc -I=src/main/protobuf --java_out=src/main/java src/main/protobuf/analysis.proto
/opt/homebrew/opt/protobuf/bin/protoc -I=src/main/protobuf --java_out=src/main/java src/main/protobuf/analysis_v2.proto
/opt/homebrew/opt/protobuf/bin/protoc -I=src/main/protobuf --java_out=src/main/java src/main/protobuf/stardoc_output.proto
