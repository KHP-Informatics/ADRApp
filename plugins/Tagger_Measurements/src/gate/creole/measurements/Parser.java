/*
 * Units is a program for unit conversion originally written in C by Adrian
 * Mariano (adrian@cam.cornell.edu.). Copyright (C) 1996, 1997, 1999, 2000,
 * 2001, 2002, 2003, 2004, 2005, 2006 by Free Software Foundation, Inc.
 * 
 * Java version Copyright (C) 2003, 2004, 2005, 2006, 2007 by Roman R
 * Redziejowski (roman.redz@tele2.se).
 * 
 * GATE version is Copyright (c) 2009-2011, The University of Sheffield.
 * 
 * This file is part of GATE (see http://gate.ac.uk/), and is free software,
 * Licensed under the GNU Library General Public License, Version 3, June 2007
 * (in the distribution as file licence.html, and also available at
 * http://gate.ac.uk/gate/licence.html).
 */
package gate.creole.measurements;

import java.util.Vector;

class Parser {
  MeasurementsParser gnuUnits = null;

  Parser(Lexer lexer, MeasurementsParser gnuUnits) {
    lex = lexer;
    this.gnuUnits = gnuUnits;
  }

  public Symbol unitexpr() throws Exception {

    @SuppressWarnings("unused")
    Production p = new Production(unitexpr);

    int i = currentProd.rhsSize();
    try {
      if(expr()) {
        // if we have found an expression after the unit then this is a weird
        // construct something like "he ran 100m in 10.5 seconds" where the in
        // is not a valid unit name in this context, so return the result before
        // this expression
                
        if(!token(END)) {
          
          // go back to the end of the previous token in the lexer so we
          // annotate the right bit of document
          lex.rollback();    
          return stack.elementAt(1);
        }
        Semantics.evalUnitExpr(currentProd, gnuUnits);
      } else {
        if(!token(END)) return Error(msg_unitexpr);
        insertEmpty(i);
        Semantics.evalUnitExpr(currentProd, gnuUnits);
      }
    } catch(Exception e) {
      return null;
    }
    return stack.elementAt(0);
  }

  private boolean expr() throws Exception {
    Production p = new Production(expr);
    if(term()) {
      if(expr_1()) {
        while(expr_1()) {
          // consume tokens
        }
        stackMsg(msg_expr_1);
      } else stackMsg(msg_expr_2);
      Semantics.evalExpr(currentProd);
      return p.reduce();
    }
    if(token(SLASH)) {
      if(!product()) Error(msg_product);
      Semantics.evalInverse(currentProd);
      return p.reduce();
    }
    return p.reject();
  }

  private boolean expr_1() throws Exception {
    if(!token(PLUS) && !token(MINUS)) return false;
    if(!term()) Error(msg_term);
    return true;
  }

  private boolean term() throws Exception {
    Production p = new Production(term);
    int i = currentProd.rhsSize();
    if(token(MINUS)) {
      if(!product()) Error(msg_product);
      if(term_2()) {
        while(term_2()) {
          // consume terms
        }
        stackMsg(msg_term_2);
      } else stackMsg(msg_term_3);
      Semantics.evalTerm(currentProd);
    } else {
      if(!product()) return p.reject();
      insertEmpty(i);
      if(term_2()) {
        while(term_2()) {
          // consume terms
        }
        stackMsg(msg_term_2);
      } else stackMsg(msg_term_3);
      Semantics.evalTerm(currentProd);
    }
    return p.reduce();
  }

  private boolean term_2() throws Exception {
    if(!token(SLASH) && !token(PER)) return false;
    if(!product()) {
      //we have hit something odd like
      // 4mg per rectum daily
      //so roll back to before the per
      lex.rollback();
      return true;
    }
    return true;
  }

  private boolean product() throws Exception {
    Production p = new Production(product);
    if(!factor()) return p.reject();
    if(product_1()) {
      while(product_MAG()) { // should this called product_1 or
        // product_MAG
        // consume products
      }
      stackMsg(msg_product_1);
    } else stackMsg(msg_product_2);
    Semantics.evalProduct(currentProd);
    return p.reduce();
  }

  /**
   * This was added to fix a parsing problem, but what was the problem?"
   */
  private boolean product_MAG() throws Exception {
    // int i = currentProd.rhsSize();
    if(!token(STAR)) return false;
    if(!factor()) Error(msg_factor);
    return true;
  }

  private boolean product_1() throws Exception {
    int i = currentProd.rhsSize();
    if(token(STAR)) {
      if(!factor()) Error(msg_factor);
      return true;
    }
    if(!factor()) return false;
    insertEmpty(i);
    return true;
  }

  private boolean factor() throws Exception {
    Production p = new Production(factor);
    if(!primary()) return p.reject();
    if(factor_2()) {
      while(factor_2()) {
        // consume factors
      }
      stackMsg(msg_factor_2);
    } else stackMsg(msg_factor_3);
    Semantics.evalFactor(currentProd);
    return p.reduce();
  }

  private boolean factor_2() throws Exception {
    if(!token(EXPONENT) && !token(DOUBLESTAR)) return false;
    if(!token(MINUS)) pushEmpty(msg_factor_1);
    if(!primary()) Error(msg_primary);
    return true;
  }

