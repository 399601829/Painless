grammar Painless;

@parser::header {
    import java.util.Set;
}

@parser::members {
    private Set<String> types = null;

    void setTypes(Set<String> types) {
        this.types = types;
    }

    boolean isType() {
        if (types == null) {
            throw new IllegalStateException();
        }

        return types.contains(getCurrentToken().getText());
    }
}

source
    : statement+ EOF
    ;

statement
    : IF LP expression RP block (ELSE block)?                                              # if
    | WHILE LP expression RP block                                                         # while
    | DO block WHILE LP expression RP                                                      # do
    | FOR LP declaration? SEMICOLON expression? SEMICOLON expression? RP ( block | empty ) # for
    | declaration SEMICOLON                                                                # decl
    | CONTINUE SEMICOLON                                                                   # continue
    | BREAK SEMICOLON                                                                      # break
    | RETURN expression SEMICOLON                                                          # return
    | expression SEMICOLON                                                                 # expr
    ;

block
    : LBRACK statement* RBRACK                 # multiple
    | statement                                # single
    ;

empty
    : SEMICOLON
    ;

declaration
    : decltype declvar ( COMMA declvar )*
    ;

decltype
    : {isType()}? ID (LBRACE RBRACE)*
    ;

declvar
    : {!isType()}? ID ( ASSIGN expression )?
    ;

expression
    :               LP expression RP                                          # precedence
    |               ( OCTAL | HEX | INTEGER | DECIMAL )                       # numeric
    |               STRING                                                    # string
    |               CHAR                                                      # char
    |               TRUE                                                      # true
    |               FALSE                                                     # false
    |               NULL                                                      # null
    |               extstart                                                  # ext
    |               ( BOOLNOT | BWNOT | ADD | SUB ) expression                # unary
    |               LP decltype RP expression                                 # cast
    |               expression ( MUL | DIV | REM ) expression                 # binary
    |               expression ( ADD | SUB ) expression                       # binary
    |               expression ( LSH | RSH | USH ) expression                 # binary
    |               expression ( LT | LTE | GT | GTE ) expression             # comp
    |               expression ( EQ | NE ) expression                         # comp
    |               expression BWAND expression                               # binary
    |               expression BWXOR expression                               # binary
    |               expression BWOR expression                                # binary
    |               expression BOOLAND expression                             # bool
    |               expression BOOLOR expression                              # bool
    | <assoc=right> expression COND expression COLON expression               # conditional
    |               extstart ASSIGN expression                                # assignment
    ;

extstart
   : extprec
   | extcast
   | exttype
   | extmember
   ;

extprec:   LP ( extprec | extcast | exttype | extmember) RP ( extdot | extbrace )?;
extcast:   LP decltype RP ( extprec | extcast | exttype | extmember );
extbrace:  LBRACE expression RBRACE ( extdot | extbrace )?;
extdot:    DOT ( extcall | extmember );
exttype:   {isType()}? ID extdot;
extcall:   ID arguments ( extdot | extbrace )?;
extmember: {!isType()}? ID (extdot | extbrace )?;

arguments
    : ( LP ( expression ( COMMA expression )* )? RP )
    ;

WS: [ \t\n\r]+ -> skip;

LBRACK:    '{';
RBRACK:    '}';
LBRACE:    '[';
RBRACE:    ']';
LP:        '(';
RP:        ')';
ASSIGN:    '=';
DOT:       '.';
COMMA:     ',';
SEMICOLON: ';';
IF:        'if';
ELSE:      'else';
WHILE:     'while';
DO:        'do';
FOR:       'for';
CONTINUE:  'continue';
BREAK:     'break';
RETURN:    'return';

BOOLNOT: '!';
BWNOT:   '~';
MUL:     '*';
DIV:     '/';
REM:     '%';
ADD:     '+';
SUB:     '-';
LSH:     '<<';
RSH:     '>>';
USH:     '>>>';
LT:      '<';
LTE:     '<=';
GT:      '>';
GTE:     '>=';
EQ:      '==';
NE:      '!=';
BWAND:   '&';
BWXOR:   '^';
BWOR:    '|';
BOOLAND: '&&';
BOOLOR:  '||';
COND:    '?';
COLON:   ':';

OCTAL: '0' [0-7]+ [lL]?;
HEX: '0' [xX] [0-9a-fA-F]+ [lL]?;
INTEGER: ( '0' | [1-9] [0-9]* ) [lL]?;
DECIMAL: ( ( '0' | [1-9] [0-9]* ) ( '.' [0-9]* )? | '.' [0-9]+ ) ( [eE] [+\-]? [0-9]+ )? [fF]?;

STRING: '"' ( '\\"' | '\\\\' | ~[\\"] )*? '"';
CHAR: '\'' . '\'';

TRUE:  'true';
FALSE: 'false';

NULL: 'null';

VOID: 'void';

ID: [_a-zA-Z] [_a-zA-Z0-9]*;