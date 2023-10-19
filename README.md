LC-3-Assembler
==============

This is a simple LC-3 assembler and linker written from scratch in Java (Java
because CS 1331 is a prereq). Pulkit wrote most of it but then Austin lightly
sabotaged it.

**The purpose of releasing this assembler to students is to give you the chance
to read the source of a 2-pass assembler and see how it would work.** It is not
intended to be perfect — much error handling is missing, for example.

How To Use This
---------------

First, compile the utilities in this repository:

    javac *.java

To assemble some LC-3 assembly into an object file (will create the object file
`my_assembly.obj` and the symbol table `my_assembly.sym`):

    java LC3asm my_assembly.asm

To link object files (links object files `my_assembly.obj` and `my_library.obj`
together into `linked.obj`):

    java LC3link my_assembly.obj my_library.obj -o linked.obj

Finally, to convert a human-readable object file to an object file usable in
[LC3Tools][1] (will create a file named `linked.lc3tools.obj` you should open
in the simulator page in LC3Tools):

    java ObjToLC3Tools linked.obj

[1]: https://github.com/gt-cs2110/lc3tools