  private boolean primary() throws Exception {
    Production p = new Production(primary);
    if(numexpr()) {
      Semantics.makeNumUnit(currentProd, gnuUnits);
      return p.reduce();
    }
    if(token(unit_name)) {
      Semantics.pass(currentProd);
      return p.reduce();
    }
    if(token(LPAR)) {
      if(!expr()) Error(msg_expr);
      if(!token(RPAR)) Error(msg_RPAR);
      Semantics.removeParens(currentProd);
      return p.reduce();
    }
    if(token(SQRT)) {
      if(!argument()) Error(msg_argument);
      Semantics.evalSqrt(currentProd);
      return p.reduce();
    }
    if(token(CBRT)) {
      if(!argument()) Error(msg_argument);
      Semantics.evalCbrt(currentProd);
      return p.reduce();
    }
    if(token(bfunc)) {
      if(!argument()) Error(msg_argument);
      Semantics.evalBfunc(currentProd);
      return p.reduce();
    }
    if(primary_7()) return p.reduce();
    return p.reject();
  }

  private boolean primary_7() throws Exception {
    int i = currentProd.rhsSize();
    if(token(FUNCINV)) {
      if(!token(ufunc)) Error(msg_ufunc);
      if(!argument()) Error(msg_argument);
      Semantics.evalUfunc(currentProd);
    } else {
      if(!token(ufunc)) return false;
      insertEmpty(i);
      if(!argument()) Error(msg_argument);
      Semantics.evalUfunc(currentProd);
    }
    return true;
  }

  private boolean argument() throws Exception {
    Production p = new Production(argument);
    if(!token(LPAR)) return p.reject();
    if(!expr()) Error(msg_expr);
    if(!token(RPAR)) Error(msg_RPAR);
    Semantics.removeParens(currentProd);
    return p.reduce();
  }

  private boolean numexpr() throws Exception {
    Production p = new Production(numexpr);
    if(!token(number)) return p.reject();
    if(numexpr_0()) {
      while(numexpr_0()) {
        // consume numbers
      }
      stackMsg(msg_numexpr_0);
    } else stackMsg(msg_numexpr_1);
    Semantics.evalNumExpr(currentProd);
    return p.reduce();
  }

  private boolean numexpr_0() throws Exception {
    if(!token(BAR)) return false;
    if(!token(number)) Error(msg_number);
    return true;
  }

  private boolean token(Symbol t) {
    if(!lex.token.isA(t)) return false;
    currentProd.push(lex.token);
    lex.read();
    msgStack.removeAllElements();
    return true;
  }

  private boolean token(Type tt) {
    if(!lex.token.isA(tt)) return false;
    currentProd.push(lex.token);
    lex.read();
    msgStack.removeAllElements();
    return true;
  }

  private boolean pushEmpty(String s) {
    currentProd.push(EMPTY);
    msgStack.addElement(s);
    return true;
  }

  private boolean insertEmpty(int n) {
    currentProd.insert(EMPTY, n);
    return true;
  }

  private boolean stackMsg(String s) {
    msgStack.addElement(s);
    return true;
  }

  private Symbol Error(String s) throws Exception {
    StringBuffer sb = new StringBuffer();
    sb.append(lex.position() + ": Expected ");
    for(int i = 0; i < msgStack.size(); i++)
      sb.append(msgStack.elementAt(i) + " or ");
    sb.append(s + ". Found ");
    sb.append(lex.token.show() + ".");
    errors++;
    throw new Exception(sb.toString());
  }

  private Lexer lex;

  private Vector<Symbol> stack = new Vector<Symbol>(10, 10); // Parser stack

  private Production currentProd = null; // Current Production

  private Vector<String> msgStack = new Vector<String>(); // Message stack

  int errors = 0; // Error count

  // =====================================================================
  //
  // Parser symbols
  //
  // =====================================================================
  // -------------------------------------------------------------------
  // Generic terminals (token types)
  // -------------------------------------------------------------------
  final static Type number = new Type("number");

  final static Type unit_name = new Type("unit_name");

  final static Type bfunc = new Type("bfunc");

  final static Type ufunc = new Type("ufunc");

  final static Type delimiter = new Type("delimiter");

  final static Type invalid = new Type("invalid");

  // -------------------------------------------------------------------
  // Specific terminals (tokens)
  // -------------------------------------------------------------------
  final static Symbol BAR = new Symbol(delimiter, "|");

  final static Symbol CBRT = new Symbol(delimiter, "cuberoot");

  final static Symbol DOUBLESTAR = new Symbol(delimiter, "**");

  final static Symbol END = new Symbol(delimiter, "end of text");

  final static Symbol EXPONENT = new Symbol(delimiter, "^");

  final static Symbol FUNCINV = new Symbol(delimiter, "~");

  final static Symbol LPAR = new Symbol(delimiter, "(");

  final static Symbol MINUS = new Symbol(delimiter, "-");

  final static Symbol PER = new Symbol(delimiter, "per");

  final static Symbol PLUS = new Symbol(delimiter, "+");

