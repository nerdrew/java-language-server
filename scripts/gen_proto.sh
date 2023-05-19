#!/bin/bash
/opt/homebrew/opt/protobuf@3.19/bin/protoc -I=src/main/protobuf --java_out=src/main/java src/main/protobuf/build.proto
/opt/homebrew/opt/protobuf@3.19/bin/protoc -I=src/main/protobuf --java_out=src/main/java src/main/protobuf/analysis.proto
/opt/homebrew/opt/protobuf@3.19/bin/protoc -I=src/main/protobuf --java_out=src/main/java src/main/protobuf/analysis_v2.proto
