; This symbol is defined in another module
.external ADDFUNC

.orig x3000
and r1, r1, 0
add r1, r1, 3 ; r1 <- 3

and r2, r2, 0
add r2, r2, 4 ; r2 <- 4

; call add(3, 4). r1 and r2 are arguments, result (r1+r2) is in r0
ld r3, ADDFUNC_ADDR
jsrr r3

add r1, r0, 0 ; r1 <- r0 (3+4)
and r2, r2, 0
add r2, r2, 5 ; r2 <- 5

; call add(3+4, 5). result is in r0
jsrr r3
st r0, ANSWER ; put 3+4+5 in ANSWER memory location below

halt

ANSWER .blkw 1
ADDFUNC_ADDR .fill ADDFUNC
.end