  final static Symbol RPAR = new Symbol(delimiter, ")");

  final static Symbol SLASH = new Symbol(delimiter, "/");

  final static Symbol SQRT = new Symbol(delimiter, "sqrt");

  final static Symbol STAR = new Symbol(delimiter, "*");

  // -------------------------------------------------------------------
  // Nonterminals (phrases)
  // -------------------------------------------------------------------
  final static Type expr = new Type("expr");

  final static Type unitexpr = new Type("unitexpr");

  final static Type term = new Type("term");

  final static Type product = new Type("product");

  final static Type factor = new Type("factor");

  final static Type primary = new Type("primary");

  final static Type numexpr = new Type("numexpr");

  final static Type argument = new Type("argument");

  // -------------------------------------------------------------------
  // Default for optional phrase
  // -------------------------------------------------------------------
  final static Type empty = new Type("empty");

  final static Symbol EMPTY = new Symbol(empty, "");

  // =====================================================================
  //
  // Parts of error messages
  //
  // =====================================================================
  private final static String msg_number = "number";

  private final static String msg_ufunc = "ufunc";

  @SuppressWarnings("unused")
  private final static String msg_END = "'end of text'";

  private final static String msg_RPAR = "')'";

  private final static String msg_expr = "expr";

  private final static String msg_unitexpr = "unitexpr";

  private final static String msg_term = "term";

  private final static String msg_product = "product";

  private final static String msg_factor = "factor";

  private final static String msg_primary = "primary";

  private final static String msg_argument = "argument";

  private final static String msg_expr_1 = "'-' or '+'";

  private final static String msg_expr_2 = "'-' or '+'";

  private final static String msg_term_2 = "'per' or '/'";

  private final static String msg_term_3 = "'per' or '/'";

  private final static String msg_product_1 = "'*' or factor";

  private final static String msg_product_2 = "'*' or factor";

  private final static String msg_factor_1 = "'-'";

  private final static String msg_factor_2 = "'**' or '^'";

  private final static String msg_factor_3 = "'**' or '^'";

  private final static String msg_numexpr_0 = "'|'";

  private final static String msg_numexpr_1 = "'|'";

  static class Type {
    final String name;

    Type(final String n) {
      name = n;
    }
  }

  static class Symbol {
    Symbol(Type t, String s) {
      type = t;
      text = s;
    }

    Symbol(Type t, StringBuffer sb) {
      type = t;
      text = sb.toString();
    }

    Symbol(Type t, int i) {
      type = t;
      char[] c = {(char)i};
      text = new String(c);
    }

    Symbol(Type t) {
      type = t;
      text = null;
    }

    boolean isA(Symbol s) {
      if(this == s) return true;
      return type == s.type && text.equals(s.text);
    }

    boolean isA(Type t) {
      return type == t;
    }

    String show() {
      return (text == null ? type.name : "'" + text + "'");
    }

    void inheritFrom(final Symbol s) {
      numValue = s.numValue;
      value = s.value;
      func = s.func;
    }

    final Type type;

    final String text;

    double numValue;

    Measurement value;

    Function func;
  } // end Symbol

  class Production {
    private Production parent; // Parent production

    private int lhs; // Index of lhs on stack

    /**
     * Construct new Production for symbol "sym". Its parent is the current
     * Production. Set the new Production as current. Lhs of the new Production
     * inherits attributes from lhs of parent.
     */
    Production(Type sym) {
      parent = currentProd;
      lhs = stack.size();
      stack.addElement(new Symbol(sym));
      if(parent != null) lhs().inheritFrom(parent.lhs());
      currentProd = this;
    }

    /**
     * Symbol on the left-hand side.
     */
    public Symbol lhs() {
      return stack.elementAt(lhs);
    }

    /**
     * i-th symbol on the right-hand side. Symbols on the rhs are numbered
     * starting with 0. Negative i means i-th symbol from the end: -1 is last,
     * -2 one before last etc.
     */
    public Symbol rhs(int i) {
      return stack.elementAt(i < 0 ? stack.size() + i : lhs + 1 + i);
    }

    /**
     * The last recognized symbol on the right-hand side.
     */
    public Symbol rhsLast() {
      return stack.lastElement();
    }

    /**
     * Number of recognized symbols on the right-hand side.
     */
    public int rhsSize() {
      return stack.size() - lhs - 1;
    }

    /**
     * Add symbol on the right-hand side.
     */
    void push(Symbol s) {
      stack.addElement(s);
    }

    /**
     * Insert symbol before the n-th symbol on the rhs.
     */
    void insert(Symbol s, int n) {
      stack.insertElementAt(s, lhs + 1 + n);
    }

    /**
     * Reduce
     */
    boolean reduce() {
      stack.setSize(lhs + 1);
      currentProd = parent;
      return true;
    }

    /**
     * Reject
     */
    boolean reject() {
      stack.setSize(lhs);
      currentProd = parent;
      return false;
    }
  }

  static class Exception extends java.lang.Exception {

    private static final long serialVersionUID = -7598568735356971844L;

    Exception(final String s) {
      super(s);
    }
  }
}
