generate-bindings:
  cd uniffi && cargo ndk -t arm64-v8a build -p chimera-ffi
  cd uniffi && cargo run -p uniffi-bindgen generate \
    --library target/aarch64-linux-android/debug/libchimera_ffi.so \
    --language kotlin \
    --out-dir ../core/src/main/java

build:
  ./gradlew assembleDebug