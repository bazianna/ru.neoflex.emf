
grammar Hron;

resource: nsPrefix? '[' (eObject (','? eObject)*)? ','? ']';

nsPrefix: ID;

eObject: label? eClass? '{' eFeature*  '}';

label: ID ':';

eClass: ID ('.' ID)?;

eFeature: ID '=' (expr|list) ';'?;

list: '[' (expr (','? expr)*)? ','? ']';

expr: labelRef |
      eObject |
      extRef |
      attribute
      ;

labelRef: ':' ID;

extRef: '#' '{' eClass STRING (path)? '}';

attribute: STRING;

path: STRING;

ID: [a-zA-Z_][a-zA-Z0-9_]*;

STRING: '"' (ESC|.)*? '"';
fragment ESC : '\\"' | '\\\\' ;

WS: [ \n\t\r]+ -> skip;
