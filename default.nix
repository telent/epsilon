{ pkgs ? import <nixpkgs> {} } :
with pkgs;
let sourceFilesOnly = src: path: type:
    let rel = lib.removePrefix (toString src + "/") (toString path);
    in ! ((lib.hasPrefix "generated" rel) ||
          (lib.hasPrefix "var" rel) ||
          (lib.hasPrefix "target" rel)) ;
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
  # location of librsvg changes some time after 18.03 I think
  rsvg = if (pkgs.gnome3 ? librsvg) then pkgs.gnome3.librsvg else pkgs.librsvg;
in stdenv.mkDerivation rec {
  CLASSPATH = (lib.concatStrings (lib.intersperse ":" jarPaths));
  NOTMUCH = "${pkgs.notmuch}/bin/notmuch";
  name = "epsilon";
  mainClass = "epsilon.server";
  cljsMain = "epsilon.client";
  src = builtins.filterSource (sourceFilesOnly ./.) ./.;
  buildInputs = [ pkgs.notmuch ];
  nativeBuildInputs = [ clojure openjdk makeWrapper rsvg ];
  makeIcons = ''
    CLJ_CONFIG=. CLJ_CACHE=. clojure -Scp build:$CLASSPATH -A:build -m hiccupize-icons ${icons}/icons;
    for w in 144 512; do
      rsvg-convert -w $w -a  -b '#552' --output=html/logo-''${w}.png html/epsilon.svg
    done
  '';
  buildPhase = ''
    export BUILD_CLASSPATH=src:generated:${CLASSPATH}
    mkdir -p target tmp
    ${makeIcons}
    java -cp $BUILD_CLASSPATH clojure.main  \
      -e '(require (quote cljs.build.api))' \
      -e "(cljs.build.api/build \"src\" {:main (quote ${cljsMain}) :optimizations :whitespace :output-dir \"tmp\" :output-to \"target/main.js\"})"
    java -cp $BUILD_CLASSPATH -Dclojure.compile.path=target clojure.lang.Compile ${mainClass}
    cp -r html/* target
  '';
  installPhase = ''
    mkdir -p $out
    mkdir -p $out/share/java
    jar cef ${mainClass} $out/share/java/$name.jar -C target  .
    mkdir -p $out/bin
    makeWrapper ${jre_headless}/bin/java $out/bin/$name \
      --add-flags "-cp ${CLASSPATH}:$out/share/java/$name.jar" \
      --add-flags "${mainClass}"
  ''	  ;

}
