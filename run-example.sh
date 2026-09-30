#!/bin/sh
set -eu

repo_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
build_dir="$repo_dir/build/classes"
test_dir="$repo_dir/build/test-classes"
artifact="$repo_dir/build/infrai-payment-job.jar"

install_example() {
  mkdir -p "$build_dir"
  javac -d "$build_dir" "$repo_dir"/src/main/java/com/example/fintech/*.java
  cp "$repo_dir/src/main/resources/application.properties" "$build_dir/application.properties"
  jar --create --file "$artifact" \
    --main-class com.example.fintech.PaymentJobApplication -C "$build_dir" .
}

test_example() {
  mkdir -p "$test_dir"
  javac -cp "$artifact" -d "$test_dir" \
    "$repo_dir"/src/test/java/com/example/fintech/*.java
  java -cp "$artifact:$test_dir" com.example.fintech.PaymentRiskServiceTest
}

run_live() {
  if [ ! -f "$artifact" ]; then
    echo "Installed artifact not found; run $0 --install first" >&2
    exit 1
  fi
  java -jar "$artifact"
}

case "${1:-}" in
  --install)
    install_example
    ;;
  --live)
    run_live
    ;;
  --test)
    install_example
    test_example
    ;;
  "")
    install_example
    test_example
    run_live
    ;;
  *)
    echo "Usage: $0 [--install|--live|--test]" >&2
    exit 2
    ;;
esac
