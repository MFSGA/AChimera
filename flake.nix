{
  description = "AChimera Android development environment";
  # Keep the source portable; flake.lock pins the exact nixpkgs revision.
  inputs.nixpkgs.url = "nixpkgs";
  outputs = { nixpkgs, ... }:
    let
      system = "x86_64-linux";
      pkgs = import nixpkgs { inherit system; config.allowUnfree = true; config.android_sdk.accept_license = true; };
      androidComposition = pkgs.androidenv.composeAndroidPackages {
        platformVersions = [ "36" ];
        buildToolsVersions = [ "36.0.0" ];
        includeEmulator = true;
        includeNDK = true;
        ndkVersions = [ "29.0.14206865" ];
      };
    in {
      devShells.${system}.default = pkgs.mkShell {
        nativeBuildInputs = with pkgs; [
          androidComposition.androidsdk jdk25 gradle
          cargo rustc rustfmt clippy
          clang cmake ninja pkg-config protobuf
        ];
        ANDROID_HOME = "${androidComposition.androidsdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${androidComposition.androidsdk}/libexec/android-sdk";
        JAVA_HOME = "${pkgs.jdk25.home}";
      };
    };
}
