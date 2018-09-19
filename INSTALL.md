# Installation

You have three options, which I have ranked in order of ease (least
effort to most effort) and inverse order of generality.

## Using Nix

If you have Nix installed, you can run

    $ nix-env -f 'https://github.com/telent/epsilon/archive/master.tar.gz' -i

and this will result in an executable shell script called `epsilon`

## Installing Nix (e.g. in a container)

If you don't have Nix but you do have Docker or similar, and a couple
Gb of disk space, 

    $ docker run -e ME=$USER -it debian:testing
    container$ apt-get install curl bzip2
    container$ adduser $ME
    container$ mkdir /nix
    container$ chown dan /nix
    container$ su - $ME
    username@container$ curl https://nixos.org/nix/install | sh

Now you have Nix.  Follow the instructions in the previous section.

## By hand

You will need to have installed:

* nodejs (icons are installed from an npm package)
* clojure 1.9 with the cli tools (`clojure` should be on your path)
* librsvg (from GNOME)

Read the commands in `default.nix` - particularly the portions of
shell script in `buildPhase` and `installPhase` - and attempt to
replicate them.
