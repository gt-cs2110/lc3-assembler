.EXTERNAL OTHERFILE

.ORIG x3000
        AND R2, R2, 0
        ADD R2, R2, 3
        LEA R5, EXLOC
        LDR R5, R5, 2
        JSRR R5
        HALT
EXLOC .BLKW 2
.FILL OTHERFILE
.END
