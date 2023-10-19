LC-3 Assembler
==============

This is a simple LC-3 assembler and linker written from scratch in Java (Java
because CS 1331 is a prereq). Pulkit wrote most of it but then Austin lightly
sabotaged it.

**The purpose of releasing this assembler to students is to give you the chance
to read the source of a 2-pass assembler and see how it would work.** It is not
intended to be perfect — much error handling is missing, for example. Same for
the linker.

How To Use This
---------------

First, compile the utilities in this repository:

    javac *.java

To assemble some LC-3 assembly into an object file (will create the object file
`my_assembly.obj` and the symbol table `my_assembly.sym`):

    java LC3asm my_assembly.asm

To link object files (links object files `my_assembly.obj` and `my_library.obj`
together into `linked.obj`. Note you will need to assemble `my_library.asm`
into `my_library.obj` first):

    java LC3link my_assembly.obj my_library.obj -o linked.obj

Finally, to convert a human-readable object file to an object file usable in
[LC3Tools][1] (will create a file named `linked.lc3tools.obj` you should open
in LC3Tools — click the chip symbol in the top right and then the folder symbol
on the left):

    java ObjToLC3Tools linked.obj

Try all those commands and see what happens! We provided `my_library.asm` and
`my_assembly.asm` in this repository for your convenience.

[1]: https://github.com/gt-cs2110/lc3tools
