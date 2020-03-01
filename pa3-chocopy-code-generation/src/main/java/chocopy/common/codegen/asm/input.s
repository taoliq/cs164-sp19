# Function input
  li a0, @error_nyi                        # Exit code for: Unsupported operation
  la a1, STRING["Unsupported operation"]   # Load error message as str
  addi a1, a1, @.__str__                   # Load address of attribute __str__
  j abort                                  # Abort
