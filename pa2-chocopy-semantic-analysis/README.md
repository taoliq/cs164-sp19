# CS 164: Programming Assignment 2

[PA2 Specification]: http://inst.eecs.berkeley.edu/~cs164/sp19/hw/PA2.pdf
[ChocoPy Specification]: http://inst.eecs.berkeley.edu/~cs164/sp19/chocopy_language_reference.pdf

## Getting started

Run the following command to build your semantic analysis, and then run all the provided tests:

    mvn clean package

    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        --pass=.s --dir src/test/data/pa2/sample/ --test


In the starter code, only two tests should pass. Your objective is to implement a semantic analysis that passes all the provided tests and meets the assignment specifications.

You can also run the semantic analysis on one input file at at time. In general, running the semantic analysis on a ChocoPy program is a two-step process. First, run the reference parser to get an AST JSON:


    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        --pass=r <chocopy_input_file> --out <ast_json_file> 


Second, run the semantic analysis on the AST JSON to get a typed AST JSON:

    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        -pass=.s  <ast_json_file> --out <typed_ast_json_file>


The `src/tests/data/pa2/sample` directory already contains the AST JSONs for the test programs (with extension `.out`); therefore, you can skip the first step for the sample test programs.

To observe the output of the reference implementation of the semantic analysis, replace the second step with the following command:


    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        --pass=.r <ast_json_file> --out <typed_ast_json_file>


In either step, you can omit the `--out <output_file>` argument to have the JSON be printed to standard output instead.

You can combine AST generation by the reference parser with your 
semantic analysis as well:

    java -cp "chocopy-ref.jar:target/assignment.jar" chocopy.ChocoPy \
        --pass=rs <chocopy_input_file> --out <typed_ast_json_file>


## Assignment specifications

See the [PA2 specification][] on the Lectures and Assignments page of our
website for a detailed specification of the assignment.
Refer to the [ChocoPy Specification][] on the CS164 web site
for the specification of the ChocoPy language. 

## Receiving updates to this repository

Add the `upstream` repository remotes (you only need to do this once in your local clone):


    git remote add upstream https://github.com/cs164spring2019/pa2-chocopy-semantic-analysis.git


To sync with updates upstream:

    git pull upstream master

## Submission writeup

Team member 1: 

Team member 2: 

(Students should edit this section with their write-up)
