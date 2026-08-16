binding-library := "target/aarch64-linux-android/debug/libchimera_ffi.so"
binding-file := "uniffi/chimera_ffi/chimera_ffi.kt"
tracked-binding := "core/src/main/java/" + binding-file
check-binding := "build/uniffi-bindings/" + binding-file

generate-binding-library:
  cd uniffi && RUSTC_BOOTSTRAP=1 cargo ndk -t arm64-v8a build --locked -p chimera-ffi

generate-bindings: generate-binding-library
  cd uniffi && RUSTC_BOOTSTRAP=1 cargo run --locked -p uniffi-bindgen -- generate \
    {{binding-library}} \
    --language kotlin \
    --out-dir ../core/src/main/java

check-bindings: generate-binding-library
  rm -rf build/uniffi-bindings
  mkdir -p build/uniffi-bindings
  cd uniffi && RUSTC_BOOTSTRAP=1 cargo run --locked -p uniffi-bindgen -- generate \
    {{binding-library}} \
    --language kotlin \
    --out-dir ../build/uniffi-bindings \
    --no-format
  python3 .github/check_uniffi_bindings.py {{tracked-binding}} {{check-binding}}

build:
  ./gradlew assembleDebug
