with import <nixpkgs> {};
let epsilon = callPackage ./default.nix {};
in
epsilon.overrideAttrs(o: {
  nativeBuildInputs = [ pkgs.rlwrap ] ++ o.nativeBuildInputs;
})
