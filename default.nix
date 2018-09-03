{ pkgs ? import <nixpkgs> {} } :
with pkgs;
let sourceFilesOnly = path: type:
    !((baseNameOf path == "var") ||
      (baseNameOf path == "target"));
  depSpecs = builtins.fromJSON (builtins.readFile ./deps.json);
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
  icons =  fetchFromGitHub {
    name  = "feather";
    owner = "feathericons"; repo = "feather";
    rev = "2ee03d261c0b342a30f1a0767aa62ed116d9a208";
    sha256 = "1vajpx7867aff9b4chbf2p83fqlpcnclxm710k5srvvkbgwfy8k2";
  };
in stdenv.mkDerivation rec {
  CLASSPATH = (lib.concatStrings (lib.intersperse ":" jarPaths));
  name = "epsilon";
  mainClass = "epsilon.server";
  cljsMain = "epsilon.client";
  src = let s = fetchFromGitHub {
    name = "epsilon";
    owner = "telent";
    repo = "epsilon";
    rev = "c3bb0ff030d6c067137c9806649e9c464626ebca";
    sha256 = "0ysciq97ggy5jkh995syzvx76w8j9pwpsnm9n9nrkr62alw6jd0q";
  }; in [s icons];
  sourceRoot = "epsilon";
  nativeBuildInputs = [ clojure openjdk makeWrapper ];
  buildPhase = ''
    pwd
    ls -l
    set -x
    export BUILD_CLASSPATH=src:generated:${CLASSPATH}
    mkdir -p out tmp 
    CLJ_CONFIG=. CLJ_CACHE=. clojure -Scp build:$CLASSPATH -A:build -m hiccupize-icons ${icons}/icons
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
