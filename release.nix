with import <nixpkgs> {};
let epsilon = callPackage ./default.nix {};
in {
  uberjar = epsilon.overrideAttrs(o: {
    nativeBuildInputs = [ pkgs.unzip ] ++ o.nativeBuildInputs;
    installPhase = ''
      for i in `echo $CLASSPATH | sed 's/:/ /g'`; do unzip -o  $i -d target ;done
      mkdir -p $out
      jar cef epsilon.server $out/epsilon-standalone.jar -C target  .
    '';
  });
}
