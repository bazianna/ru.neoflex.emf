
grammar Hron;

resource: '[' (eObject (',' eObject)?)? ','? ']';

eObject: label? eClass? '{' eFeature*  '}';

label: ID ':';

eClass: ID '.' ID;

eFeature: ID '=' (expr|list) ';';

list: '[' (expr (',' expr)?)? ','? ']';

expr: label |
      eObject |
      ref |
      STRING
      ;

ref: '#' '{' eClass STRING (path|label)?'}';

path: STRING;

ID: [a-zA-Z_][a-zA-Z0-9_]*;

STRING: '"' (ESC|.)*? '"';
fragment ESC : '\\"' | '\\\\' ;

WS: [ \n\t\r]+ -> skip;
