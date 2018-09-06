with import <nixpkgs> {};
let epsilon = callPackage ./default.nix {};
in
epsilon.overrideAttrs(o: {
  nativeBuildInputs = [ pkgs.boot pkgs.rlwrap pkgs.nodejs ] ++ o.nativeBuildInputs;
})
