{ pkgs ? import <nixpkgs> { }
 , system ? builtins.currentSystem
 , lib ? pkgs.lib } :
with pkgs;
let sourceFilesOnly = path: type:
    !((baseNameOf path == "var") ||
      (baseNameOf path == "target"));
depSpecs = builtins.fromJSON (builtins.readFile ./generated/nix-deps.json);
mapJars = builtins.foldl'
 (m: a: m // builtins.listToAttrs
              [(lib.attrsets.nameValuePair
               (builtins.head a.coordinates)
               ( a // { storePath = fetchurl {
                  urls = map (r: lib.concatStrings [r a.relativePathname])
                             depSpecs.repositories;
                  sha256 = a.sha256;
                  };
               }) )])
 {}
 depSpecs.artifacts;
jarPaths = map (a: a.storePath) (builtins.attrValues mapJars);
in stdenv.mkDerivation rec {
  CLASSPATH = (lib.concatStrings (lib.intersperse ":" jarPaths));
  name = "epsilon";
  mainClass = "epsilon.server";
  cljsMain = "epsilon.client";
  src = [(builtins.filterSource sourceFilesOnly ./.) ];
  sourceRoot = "epsilon";
  nativeBuildInputs = [ clojure openjdk makeWrapper ];
  buildPhase = ''
    export BUILD_CLASSPATH=src:generated:${CLASSPATH}
    mkdir -p out tmp
    java -cp $BUILD_CLASSPATH clojure.main  \
      -e '(require (quote cljs.build.api))' \
      -e "(cljs.build.api/build \"src\" {:main (quote ${cljsMain}) :optimizations :whitespace :output-dir \"tmp\" :output-to \"out/public/frontend.js\"})"
    java -cp $BUILD_CLASSPATH -Dclojure.compile.path=out clojure.lang.Compile ${mainClass}
  '';
  installPhase = ''
    mkdir -p $out
    mkdir -p $out/share/java
    jar cef ${mainClass} $out/share/java/$name.jar -C out  .
    mkdir -p $out/bin
    makeWrapper ${jre_headless}/bin/java $out/bin/$name \
      --add-flags "-cp ${CLASSPATH}:$out/share/java/$name.jar" \
      --add-flags "${mainClass}"
  ''	  ;

}
