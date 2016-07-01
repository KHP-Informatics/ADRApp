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

/**
 * A product of units and/or prefixes. <br>
 * It may be empty, representing dimensionless number 1.
 */
class Product {
  // -------------------------------------------------------------------
  // The factors in a Product are represented as element of this Vector.
  // The factors are Factor objects (units or prefixes).
  // They are sorted in increasing alphabetic order of their names.
  // Duplicates are allowed (mean a power>1 of the unit).
  // -------------------------------------------------------------------
  private Vector<Factor> factors;

  Product() {
    factors = new Vector<Factor>();
  }

  @SuppressWarnings("unchecked")
  Product(final Product p) {
    factors = (Vector<Factor>)p.factors.clone();
  }

  /**
   * Add Factor 'f' to the Product. Return the result. (Originally
   * 'addsubunit').
   */
  Product add(final Factor f) {
    f.insertAlph(factors);
    return this;
  }

  /**
   * Add all factors of Product "p" to "this". Return the result. (Originally
   * 'addsubunitlist').
   */
  Product add(final Product p) {
    for(int i = 0; i < p.size(); i++)
      add(p.factor(i));
    return this;
  }

  /**
   * Return number of factors.
   */
  int size() {
    return factors.size();
  }

  /**
   * Return i-th factor.
   */
  Factor factor(int i) {
    return factors.elementAt(i);
  }

  /**
   * Remove i-th factor.
   */
  void delete(int i) {
    factors.removeElementAt(i);
  }

  /**
   * Return true if this Product and Product "p" have the same factors, other
   * than those marked dimensionless. (Originally 'compareproducts'.)
   */
  boolean isCompatibleWith(final Product p, Ignore ignore) {
    int i = 0;
    int j = 0;
    while(true) {
      while(i < size() && factor(i).ignoredIf(ignore))
        i++;
      while(j < p.size() && p.factor(j).ignoredIf(ignore))
        j++;
      if(i == size() || j == p.size()) break;
      if(factor(i) != p.factor(j)) return false;
      i++;
      j++;
    }
    if(i == size() && j == p.size()) return true;
    return false;
  }

  /**
   * Return printable representation of the Product.
   */
  String asString() {
    StringBuffer sb = new StringBuffer();
    int counter = 1;
    for(int i = 0; i < size(); i++) {
      Factor f = factor(i);
      // If s is the same as preceding, increment counter.
      if(i > 0 && f == factor(i - 1))
        counter++;
      // If s is first or distinct from preceding:
      else {
        if(counter > 1) sb.append("^" + counter);
        sb.append(" " + f.name);
        counter = 1;
      }
    }
    if(counter > 1) sb.append("^" + counter);
    return sb.toString();
  }

  /**
   * Return n-th root of the Product, or null if not n-th root. (Originally
   * 'subunitroot').
   */
  Product root(int n) {
    Product p = new Product();
    for(int i = 0; i < size(); i++) {
      Factor f = factor(i);
      int j = 1;
      i++;
      while(i < size() && f == factor(i)) {
        i++;
        j++;
      }
      if(j % n != 0) return null; // Not n-th root.
      for(int k = 0; k < (j / n); k++)
        p.add(f);
    }
    return p;
  }
}