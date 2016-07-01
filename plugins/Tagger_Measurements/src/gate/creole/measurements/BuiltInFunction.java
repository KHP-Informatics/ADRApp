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

import java.util.List;

/**
 * A built-in function.
 */
class BuiltInFunction extends Function {
  private int funcType;

  protected static final int DIMLESS = 0;

  protected static final int ANGLEIN = 1;

  protected static final int ANGLEOUT = 2;

  private int funcNum;

  protected static final int SIN = 0;

  protected static final int COS = 1;

  protected static final int TAN = 2;

  protected static final int LN = 3;

  protected static final int LOG = 4;

  protected static final int LOG2 = 5;

  protected static final int EXP = 6;

  protected static final int ACOS = 7;

  protected static final int ATAN = 8;

  protected static final int ASIN = 9;

  BuiltInFunction(String nam, int ftyp, int fnum, MeasurementsParser gnuUnits) {
    super(nam, gnuUnits);
    funcType = ftyp;
    funcNum = fnum;
  }

  /**
   * Apply the function to Value 'v' (with result in 'v').
   */
  @Override
  void applyTo(Measurement v) throws Parser.Exception {
    switch(funcType){
      case ANGLEIN:
        if(!v.isNumber()) {
          String s = v.toString();
          v.denominator.add(gnuUnits.radian);
          if(!v.isNumber())
            throw new Parser.Exception("Argument " + s + " of " + name
                    + " is not a number or angle.");
        }
        break;
      case ANGLEOUT:
      case DIMLESS:
        if(!v.isNumber())
          throw new Parser.Exception("Argument " + v.toString() + " of " + name
                  + " is not a number.");
        break;
      default:
        throw new UnsupportedOperationException("Program Error; funcType="
                + funcType);
    }
    double arg = v.factor;
    switch(funcNum){
      case SIN:
        v.factor = Math.sin(arg);
        break;
      case COS:
        v.factor = Math.cos(arg);
        break;
      case TAN:
        v.factor = Math.tan(arg);
        break;
      case LN:
        v.factor = Math.log(arg);
        break;
      case LOG:
        v.factor = Math.log(arg) / Math.log(10);
        break;
      case LOG2:
        v.factor = Math.log(arg) / Math.log(2);
        break;
      case EXP:
        v.factor = Math.exp(arg);
        break;
      case ACOS:
        v.factor = Math.acos(arg);
        break;
      case ATAN:
        v.factor = Math.atan(arg);
        break;
      case ASIN:
        v.factor = Math.asin(arg);
        break;
      default:
        throw new UnsupportedOperationException("Program Error; funcNum="
                + funcNum);
    }
    if(funcType == ANGLEOUT) v.numerator.add(gnuUnits.radian);
  }

  @Override
  void applyInverseTo(Measurement v) {
    throw new UnsupportedOperationException("Program Error");
  }

  @Override
  void check() {
    throw new UnsupportedOperationException("Program Error");
  }

  @Override
  void addtolist(final Measurement v, List<Entity> list) {
    throw new UnsupportedOperationException("Program Error");
  }

  @Override
  String desc() {
    throw new UnsupportedOperationException("Program Error");
  }
}
