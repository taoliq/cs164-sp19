# Runtime support function heap.init.
  mv a1, a0                                # Move requested size to A1
  li a0, @sbrk                             # Code for ecall: sbrk
  ecall                                    # Request A1 bytes
  jr ra                                    # Return to caller
