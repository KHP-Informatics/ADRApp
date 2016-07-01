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

/**
 * Lexer for unit expressions; used by Parser.
 */
class Lexer {
  private String s; // Source string to parse.

  private int nxt; // Position of next character in source.

  private int pos; // starting position of token.

  private int stopped = -1;

  private int start;
  
  private int previous = -1;

  protected Parser.Symbol token; // Next token.

  protected String funcParm = ""; // Name of function parameter

  protected Measurement funcParmValue; // Value of function parameter

  private MeasurementsParser gnuUnits = null;

  void initialize(final String source, int start, MeasurementsParser gnuUnits) {
    s = source + "\n\n";
    nxt = start;
    this.start = start;
    this.gnuUnits = gnuUnits;
    read();
  }
  
  void rollback() {
    nxt = previous;
    pos = previous;
    stopped = previous;
  }

  String getParsedText() throws Parser.Exception {
    if(stopped == 0)
      throw new Parser.Exception("string '" + s + "' is not a unit");

    return s.substring(start, (stopped == -1 ? pos : stopped) - 1);
  }

  /**
   * Returns text identifying position of 'token'.
   */
  String position() {
    if(pos > start)
      return "After '"
              + s.substring(pos > (start + 15) ? pos - 15 : start, pos) + "'";
    return "At '"
            + s.substring(start,
                    s.length() > start + 17 ? start + 15 : s.length() - 2)
            + "'";
  }

  /**
   * Reads next token.
   */
  void read() {
    while(whitespace()) {
      // skip over the whitespace
    }
    previous = pos;
    pos = nxt;
    if(number()) return;
    if(delimiter()) return;
    if(word()) return;
    invalid();
  }

  /**
   * Accepts white space.
   */
  private boolean whitespace() {
    if(" \t".indexOf(s.charAt(nxt)) < 0) return false;
    nxt++;
    return true;
  }

  /**
   * Accepts number.
   */
  private boolean number() {
    if(".0123456789".indexOf(s.charAt(nxt)) < 0) return false;
    int i = Util.strtod(s, nxt);
    if(i == nxt) return false;
    String text = s.substring(nxt, i);
    try {
      double val = (Double.valueOf(text)).doubleValue();
      token = new Parser.Symbol(Parser.number, text);
      token.numValue = val;
      nxt = i;
      return true;
    } catch(Exception e) {
      return false;
    }
  }

  /**
   * Accepts word: any string other than whitespace, number or delimiter, not
   * containing + - * / | \t \n ^ blank ( )
   **/
  private boolean word() {
    int ew = Util.indexOf("+-*/|\t\n^ ()", s, nxt);
    if(ew == nxt) return false;
    String word = s.substring(nxt, ew);
    nxt = ew;
    // ------------------------------------------------------------------
    // Check for keywords.
    // ------------------------------------------------------------------
    if(word.equals("cuberoot"))
      token = Parser.CBRT;
    else if(word.equals("per"))
      token = Parser.PER;
    else if(word.equals("sqrt"))
      token = Parser.SQRT;
    // ------------------------------------------------------------------
    // Check for name of built-in function.
    // ------------------------------------------------------------------
    else if(gnuUnits.isBuiltInFunction(word)) {
      token = new Parser.Symbol(Parser.bfunc, word);
      token.func = gnuUnits.getBuiltInFunction(word);
    }
    // ------------------------------------------------------------------
    // Check for function parameter.
    // ------------------------------------------------------------------
    else if(word.equals(funcParm)) {
      token = new Parser.Symbol(Parser.unit_name, word);
      token.value = new Measurement(funcParmValue);
    }
    // ------------------------------------------------------------------
    // Check for name of user-defined function.
    // ------------------------------------------------------------------
    // else if(DefinedFunction.table.containsKey(word)) {
    // token = new Parser.Symbol(Parser.ufunc, word);
    // token.func = DefinedFunction.table.get(word);
    // }
    // ------------------------------------------------------------------
    // None of the above: treat as unit name.
    // ------------------------------------------------------------------
    else {
      token = new Parser.Symbol(Parser.unit_name, word);
      // Do exponent handling like m3
      int exp = 2 + "23456789".indexOf(word.charAt(word.length() - 1));
      if(exp > 1) word = word.substring(0, word.length() - 1);
      try {
        Measurement v = Measurement.fromName(word, gnuUnits);
        if(exp > 1) v.power(exp);
        token.value = v;
      } catch(Exception e) {
        token = Parser.END;
        // System.err.println("pos = " + pos);
        stopped = pos;
        nxt = s.length() - 1;
        // e.printStackTrace();
      }
    }
    return true;
  }

  private boolean delimiter() {
    char c = s.charAt(nxt);
    switch(c){
      case '|':
        token = Parser.BAR;
        break;
      case '\n':
        token = Parser.END;
        break;
      case '^':
        token = Parser.EXPONENT;
        break;
      case '~':
        token = Parser.FUNCINV;
        break;
      case '(':
        token = Parser.LPAR;
        break;
      case '-':
        token = Parser.MINUS;
        break;
      case '+':
        token = Parser.PLUS;
        break;
      case ')':
        token = Parser.RPAR;
        break;
      case '/':
        token = Parser.SLASH;
        break;
      case '*':
        if(s.charAt(nxt + 1) == '*') {
          token = Parser.DOUBLESTAR;
          nxt++;
          break;
        }
        token = Parser.STAR;
        break;
      default:
        return false;
    }
    nxt++;
    return true;
  }

  private void invalid() {
    token = new Parser.Symbol(Parser.invalid, s.charAt(nxt));
    nxt++;
  }
}
