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
 * Holds procedures called by Parser to process unit expressions.
 */
final class Semantics {

  static void evalUnitExpr(Parser.Production p, MeasurementsParser gnuUnits) {
    if(p.rhs(0).isA(Parser.expr))
      p.lhs().value = p.rhs(0).value;
    else p.lhs().value = new Measurement(gnuUnits);
  }

  static void evalExpr(Parser.Production p) throws Parser.Exception {
    Measurement v = p.rhs(0).value;
    for(int i = 2; i < p.rhsSize(); i += 2) {
      Measurement v1 = p.rhs(i).value;
      if(p.rhs(i - 1).isA(Parser.MINUS)) v1.factor *= -1;
      v.add(v1);
    }
    p.lhs().value = v;
  }

  static void evalInverse(Parser.Production p) throws Parser.Exception {
    Measurement v = p.rhs(1).value;
    v.invert();
    p.lhs().value = v;
  }

  static void evalTerm(Parser.Production p) throws Parser.Exception {
    Measurement v = p.rhs(1).value;
    for(int i = 3; i < p.rhsSize(); i += 2)
      v.div(p.rhs(i).value);
    if(p.rhs(0).isA(Parser.MINUS)) v.factor *= -1;
    p.lhs().value = v;
  }

  static void evalProduct(Parser.Production p) {
    Measurement v = p.rhs(0).value;
    for(int i = 2; i < p.rhsSize(); i += 2)
      v.mult(p.rhs(i).value);
    p.lhs().value = v;
  }

  static void evalFactor(Parser.Production p) throws Parser.Exception {
    for(int i = p.rhsSize() - 4; i >= 0; i -= 3) {
      if(p.rhs(i + 2).isA(Parser.MINUS)) p.rhs(i + 3).value.factor *= -1;
      p.rhs(i).value.power(p.rhs(i + 3).value);
    }
    p.lhs().value = p.rhs(0).value;
  }

  static void makeNumUnit(Parser.Production p, MeasurementsParser gnuUnits) {
    Measurement v = new Measurement(gnuUnits);
    v.factor = p.rhs(0).numValue;
    p.lhs().value = v;
  }

  static void pass(Parser.Production p) {
    p.lhs().value = p.rhs(0).value;
  }

  static void removeParens(Parser.Production p) {
    p.lhs().value = p.rhs(1).value;
  }

  static void evalSqrt(Parser.Production p) throws Parser.Exception {
    p.lhs().value = p.rhs(1).value;
    p.lhs().value.root(2);
  }

  static void evalCbrt(Parser.Production p) throws Parser.Exception {
    p.lhs().value = p.rhs(1).value;
    p.lhs().value.root(3);
  }

  static void evalBfunc(Parser.Production p) throws Parser.Exception {
    p.lhs().value = p.rhs(1).value;
    p.rhs(0).func.applyTo(p.lhs().value);
  }

  static void evalUfunc(Parser.Production p) throws Parser.Exception {
    p.lhs().value = p.rhs(2).value;
    if(p.rhs(0).isA(Parser.EMPTY))
      p.rhs(1).func.applyTo(p.lhs().value);
    else p.rhs(1).func.applyInverseTo(p.lhs().value);
  }

  static void evalNumExpr(Parser.Production p) {
    p.lhs().numValue = p.rhs(0).numValue;
    for(int i = 2; i < p.rhsSize(); i += 2) {
      double n = p.rhs(i).numValue;
      p.lhs().numValue /= n;
    }
  }
}
